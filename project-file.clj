(defproject softplc-clj "0.1.0-SNAPSHOT"
  :description "A Clojure implementation of SoftPLC"
  :url "https://github.com/yourusername/softplc-clj"
  :license {:name "GPLv3"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [quil "4.3.1562"]  ; For GUI and visualization
                 [org.clojure/data.json "2.4.0"]  ; JSON handling
                 [org.clojure/tools.cli "1.0.219"]  ; Command-line parsing
                 [cheshire "5.11.0"]  ; JSON parsing/generation
                 [org.clojure/core.async "1.6.681"]]  ; For async operations
  :main ^:skip-aot softplc-clj.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                        :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[org.clojure/test.check "1.1.1"]]}}
  :resource-paths ["resources"])
