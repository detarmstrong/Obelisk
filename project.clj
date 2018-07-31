(defproject red-jem "1.1.4"
  :description "A notepad for Redmine; create tickets fast"
  :dependencies [[org.clojure/clojure "1.6.0"]
                  [seesaw "1.5.0"]
                  [cheshire "5.8.0"]
                  [clj-http "2.2.0"]
                  [org.clojure/core.memoize "0.7.1"]
                  [org.clojure/data.xml "0.0.8"]]
  :profiles {:dev {:dependencies [[midje "1.9.2"]]}}
  ;:aot [clojure.main red-jem.core]
  :main red-jem.core
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :jvm-opts ["-Xmx128m"])

