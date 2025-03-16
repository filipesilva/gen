(ns filipesilva.gen.log
  (:require [bling.core :as bling]))

(defn log [style prefix & args]
  (apply println (bling/bling [style prefix]) args))

;; https://github.com/paintparty/bling?tab=readme-ov-file#bling
(def write (partial log :green  "write"))
(def xform (partial log :orange "xform"))
(def run   (partial log :purple "run  "))

;; https://github.com/paintparty/bling?tab=readme-ov-file#color-aliases
(def info  (partial log :bold.info  "info ")) ;; blue
(def warn  (partial log :bold.warn  "warn ")) ;; yellow
(def error (partial log :bold.error "error")) ;; red
