(ns filipesilva.gen
  (:refer-clojure :exclude [read])
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.repl.deps :as deps]
            [clojure.set :as set]
            [clojure.string :as str]
            [filipesilva.gen.error :as error]
            [filipesilva.gen.filters]
            [filipesilva.gen.log :as log]
            [medley.core :as m]
            [selmer.parser :as selmer]))

;; Don't try to render non-text extensions.
;; Maybe should make it configurable at some point.
(def render-ignore
  #{".png" ".jpeg" ".jpg" ".gif" ".svg" ".webp" ".ico"})

(defn dont-render? [filepath]
  (some #(str/ends-with? filepath %) render-ignore))

(defn ->xform
  "Returns xform for xform-filepath, if any, as [filepath sym xform-name]."
  [xforms xform-filepath]
  (some (fn [[ext sym]]
          (when (str/ends-with? xform-filepath ext)
            [(subs xform-filepath 0 (- (count xform-filepath) (count ext)))
             sym
             (subs ext 1)]))
        xforms))

(defn read [source root]
  (let [source' (fs/path source root)]
    (->> (fs/glob source' "**")
         (filter fs/regular-file?)
         (map #(->> % (fs/relativize source') str))
         (remove #(or (= % "gen.edn")
                      (str/starts-with? % ".git/")
                      (str/starts-with? % ".git\\")))
         (map (fn [f] [f (slurp (str (fs/path source' f)))]))
         (into {}))))

(defn check [file-map cmds dest vars xforms allow-missing? overwrite? dry-run?]
  (when-not allow-missing?
    (let [known   (->> file-map (filter #(-> % first dont-render? not))
                       (mapcat identity) (into cmds)
                       (map selmer/known-variables) (reduce into #{}))
          missing (set/difference known vars)]
      (when-not (empty? missing)
        (error/throw "Missing vars, use --gen/allow-missing dest force" {:missing missing}))))

  (when-not (or dry-run? overwrite?)
    (let [overwritten-files (->> (keys file-map)
                                 (filter (comp not (partial ->xform xforms)))
                                 (map #(fs/path dest %))
                                 (map #(fs/relativize (fs/cwd) %))
                                 (filter fs/exists?)
                                 (map str)
                                 vec)]
      (when-not (empty? overwritten-files)
        (error/throw "Would overwrite files, use --gen/overwrite dest force"
                     {:overwritten-files overwritten-files}))))
  file-map)

(defn render [file-map vars]
  (m/map-kv (fn [filepath content]
              [(selmer/render filepath vars)
               (if (dont-render? filepath)
                 content
                 (selmer/render content vars))])
            file-map))

(defn write [file-map dest xforms dry-run?]
  (run! (fn [[filepath content]]
          (let [f (fs/path dest filepath)]
            (log/write (->> f (fs/relativize (fs/cwd)) str))
            (when-not dry-run?
              (fs/create-dirs (fs/parent f))
              (spit (str f) content))))
        (->> file-map
             (filter (comp not (partial ->xform xforms) first))
             (sort-by first)))
  file-map)

(defn xform [file-map dest xforms dry-run?]
  (run! (fn [[xform-filepath xform-content]]
          (let [[filepath sym xform-name] (->xform xforms xform-filepath)
                f (fs/path dest filepath)]
            (if-not (fs/exists? f)
              (log/warn "missing, can't xform" (->> f (fs/relativize (fs/cwd)) str))
              (let [file-content    (slurp (str f))
                    xformed-content ((requiring-resolve sym) file-content xform-content)]
                (log/xform (->> f (fs/relativize (fs/cwd)) str) xform-name)
                (when-not dry-run?
                  (fs/create-dirs (fs/parent f))
                  (spit (str f) xformed-content))))))
        (->> file-map
             (filter (comp (partial ->xform xforms) first))
             (sort-by first)))
  file-map)

(defn run-cmd! [dest vars dry-run? cmd]
  (let [cmd' (selmer/render cmd vars)]
    (log/run cmd')
    (when-not dry-run?
      (try
        (process/shell {:dir dest, :out :string, :err :string} cmd')
        (catch Exception _
          (log/error cmd'))))))

(defn resolve-local-coord [source coord]
  (if (:local/root coord)
    (update coord :local/root #(str (fs/path source %)))
    coord))

(defn generate [{:keys [source dest root vars cmds deps xforms]
                 :gen/keys [allow-missing overwrite dry-run]}]
  (when deps
    (deps/add-libs (m/map-vals (partial resolve-local-coord source) deps)))
  (-> (read source root)
      (check cmds dest vars xforms allow-missing overwrite dry-run)
      (render vars)
      (write dest xforms dry-run)
      (xform dest xforms dry-run))
  (run! (partial run-cmd! dest vars dry-run) cmds))


;; TODO:
;; - readme
;; - interesting usecases
;;   - readme generation (code blocks examples etc)
;;   - version bump
;;   - changelog
;; - xforms
;;   - Files whose extention matches a known transformation (xform) fn will be processed last.
;;   - The xform fn will receive a ctx object containing gen configuration, current file map,
;;     own file without xform extension, own file content, and return a new file map.
;;   - Default xforms include `append` (appending dest file), and `require` (adds requires).
;;   - language specific xforms should target ext, e.g. .clj.require instead of just .require
;;   - could call a cmd with both file contents as args, maybe overkill
;;   - yeah pretty sold as a CLI xform
;;     - call a script
;;     - stdin gets edn with {:file-content "...", :xform-content "..."}
;;     - stdout gets xformed-file-content
;;     - config have a :xforms map from k to bin path
;;     - resolved from declaring file
;;     - can be declared on global, overwritten on local
;; - xform seems like the escape hatch
;;   - probably always needs dest use symbol/scripts and paths and whatever
;;   - will need a classpath and require thing... try dest reuse as much source bb
;;   - don't think I can use classpaths source multiple places...
;;     - points dest having a certain "home" for a given gen, and not crossing that barrier
;; - val eval
;;   - maybe just shell commands too?
;;   - or both use the [:sh "cmd --foo"] thing like filedb
;;   - bad for vals imho..
;;   - maybe leave vals for later rly
;;   - not even 100% sure what kind of usecases need val evals
;; - compose in :from opt?
;;   - would be nice dest have a way dest cmds multiple source
;;   - alias is merge, compose is multiple
;;   - easy dest do multiple in config, just make it an array
;;   - doing it in cli might be hard, would need another char like `:` is used for aliases
;;   - related dest :include :exclude :extra-*, need dest think better about composability
;; - known errors could be a lot nicer
;;   - still prints the stack trace I think
