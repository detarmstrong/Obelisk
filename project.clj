(defproject red-jem "1.1.4"
  :description "A notepad for Redmine; create tickets fast"
  :dependencies [[org.clojure/clojure "1.5.1"]
                  [seesaw "1.4.4"]
                  [cheshire "5.3.1"]
                  [clj-http "0.9.1"]
                  [org.clojure/core.memoize "0.5.6"]
                  [org.clojure/data.xml "0.0.7"]]
  :profiles {:dev {:dependencies [[midje "1.5.0"]]}}
  :aot [clojure.main red_jem.core]
  :main red_jem.core
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :jvm-opts ["-Xmx128m"])

