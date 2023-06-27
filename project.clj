(defproject group.msg/foss-report "0.6.5-SNAPSHOT"
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
                 [org.soulspace.clj/clj.java "0.9.1"]
                 [org.soulspace.clj/cmp.poi "0.6.4"]
                 [org.soulspace.clj/tools.repository "0.3.7"]]

  :test-paths ["test"]
  :uberjar-name "foss-report.jar"
  :main foss-report.main

  :profiles {:dev {:dependencies [[org.clojure/test.check "1.1.1"]
                                  [djblue/portal "0.37.1"]
                                  [criterium "0.4.6"]
                                  [com.clojure-goes-fast/clj-java-decompiler "0.3.4"]
                                  [expound/expound "0.9.0"]]
                   :global-vars {*warn-on-reflection* true}}}

  :scm {:name "git" :url "https://github.com/msg-systems/foss-report"}
  :deploy-repositories [["clojars" {:sign-releases false :url "https://clojars.org/repo"}]])
