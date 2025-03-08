(ns filipesilva.gen.cli
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [filipesilva.gen :as gen]
            [filipesilva.gen.config :as config]
            [filipesilva.gen.error :as error]))

(def reserved-args [:source :dest :version :help :config-help
                    :gen/config :gen/global-config
                    :gen/overwrite :gen/dry-run :gen/allow-missing])

(def default-vars [:dest-name :date :year])

(def config-spec
  {:spec
   {;; reserved-args
    :source            {:coerce :string}
    :dest              {:coerce :string}
    :version           {:coerce :boolean}
    :help              {:coerce :boolean}
    :config-help       {:coerce :boolean}
    :gen/dry-run       {:coerce  :boolean
                        :default false
                        :desc    "Don't write to disk"}
    :gen/overwrite     {:coerce  :boolean
                        :default false
                        :desc    "Allow overwriting files"}
    :gen/allow-missing {:coerce  :boolean
                        :default false
                        :desc    "Allow missing values"}
    :gen/config        {:coerce  :string
                        :desc    "Path to config file"
                        :default (config/resolve-config)}
    :gen/global-config {:desc    "Path to global config file"
                        :default (config/resolve-global-config)}

    ;; default
    :dest-name {:coerce :string
                :desc   "Defaults to last part of `dest`"}
    :date      {:coerce  :string
                :desc    "Current date as yyyy-mm-dd"
                :default (str (java.time.LocalDate/now))}
    :year      {:coerce  :string
                :desc    "Current year"
                :default (str (java.time.Year/now))}}
   :args->opts [:source :dest]})

(def gen-project-dir
  (-> (io/resource "examples/gen.edn")
      (fs/path  "../../../")
      fs/normalize
      str))

(def local-readme-path
  (-> (fs/path gen-project-dir "README.md")
      str))

(defn str-line-seq [s]
  (-> s .getBytes io/reader line-seq))

(defn show-version []
  (let [opts          {:dir gen-project-dir :continue true, :out :string, :err :string}
        sh            #(process/shell opts %)
        [tag tag-sha] (some-> "git for-each-ref --sort=-taggerdate --format='%(refname:short) %(objectname)' refs/tags"
                              sh :out str-line-seq first (str/split #" "))
        sha           (-> "git rev-parse HEAD" sh :out str-line-seq first)
        snapshot      (-> "git diff --quiet" sh :exit (not= 0))]
    (println (str (or tag "vUNKNOWN")
                  (when (not= sha tag-sha)
                    (str "-" sha))
                  (when snapshot
                    "-SNAPSHOT")))))

(defn show-help
  [spec]
  (println
"Usage: gen [source] dest [--var val]

Copy files from `source` to `dest`, replacing all instances of `{{var}}` in
file paths, file content, and commands with `val`.

You can provide any number of replacement vars.
The `dest-name` var defaults to the `dest` directory name but can be overwritten.

`source` can be:
- a directory
- a `git` respository URL
- ommited, resulting in the config directory, if any
- a key in the `:sources` map in global config
- followed by a key in config `:aliases` to be merged

Configuration files are optional but let you do more, like running commands.
Run `gen --config-help` for more information about configuration files.")
  (println "\nYou can find in depth docs in the local README:")
  (println local-readme-path)
  (println "Project homepage: https://github.com/filipesilva/gen")
  (println "Gen uses Selmer templates: https://github.com/yogthos/Selmer")
  (println "\nDefault vars:")
  (println (cli/format-opts (merge spec {:order default-vars})))
  (println "\nReserved args, cannot be used as vars:")
  (println (cli/format-opts (merge spec {:order reserved-args}))))

(defn show-config-help
  []
  (println
   "`gen` will look for configuration in `gen.edn` on the `source` directory, or recursively up
the current directory parents.

Configuration example:")
  (println (slurp (io/resource "examples/gen.edn")))

  (println "Aliases are useful for when you want to generate different things with the same config.
The :default alias will be used if it exists and no other is specified.")
  (println (slurp (io/resource "examples/license/gen.edn")))

  (println "Global configuration will be looked up in `~/.gen-global.edn` and can contain
a :sources shorthand map, and :vars that configuration will merge on top.")
  (print (slurp (io/resource "examples/.gen-global.edn"))))

(defn args->cli-config [args]
  (let [only-reserved (select-keys args reserved-args)
        no-reserved   (apply dissoc args reserved-args)]
    (assoc only-reserved :vars no-reserved)))

(defn run
  {:org.babashka/cli config-spec}
  [{:keys [version help config-help source] :as args}]
  (try
    (cond
      (nil? source) (show-help config-spec)
      version       (show-version)
      help          (show-help config-spec)
      config-help   (show-config-help)
      :else         (-> args args->cli-config config/compose-configs gen/generate))
    (catch ^:sci/error Exception e
      (if (error/error? e)
        (do (error/log e)
            (System/exit 1))
        (throw e)))))
