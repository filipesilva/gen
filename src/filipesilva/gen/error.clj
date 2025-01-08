(ns filipesilva.gen.error
  (:require [bling.core :as bling]))

(defn throw [msg map]
  (throw (ex-info msg (assoc map ::error true))))

(defn error? [e]
  (-> e ex-data ::error))

(defn log [e]
  (println (bling/bling [:bold.red "Error:"])
           (ex-message e)
           (dissoc (ex-data e) ::error)))
