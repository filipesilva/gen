(ns filipesilva.gen.xforms)

(defn append [file xform]
  (str file xform))

(defn prepend [file xform]
  (str xform file))
