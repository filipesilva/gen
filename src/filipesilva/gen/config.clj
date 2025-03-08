(ns filipesilva.gen.config
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [edamame.core :as e]
            [filipesilva.gen.error :as error]
            [medley.core :as m]))

(def default-config
  {:root ""
   :vars {}
   :cmds []})

(def config-filename "gen.edn")

(defn resolve-config
  ([]
   (resolve-config (fs/cwd)))
  ([dir]
   (let [cfg    (fs/file dir config-filename)
         parent (fs/parent dir)]
     (cond
       (fs/exists? cfg)    cfg
       (fs/exists? parent) (recur parent)))))

(def global-config-filename ".gen-global.edn")

(defn resolve-global-config []
  (let [f (fs/file (fs/home) global-config-filename)]
    (when (fs/exists? f)
      (str f))))

(defn parse-source-aliases-dest
  [{:keys [source dest]} {:keys [sources]} global-config-path]
  (let [[source' dest']      (cond
                               (and source dest)       [source dest]
                               (and source (not dest)) ["" source]
                               :else                   (error/throw "Could not determine `source` and `dest`"
                                                                    {:source source, :dest dest}))
        [source'' & aliases] (let [[first second & rest :as split] (str/split source' #":")]
                               (if (= first "https")
                                 (into [(str first ":" second)] rest)
                                 split))
        source'''            (if-let [global-source (some-> sources (get (keyword source'')) fs/expand-home)]
                               (fs/path (fs/parent global-config-path) global-source)
                               source'')
        source''''           (some-> source''' not-empty fs/expand-home fs/absolutize str)
        dest''               (some-> dest' not-empty fs/expand-home fs/absolutize str)]
    [source'''' aliases dest'']))

(defn git-clone-path
  [git-url]
  (let [tmp-dir            (fs/path (fs/temp-dir) (str (random-uuid)))
        {:keys [err exit]} (process/shell {:out :string :err :string :continue true}
                                          "git" "clone" git-url tmp-dir)]
    (when (not= exit 0)
      (error/throw "Error during git clone" {:err err}))
    tmp-dir))

(defn resolve-source-path
  [source local-config-path]
  (cond
    (and (empty? source) local-config-path)
    (str (fs/parent local-config-path))

    (and (not-empty source) (fs/directory? source))
    source

    (or (str/starts-with? source "https://")
        (str/starts-with? source "git@"))
    (git-clone-path source)

    :else
    (error/throw "Could not determine `source` path"
                 {:source source, :local-config-path local-config-path})))

(defn deep-merge-alias
  [config alias]
  (let [alias-config (get-in config [:aliases (keyword alias)])]
    (when (nil? alias-config)
      (error/throw "Alias not found" {:alias alias}))
    (m/deep-merge config alias-config)))

(defn compose-configs
  [cli-config]
  (let [local-config-path     (:gen/config cli-config)
        local-config          (some-> local-config-path slurp e/parse-string)
        global-config-path    (:gen/global-config cli-config)
        global-config         (some-> global-config-path slurp e/parse-string)
        [source aliases dest] (parse-source-aliases-dest cli-config global-config global-config-path)
        source-path           (resolve-source-path source local-config-path)
        source-config-path    (if (empty? source)
                                local-config-path
                                (str (fs/path source-path config-filename)))
        source-config         (when (fs/exists? source-config-path)
                                (some-> source-config-path slurp e/parse-string))
        aliases'              (if (and (empty? aliases) (:default-alias source-config))
                                [(-> source-config :default-alias name)]
                                aliases)
        source+aliases-config (reduce deep-merge-alias source-config aliases')]
    (m/deep-merge default-config
                  {:vars {:dest-name (fs/file-name dest)}}
                  (select-keys global-config [:vars])
                  (select-keys local-config [:vars])
                  (dissoc source+aliases-config :aliases :default-alias)
                  (select-keys cli-config [:vars :gen/overwrite :gen/dry-run :gen/allow-missing])
                  {:source source-path
                   :dest   dest})))
