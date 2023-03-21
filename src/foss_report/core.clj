(ns foss-report.core
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [org.soulspace.tools.repo :as repo]
            [org.soulspace.tools.spdx :as spdx]
            [org.soulspace.cmp.poi.excel :as xl]
            [org.soulspace.clj.file :as sf]
            [foss-report.zip :as z]))

;;;;
;;;; base functionality for handling FOSS licenses
;;;;

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



;;;
;;; handle licenses 
;;;

;;
;; handle SPDX license references
;;

(defn- to-sorted-set
  "Converts the value to a sorted set. Used as :value-fn in reading JSON."
  [_ v]
  (into (sorted-set) v))

(defn download-spdx-license
  "Downloads the license text for the given SPDX id."
  ([id]
   (download-spdx-license "." id))
  ([output-dir id]
   (let [[license exception] (str/split id #" WITH ")
         license-text (:licenseText (spdx/license license))]
     (if exception
       (spit (str output-dir "/" id ".txt")
             (str license-text "\n\n" (:licenseExceptionText (spdx/exception exception))))
       (spit (str output-dir "/" id ".txt")
             license-text)))))

(defn download-spdx-licenses
  "Downloads the license texts for the given SPDX ids."
  ([ids]
   (download-spdx-licenses "." ids))
  ([output-dir ids]
   (when-not (sf/exists? output-dir)
     (sf/create-dir (io/as-file output-dir)))
   (doseq [id ids]
     (download-spdx-license output-dir id))))

;;
;; handle the mapping between POM license infos and SPDX ids
;;
(defn license-names-set
  "Returns a set of all license names in the collection of artifacts."
  [artifacts]
  (->> artifacts
       (map :licenses)
  ;     (flatten)
       (into (sorted-set))))

(defn new-spdx-mapping
  "Returns a map with the license name and an (empty) array for SPDX IDs."
  [artifacts]
  (->> artifacts
       (license-names-set)
       (into (sorted-map) (map #(vec [% (sorted-set)])))))

(defn read-mvn-spdx-mapping
  "Reads the map of the JSON POM license to SPDX id mapping."
  [filename]
  (-> (slurp filename)
      (json/read-str :value-fn to-sorted-set)))

(defn write-mvn-spdx-mapping
  "Writes the map of the POM license to SPDX id mapping as JSON."
  [filename m]
  (->> (dissoc m nil)
       (json/write-str)
       (spit filename)))

(defn update-mvn-spdx-mapping
  "Updates the JSON POM license to SPDX id mapping."
  [mapping-file artifacts]
  (let [bak-file (str mapping-file ".bak")]
    (when (sf/exists? bak-file)
      (sf/delete-file bak-file))
    (io/copy (io/file mapping-file) (io/file bak-file))
    (write-mvn-spdx-mapping mapping-file
                            (merge-with set/union (read-mvn-spdx-mapping mapping-file) (new-spdx-mapping artifacts)))))

(defn spdx-ids-for-licenses
  "Returns the set of SPDX license ids for the license string in the maven artifact."
  [m a]
  (->> (:licenses a)
       (map (partial get m))
       (apply set/union)))

(defn distinct-spdx-ids
  "Returns a set of SPDX ids for the given artifacts."
  [spdx-mapping artifacts]
  (->> artifacts
       (map (partial spdx-ids-for-licenses spdx-mapping))
       (apply set/union)))

; evaluate the forms in REPL for quick tests
(comment
  (json/write-str {"a" #{"b"} "c" (sorted-set "d" "e" "a")})
  (spdx/licenses)
  (spdx/license "EPL-1.0")
  (license-names-set {:licenses #{"Apache-2.0" "CC0-1.0" "EPL-2.0" "GPL-2.0-only WITH Classpath-exception-2.0"}})
  (download-spdx-license "data/licenses" "EPL-1.0")
  (read-mvn-spdx-mapping "txt2spdx.json")
  (read-mvn-spdx-mapping "data/txt2spdx.json")
  (spdx-ids-for-licenses (read-mvn-spdx-mapping "data/txt2spdx.json") {:licenses #{"Apache License, 2.0; EPL 2.0; Public Domain; The GNU General Public License (GPL), Version 2, With Classpath Exception"}})
  (spdx-ids-for-licenses (read-mvn-spdx-mapping "data/txt2spdx.json") {:licenses #{"Apache License, 2.0" "EPL 2.0" "Public Domain" "The GNU General Public License (GPL), Version 2, With Classpath Exception"}}))

;;;
;;; handle jars
;;;

;;
;; download source jars
;;
(defn download-source-jar
  "Downloads the sources for an artifact."
  [sources-dir artifact]
  (when-not (sf/exists? sources-dir)
    (sf/create-dir (io/as-file sources-dir)))

  (when-not (repo/local-artifact? "sources" "jar" artifact)
    (repo/cache-artifact "sources" "jar" artifact))
  (if (repo/local-artifact? "sources" "jar" artifact)
    (io/copy (io/as-file (str (repo/artifact-version-local-path artifact) "/" (repo/artifact-filename "sources" "jar" artifact)))
             (io/as-file (str sources-dir "/" (repo/artifact-filename "sources" "jar" artifact))))
    (println "missing sources for " (repo/artifact-version-key artifact))))

(defn download-sources
  "Downloads the sources for the collection of artifacts."
  [sources-dir artifacts]
  (when-not (sf/exists? sources-dir)
    (sf/create-dir (io/as-file sources-dir)))
  (doseq [a artifacts]
    (download-source-jar sources-dir a)))

;;
;; extract copyrights
;;
(def copyright-pattern #"(?i)^(?:.*((?:Copyright|\(C\)|©).*$))")

(defn copyright-lines
  "Extracts the copyright from a string, if any."
  [str]
  (second (re-matches copyright-pattern str)))

(defn copyrights-from-file
  "Extracts the copyrights from a file."
  [file]
  ; (println "Filename:" file)
  (->> file
       (slurp)
       (str/split-lines)
       (map copyright-lines)
       (remove nil?)
       (remove empty?)
       (map str/trim)
       (into #{})))

(defn copyrights-from-dir
  "Extracts copyrights from the files in the given directory."
  [dir]
  (let [java-files (sf/all-files-by-extension "java" (io/as-file dir))]
    ; (println java-files)
    (let [copyrights (->> java-files
                          (map copyrights-from-file)
                          (apply set/union))]
      ;(println copyrights)
      copyrights)))

(defn read-license
  "Reads the content of the file LICENSE.txt, if it exists."
  []
  (when (sf/exists? (io/as-file "scandir/LICENSE.txt"))
    (slurp (io/as-file "scandir/LICENSE.txt"))))

(defn read-notice
  "Reads the content of the file NOTICE.txt, if it exists."
  []
  (when (sf/exists? (io/as-file "scandir/NOTICE.txt"))
    (slurp (io/as-file "scandir/NOTICE.txt"))))

(defn scan-sources-jar
  "Unzips the source jar of the artifact and scans for copyright and notice information."
  [artifact]
  (when (get-option :debug)
    (println "scanning source jar for" (repo/artifact-version-key artifact)))

  (if (and (get-option :scan-sources) (repo/local-artifact? "sources" "jar" artifact))
    (do
      (sf/create-dir (io/as-file "scandir"))
      (z/unzip-file (str (repo/artifact-version-local-path artifact)
                         "/" (repo/artifact-filename "sources" "jar" artifact)) "scandir")

      (let [notice (read-notice)
            updated (update artifact :copyrights set/union (copyrights-from-dir "scandir"))]
        (println (:copyrights updated))
        (sf/delete-dir (io/as-file "scandir"))
        updated))
    artifact))

; evaluate the forms in REPL for quick tests
(comment
  (re-matches copyright-pattern "   Just a normal line")
  (re-matches copyright-pattern "   Copyright Ludger Solbach")
  (re-matches copyright-pattern "   copyright 2023 Ludger Solbach")
  (re-matches copyright-pattern "   (c) Ludger Solbach")
  (re-matches copyright-pattern "   (C) Ludger Solbach")
  (re-matches copyright-pattern "   © 2020 Ludger Solbach")
  (re-matches copyright-pattern "   (C) 2023 Ludger Solbach. All rights reserved.")
  (copyrights-from-file "C:/devel/code/foss/src/foss_report/core.clj"))

;;;
;;; handle artifact information
;;;

;;
;; project information
;;

(def github-pattern #"https://github.com/(.*)")
(def apache-pattern #"https://(.*)\.apache\.org.*")

(defn github-url?
  "Checks, if the project for this artifact is on github."
  [url]
  (re-matches github-pattern url))

(defn apache-url?
  "Checks, if the url is an apache url."
  [url]
  (re-matches apache-pattern url))

(defn apache-repository-url
  "Returns the github repository URL for an apache homepage."
  [url]
  (let [project (->> url
                     (re-matches apache-pattern)
                     (second))]
    (str "https://github.com/apache/" project)))

(defn repository-url
  "Returns the repository URL for the artifact."
  [url]
  (cond
    (github-url? url) url
    (apache-url? url) (apache-repository-url url)))

(defn contributors-url
  "Returns the repository URL for the artifact."
  [url]
  (cond
    (github-url? url) (str url "/graphs/contributors")
    (apache-url? url) (str (apache-repository-url url) "/graphs/contributors")))

; evaluate the forms in REPL for quick tests
(comment
  (github-url? "https://github.com/lsolbach")
  (apache-url? "https://spark.apache.org/")
  (repository-url "https://github.com/lsolbach")
  (repository-url "https://spark.apache.org/")
  (contributors-url "https://github.com/lsolbach")
  (contributors-url "https://spark.apache.org/"))

(defn license-unknown?
  "Returns true, if the artifact has no known license."
  [a]
  (nil? (seq (:licenses a))))

(defn single-licensed?
  "Returns true, if the artifact has only one license."
  [a]
  (= (count (:licenses a)) 1))

(defn gpl-only?
  "Returns true, if the artifact is only licensed with a GPL."
  [a]
  (and (single-licensed? a)
       (or (contains? (:licenses a) "GPL-2.0-only")
           (contains? (:licenses a) "GPL-3.0-or-later"))))

;;
;; handle maven thirdparty information
;;
(defn merge-artifacts
  "Merges maven third party artifacts.
   Called without arguments the function returns an empty artifact.
   Called with two artifacts the function returns a new artifact with the information merged."
  ([]
   {:components #{}
    :group-id nil
    :artifact-id nil
    :version nil
    :name nil
    :artifact-type "jar"
    :package-manager "mvn"
    :licenses #{}
    :delivery "binary"
    :integration "None"
    :homepage-url nil
    :spdx-ids #{}
    :copyrights #{}
    :repository nil
    :contributors nil})
  ([a b]
   {:components (set/union (:components a) (:components b)) ; TODO concat?
    :group-id (:group-id a)
    :artifact-id (:artifact-id a)
    :version (:version a) ; TODO decide, if a list of versions is better
    :name (:name a)
    :artifact-type (:artifact-type a)
    :package-manager (:package-manager a)
    :licenses (set/union (:licenses a) (:licenses b))
    :delivery (:delivery a)
    :integration (:integration a)
    :homepage-url (:homepage-url a)
    :spdx-ids (set/union (:spdx-ids a) (:spdx-ids b)) ; TODO set/union?
    :copyrights (set/union (:copyrights a) (:copyrights b))
    :repository (:repository a)
    :contributors (:contributors a)}))

(defn combine-artifacts
  "Combine the collection of artifacts based on the key function."
  [key-fn coll]
  (->> coll
       (group-by key-fn)
       (vals)
       (map #(reduce merge-artifacts %))
       (flatten)
       (into #{})))

;;
;; handle THIRD-PARTY.txt files generated by maven
;;
(defn split-third-party-licenses
  "Splits the licenses string and returns a seq of licenses."
  [s]
  ; remove outer parethesis with substring and split at ') ('.
  (str/split (subs s 1 (- (count s) 1)) #"\) \("))

(defn unix-path
  "Returns the unix path for the given path by replacing the backslashes with slashes."
  [s]
  (str/replace s "\\" "/"))

(defn component-from-path
  "Extracts the component name from the path of the 'THIRD-PARTY.txt' path string."
  ([path]
   (component-from-path 4 path))
  ([drops path]
   (->> path
        (unix-path)
        (sf/split-path "/")
        (drop-last drops) ; TODO depends on the relative location of the file
        (last))))

(defn update-artifact
  "Enhances the artifact with derived information."
  ([spdx-mapping a]
   (assoc a
          :spdx-ids (apply set/union
                           (map #(spdx-mapping %) (:licenses a))))))

(defn parse-third-party-line
  "Parses a line from a maven 'THIRD-PARTY.txt' file into an artifact map."
  [component line]
  (let [[_ licenses name group-id
         artifact-id version
         homepage-url] (re-matches
                        #"^\s*(\(.*\)(?: \(.*\))*) (.*) \((.*):(.*):(.*) - (.*)\)" ; line format regex
                        line)
        a {:components #{component}
           :group-id group-id
           :artifact-id artifact-id
           :version version
           :name name
           :artifact-type "jar"
           :package-manager "mvn"
           :licenses (into #{} (split-third-party-licenses licenses))
           :delivery "binary"
           :integration "None"
           :homepage-url homepage-url
           :repository (repository-url homepage-url)
           :contributors (contributors-url homepage-url)
           :spdx-ids #{}
           :copyrights #{}
           :notice ""}]
    a))

(defn artifacts-from-third-party-file
  "Reads the licenses from a maven 'THIRD-PARTY.txt' file. Returns a set of maps with the artifact information."
  [file]
  (let [component (component-from-path (sf/path file))] ; extract the component from the path of the third party file
    (->> (slurp file)
         (str/split-lines)
         (drop 2) ; first 2 lines don't contain dependencies
         (into #{} (map (partial parse-third-party-line component))))))

(defn artifacts-from-third-party-files
  "Returns a set of artifacts of all 'THIRD-PARTY.txt' files under the given directory."
  [dir]
  (->> (sf/all-files-by-pattern #".*THIRD-PARTY.txt" dir)
       (map artifacts-from-third-party-file)
       (apply set/union)))

; evaluate the forms in REPL for quick tests
(comment
  (component-from-path "data/fap5ea-core/THIRD-PARTY.txt")
  (component-from-path (sf/path "data/fap5ea-core/THIRD-PARTY.txt"))
  (component-from-path "C:\\git\\fap5ea-core\\target\\generated-sources\\license\\THIRD-Party.txt")
  (str/split "(The MIT License(MIT))" #"(?:\)) ")
  (str/split "(EPL 2.0) (GPL2 w/ CPE)" #"(?:\)) ")
  (split-third-party-licenses "(EPL 2.0) (GPL2 w/ CPE)")
  (parse-third-party-line "cmp"
                          "(Apache 2.0) Gson (com.google.code.gson:gson:2.8.6 - https://github.com/google/gson/gson)")
  (parse-third-party-line "cmp"
                          "(The MIT License(MIT)) Java implementation of GeographicLib (net.sf.geographiclib:GeographicLib-Java:1.50 - https://geographiclib.sourceforge.io)")
  (parse-third-party-line "cmp"
                          "(EPL 2.0) (GPL2 w/ CPE) Jakarta Expression Language 3.0 (org.glassfish:jakarta.el:3.0.3 - https://projects.eclipse.org/projects/ee4j.el)")
  (artifacts-from-third-party-file "data/fap5ea-core/THIRD-PARTY.txt")
  (count (artifacts-from-third-party-file "data/fap5ea-core/THIRD-PARTY.txt"))
  (artifacts-from-third-party-files "data")
  (count (artifacts-from-third-party-files "data"))
  (new-spdx-mapping (artifacts-from-third-party-files "data"))
  (merge-with set/union (read-mvn-spdx-mapping "txt2spdx.json") (new-spdx-mapping (artifacts-from-third-party-files "data")))
  (merge-with set/union (read-mvn-spdx-mapping "data2/txt2spdx.json") (new-spdx-mapping (artifacts-from-third-party-files "data")))
  (update-mvn-spdx-mapping "data/txt2spdx.json" "data")
  (->> (artifacts-from-third-party-files "data")
       (map (partial spdx-ids-for-licenses (read-mvn-spdx-mapping "data/txt2spdx.json")))
       (apply set/union)))

;;; 
;;; handle the FOSS excel files
;;;
(defn split-excel-licenses
  "Split the licenses in the excel cell."
  [licenses]
  (str/split licenses #"; "))

(comment
  (split-excel-licenses "EPL 2.0; GPL2 w/ CPE"))

(defn parse-excel-row
  "Parses a FOSS excel row into an artifact map."
  [[component group-id artifact-id version artifact-type licenses
    delivery integration homepage-url ohloh-url license-name spdx-id
    copyrights authors license-title license-delivery disclaimer repository]]
  {:components #{component}
   :group-id group-id
   :artifact-id artifact-id
   :version version
   :artifact-type artifact-type
   :licenses (into #{} (split-excel-licenses licenses))
   :delivery delivery
   :integration integration
   :homepage-url homepage-url
   :ohloh-url ohloh-url
   :license-name license-name
   :spdx-ids #{spdx-id}
   :copyrights #{}
   :authors authors
   :license-title license-title
   :license-delivery license-delivery
   :disclaimer disclaimer
   :repository repository
   :notice ""})

(defn artifacts-from-excel
  "Returns the set of artifact maps from the given excel."
  [file]
  (let [wb (xl/create-workbook file {:missingCellPolicy (xl/missing-cell-policy :create-null-as-blank)})]
    (->> (xl/sheet-values wb) ; get the content of the excel sheets
         (first) ; select first sheet
         (drop 4) ; skip to content rows
         (into #{} (map parse-excel-row)))))

;;
(defn diff-deps
  "Calculates the difference between two sets of artifacts. Set versions to false to ignore version information."
  ([set1 set2]
   (diff-deps set1 set2 true))
  ([set1 set2 versions]
   (let [key-fn (if versions repo/artifact-version-key repo/artifact-key)
         map1 (into (sorted-map) (map #(vector (key-fn %1) %1)) set1)
         coordinates-set1 (into (sorted-set) (map key-fn) set1)
         map2 (into (sorted-map) (map #(vector (key-fn %1) %1)) set2)
         coordinates-set2 (into (sorted-set) (map key-fn) set2)
         ; diff key sets
         both (set/intersection coordinates-set1 coordinates-set2)
         only1 (set/difference coordinates-set1 coordinates-set2)
         only2 (set/difference coordinates-set2 coordinates-set1)]
     {:both (map #(map2 %) both)
      :only1 (map #(map1 %) only1)
      :only2 (map #(map2 %) only2)})))

(comment
  ; evaluate the forms in REPL for quick tests
  (artifacts-from-third-party-files "data")
  (artifacts-from-excel "data/FOSS.xlsm")
  (merge-with set/union (read-mvn-spdx-mapping "data2/txt2spdx.json") (new-spdx-mapping (artifacts-from-excel "data2/FOSS_Report.xlsx")))
  (into #{}
        (map (partial update-artifact (read-mvn-spdx-mapping "data/txt2spdx.json")))
        (artifacts-from-third-party-files "data"))
  (print (diff-deps (artifacts-from-third-party-files "data") (artifacts-from-excel "data/FOSS.xlsm")))
  (print (diff-deps (artifacts-from-third-party-files "data") (artifacts-from-excel "data/FOSS.xlsm") true))
  )
