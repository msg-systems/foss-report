(ns foss-report.io
  (:require [clojure.edn :as edn]
            [clojure.data.json :as json]
            [clojure.data.csv :as csv]))

(defn write-edn
  "Writes the data in EDN format to the given writer."
  [writer data]
  (with-open [out writer]
    (binding [*out* out]
      (pr data))))

(defn read-edn
  "Reads the data in EDN format from the given reader."
  [reader]
  (with-open [in reader]
    (edn/read-string (slurp in))))

(defn write-csv
  "Writes the data in CSV format to the given writer."
  ([writer data]
   (with-open [out writer]
     (csv/write-csv out data)))
  ([writer data options]
   (with-open [out writer]
     (csv/write-csv out data options))))

(defn read-csv
  "Reads the data in CSV format from the given reader."
  [reader]
  (with-open [in reader]
    (doall
     (csv/read-csv in))))

(defn write-json
  "Writes the data in JSON format to the given writer."
  [writer data]
  (with-open [out writer]
    (spit out (json/write-str data))))

(defn read-json
  "Reads the data in JSON format from the given reader."
  [reader]
  (with-open [in reader]
    (json/read-str (slurp in) :key-fn keyword)))

