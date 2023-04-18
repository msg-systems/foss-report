(defproject group.msg/foss-report "0.6.4"
  :description "Generate various reports regarding foss licenses."

  ; use dependencies from deps.edn
  ;:plugins [[lein-tools-deps "0.4.5"]]
  ;:middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  ;:lein-tools-deps/config {:config-files [:install :user :project]}

  ; use leiningen dependencies (workaround for windows)
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/data.csv "1.0.1"]
                 [org.clojure/tools.cli "1.0.206"]
                 [org.soulspace.clj/clj.base "0.9.1"]
                 [org.soulspace.clj/cmp.poi "0.6.4"]
                 [org.soulspace.clj/tools.repository "0.3.4"]]
  
  :test-paths ["test"]
  :uberjar-name "foss-report.jar"
  :main foss-report.main)
