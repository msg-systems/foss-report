(ns foss-report.report
  (:require [foss-report.core :as fc]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [org.soulspace.cmp.poi.excel :as xl])
  (:import [java.time Instant]
           [java.time.format DateTimeFormatter]
           [java.time.temporal ChronoUnit]))

(defn timestamp
  "Returns an ISO formated timestamp truncated to seconds for the current instant."
  []
  (.format DateTimeFormatter/ISO_INSTANT (.truncatedTo (Instant/now) ChronoUnit/SECONDS)))

(defn clean-filename
  "Returns a valid filename by replacing ':' with '_' in ISO formatted timestamp."
  [s]
  (str/replace s ":" "_"))

(defn timestamped-filename
  "Returns a timestamped filename with the given base and extension."
  [base ext]
  (-> (str base (timestamp) "." ext)
      (str/replace ":" "_")))

;;
;; excel diff report 
;;

(defn xls-header-row
  "Generates a header row."
  []
  (xl/new-row {}
              (xl/new-cell {} "Components")
              (xl/new-cell {} "GroupID")
              (xl/new-cell {} "ArtifactID")
              (xl/new-cell {} "Version")
              (xl/new-cell {} "Artifact Type")
              (xl/new-cell {} "Licenses")
              (xl/new-cell {} "Delivery")
              (xl/new-cell {} "Integration")
              (xl/new-cell {} "Homepage")
              (xl/new-cell {} "SPDX-IDs")
              (xl/new-cell {} "Copyrights")
              (xl/new-cell {} "Authors")
              (xl/new-cell {} "Repository")))

(defn xls-artifact-row
  "Generates a report row from the artifact."
  [a]
  (xl/new-row {}
              (xl/new-cell {} (str/join "; " (:components a)))
              (xl/new-cell {} (:group-id a))
              (xl/new-cell {} (:artifact-id a))
              (xl/new-cell {} (:version a))
              (xl/new-cell {} (:artifact-type a))
              (xl/new-cell {} (str/join "; " (:licenses a)))
              (xl/new-cell {} (:delivery a))
              (xl/new-cell {} (:integration a))
              (xl/new-cell {} (:homepage-url a))
              (xl/new-cell {} (str/join "; " (:spdx-ids a)))
              (xl/new-cell {} (str/join "\r\n" (:copyrights a)))
              (xl/new-cell {} (:authors a))
              (xl/new-cell {} (:repository a))))

(defn xls-sheet
  "Generates a report sheet with the sheet-name and the data from coll."
  [sheet-name coll]
  (xl/new-sheet {}
                (xls-header-row)
                (doseq [a coll]
                  (try
                    (xls-artifact-row a)
                    (catch Exception e
                      (prn "caught exception " e " on row " a))))))

(defn xls-foss-report
  "Generates a new FOSS report in excel format."
  ([data]
   (xls-foss-report (timestamped-filename "FOSS_Report_" "xlsx") data))
  ([report-xls data]
   (xl/write-workbook
    report-xls
    (xl/new-workbook {}
                     (xls-sheet "foss licenses" data)))))

(defn xls-diff-report
  "Generates a FOSS diff report in excel format."
  ([data]
     (xls-diff-report (timestamped-filename "FOSS_Diff_" "xlsx") data))
  ([report-xls data]
   (xl/write-workbook
    report-xls
    (xl/new-workbook {}
                     (xls-sheet "both" (:both data))
                     (xls-sheet "only1" (:only1 data))
                     (xls-sheet "only2" (:only2 data))))))

(defn xls-gpl-only-report
  "Generates a report with GPL only licensed artifacts."
  ([artifacts]
   (xls-gpl-only-report (timestamped-filename "StrongCopyleft_Report_" "xlsx") artifacts))
  ([report-xls artifacts]
   (xl/write-workbook
    report-xls
    (xl/new-workbook {}
                     (xls-sheet "Strong-Copyleft" (filter fc/gpl-only? artifacts))))))

(defn xls-no-foss-report
  "Generates a report with artifacts without a (known) FOSS license."
  ([artifacts]
   (xls-no-foss-report (timestamped-filename "NoLicense_Report_" "xlsx") artifacts))
  ([report-xls artifacts]
   (xl/write-workbook
    report-xls
    (xl/new-workbook {}
                     (xls-sheet "No-License" (filter fc/license-unknown? artifacts))))))

(defn json-diff-report
  "Generates a FOSS diff report in JSON format."
  ([data]
   (let [timestamp (.format DateTimeFormatter/ISO_INSTANT (.truncatedTo (Instant/now) ChronoUnit/SECONDS))
         report-file (clean-filename (str "FOSS_Diff_" timestamp ".json"))]
     (json-diff-report report-file data)))
  ([report-file data]
   (spit report-file (json/json-str data))))

(defn json-foss-report
  "Generates a FOSS report in JSON format."
  ([data]
   (json-foss-report (timestamped-filename "FOSS_Report_" "json") data))
  ([report-file data]
  ; TODO handle non Java dependencies
   (spit report-file (json/json-str (concat (:both data) (:only1 data))))))

(defn json-gpl-only-report
  "Generates a report with GPL only licensed artifacts in JSON format."
  ([artifacts]
   (json-gpl-only-report (timestamped-filename "GPLOnly_Report_" "json") artifacts))
  ([report-file artifacts]
   (spit report-file (json/json-str (filter fc/gpl-only? artifacts)))))

(defn json-no-foss-report
  "Generates a report with artifacts without a (known) FOSS license."
  ([artifacts]
   (json-no-foss-report (timestamped-filename "NoLicense_Report_" "json") artifacts))
  ([report-file artifacts]
   (spit report-file (json/json-str (filter fc/license-unknown? artifacts)))))

(defn print-diff-report
  "Prints the differences in JSON format to stdout."
  [data]
  (println (json/json-str data)))

(defn print-foss-report
  "Prints the artifacts in JSON format to stdout."
  [data]
  (println (json/json-str data)))

(defn print-gpl-only-report
  "Prints the artifacts in JSON format to stdout."
  [artifacts]
  (println (json/json-str (filter fc/gpl-only? artifacts))))

(defn print-no-foss-report
  "Prints the artifacts in JSON format to stdout."
  [artifacts]
  (println (json/json-str (filter fc/license-unknown? artifacts))))

(comment
  (defmulti foss-report "Generates a FOSS report for the given artifacts." #(fo/get-option :format))
  (defmulti diff-report "Generates a FOSS diff report for the given artifacts." #(fo/get-option :format))
  (defmulti strong-copyleft-report "Generates FOSS stron copyleft report for the given artifacts." #(fo/get-option :format))
  (defmulti no-license-report "Generates the FOSS report for the given artifacts." #(fo/get-option :format))

  (defmethod foss-report :xls
    [artifacts]
    (xls-foss-report (timestamped-filename "FOSS_Report_" "xlsx") artifacts))

  (defmethod foss-report :json
    [artifacts]
    (json/json-str artifacts))

  (defmethod foss-report :edn
    [artifacts])

  (defmethod foss-report :csv
    [artifacts])
  )

(defn foss-report
  "Generates the FOSS report for the given artifacts."
  [artifacts]
  (println "Generating FOSS report.")
  (case (fc/get-option :report-format)
    "xls" (xls-foss-report artifacts)
    "json" (json-foss-report artifacts)
    "stdout" (print-foss-report artifacts)
    :else (print-foss-report artifacts)))

(defn diff-report
  "Generates the Diff report for the given current and previous artifacts."
  [current-artifacts previous-artifacts]
  (println "Generating Diff report.")
  (let [diff (fc/diff-deps current-artifacts previous-artifacts (fc/get-option :versions))]
  (case (fc/get-option :report-format)
    "xls" (xls-diff-report diff)
    "json" (json-diff-report diff)
    "stdout" (json-diff-report diff)
    :else (print-diff-report diff))))

(defn strong-copyleft-report
  "Generates the Strong Copyleft Only report for the given artifacts."
  [artifacts]
  (println "Generating Strong Copyleft Only report.")
  (case (fc/get-option :report-format)
    "xls" (xls-gpl-only-report artifacts)
    "json" (json-gpl-only-report artifacts)
    "stdout" (print-gpl-only-report artifacts)
    :else (print-gpl-only-report artifacts)))

(defn no-license-report
  "Generates the No License report for the given artifacts."
  [artifacts]
  (println "Generating No License report.")
  (case (fc/get-option :report-format)
    "xls" (xls-no-foss-report artifacts)
    "json" (json-no-foss-report artifacts)
    "stdout" (print-no-foss-report artifacts)
    :else (print-no-foss-report artifacts)))

; evaluate the forms in REPL for quick tests
(comment
  (xls-diff-report (fc/diff-deps (fc/artifacts-from-third-party-files "data") (fc/artifacts-from-excel "data/FOSS.xlsm")))
  (xls-foss-report (fc/artifacts-from-third-party-files "data"))
  (json-diff-report (fc/diff-deps (fc/artifacts-from-third-party-files "data") (fc/artifacts-from-excel "data/FOSS.xlsm")))
  (json-foss-report (fc/artifacts-from-third-party-files "data"))
  (print-diff-report (fc/diff-deps (fc/artifacts-from-third-party-files "data") (fc/artifacts-from-excel "data/FOSS.xlsm")))
  )
