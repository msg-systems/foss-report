(ns foss-report.main
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [org.soulspace.clj.file :as sf]
            [org.soulspace.tools.repo :as repo]
            [foss-report.core :as fc]
            [foss-report.options :as fo]
            [foss-report.report :as fr])
  (:gen-class))

(def appname "foss-report")
(def description
  "The program handles various tasks regarding FOSS licenses.
Inputs are the third party reports of the maven license plugin and
the excel sheet with the information for license reports.")

(def cli-opts
  [["-b" "--base-dir DIRNAME" "Base directory. If set, the paths are relative to this directory."]
   ["-c" "--current FILENAME" "Name of the file or directory with current artifact information."]
   ["-p" "--previous FILENAME" "Name of the file or directory with current artifact information."]
   ["-i" "--spdx-file FILENAME" "Name of the license names to SPDX ID mapping file." :default "data/txt2spdx.json"]
   ["-r" "--foss-report" "Generate a FOSS report." :default false]
   ["-d" "--foss-diff" "Generate a FOSS diff report. Needs current and previous input." :default false]
   ["-u" "--update-spdx-mapping" "Generate an updated license names to SPDX ID mapping." :default false]
   ["-l" "--download-licenses" "Download the relevant licenses from SPDX." :default false]
   ["-L" "--licenses-dir DIRNAME" "Directory for the download of licenses from SPDX." :default "data/licenses"]
   ["-s" "--download-sources" "Download the relevant source jars." :default false]
   ["-S" "--sources-dir DIRNAME" "Directory for the download of source jars." :default "data/sources"]
   ["-y" "--scan-sources" "Scan the sources for copyrights and notices." :default false]
   ["-f" "--report-format FORMAT" "The output format for reports (xls, json, stdout)." :default "xls"]
   ["-v" "--versions" "Process each artifact version." :default true]
   ["-g" "--gpl-only" "Reports the list of GPL only licensed artifacts."]
   ["-n" "--no-foss-license" "Reports the list of artifacts without FOSS license."]
   ["-h" "--help" "Print usage information."]])

(defn path
  "Builds a file path from the parameters."
  [& ps]
  (str/join \/ (remove nil? ps)))

(defn usage-msg
  "Returns a message containing the program usage."
  ([summary]
   (usage-msg (str "java --jar " appname ".jar <options>") "" summary))
  ([name summary]
   (usage-msg name "" summary))
  ([name description summary]
   (str/join "\n\n"
             [description
              (str "Usage: " name " [options].")
              ""
              "Options:"
              summary])))

(defn error-msg
  "Returns a message containing the parsing errors."
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn exit
  "Exits the process."
  [status msg]
  (println msg)
  (System/exit status))

(defn validate-args
  "Validate command line arguments. Either returns a map indicating the program
  should exit (with an error message and optional success status), or a map
  indicating the options provided."
  [args cli-opts]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-opts)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage-msg appname description summary) :success true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      (and (:diff options) (not (:previous options)))
      {:exit-message (error-msg "No previous file or directory specified to diff against.")}
      (= 0 (count arguments)) ; no args
      {:options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage-msg appname description summary)})))

(defn read-input
  "Reads the artifacts from the given filename.
   If filename points to a directory, it is recursively searched for THIRDPARTY.txt files.
   If filename ponts to an excel report, it is read and parsed into artifacts."
  ([filename]
   (let [f (io/as-file filename)]
     (if-not (sf/exists? f)
       (exit 1 (str "Error: File " filename "doesn't exist."))
       (if (sf/is-dir? f) 
         (fc/artifacts-from-third-party-files filename)
         (fc/artifacts-from-excel filename))))))

(defn compute-artifacts
  "Compute the artifacts from file-name with the given key function and spdx mapping."
  [filename key-fn spdx-mapping]
  (let [artifacts (->> (read-input filename)
                       (fc/combine-artifacts key-fn)
                       (map (partial fc/update-artifact spdx-mapping))
                       (map fc/scan-sources-jar)
                       (into #{}))]
    artifacts))

(defn handle
  "Handle options and generate the requested outputs."
  []
  (let [base-dir (fo/get-option :base-dir)
        key-fn (if (fo/get-option :versions) repo/artifact-version-key repo/artifact-key)
        spdx-mapping (fc/read-mvn-spdx-mapping (path base-dir (fo/get-option :spdx-file)))
        current-artifacts (compute-artifacts (path base-dir (fo/get-option :current)) key-fn spdx-mapping)]
    (when (fo/get-option :foss-report)
        ; generate FOSS report
      (fr/foss-report current-artifacts))
    (when (fo/get-option :foss-diff)
        ; generate FOSS diff reports
      (let [previous-artifacts (compute-artifacts (path base-dir (fo/get-option :previous)) key-fn spdx-mapping)]
        (fr/diff-report current-artifacts previous-artifacts)))
    (when (fo/get-option :gpl-only)
        ; generate Strong Copyleft only report
      (fr/strong-copyleft-report current-artifacts))
    (when (fo/get-option :no-foss-license)
        ; generate No License report
      (fr/no-license-report current-artifacts))
    (when (fo/get-option :update-spdx-mapping)
      ; generate an updated spdx mapping
      (fc/update-mvn-spdx-mapping (path base-dir (fo/get-option :spdx-file)) current-artifacts))
    (when (fo/get-option :download-licenses)
      ; download used FOSS license texts
      (fc/download-spdx-licenses (path base-dir (fo/get-option :licenses-dir))
                                 (fc/distinct-spdx-ids spdx-mapping current-artifacts)))
    (when (fo/get-option :download-sources)
      ; download the source jars for the FOSS artifacts
      (fc/download-sources (path base-dir (fo/get-option :sources-dir)) current-artifacts))))

(defn -main
  "Main function as entry point for foss report generation."
  [& args]
  (let [{:keys [options exit-message success]} (validate-args args cli-opts)]
    ; make options globally available 
    (fo/set-options options)
    (println @fo/options)
    (if exit-message
      ; exit with message
      (exit (if success 0 1) exit-message)
      ; handle options and generate the requested outputs
      (handle))))

(comment
  (-main "-h")
  (-main "-y")
  )
