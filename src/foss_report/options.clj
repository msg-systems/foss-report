(ns foss-report.options)

;;; 
;;; handle options
;;;
(def options "Reference variable for the options initialized with an empty map."
  (ref {}))

(defn set-options
  "Set the opts as global var for reference."
  [opts]
  (dosync
   (ref-set options opts)))

(defn get-option
  "Returns the value of the option k by dereferencing the options ref."
  ([k]
   (@options k)))

