(ns filipesilva.gen.error
  (:require [bling.core :as bling]))

(defn throw [msg map]
  (throw (ex-info msg (assoc map ::error true))))

(defn sci-error->ex-info [sci-error]
  (when-let [m (Throwable->map sci-error)]
    (ex-info (:cause m) (:data m))))

(defn error? [e]
  (-> e ex-data ::error))

(defn log [e]
  (println (bling/bling [:bold.red "Error:"])
           (ex-message e)
           (dissoc (ex-data e) ::error)))
