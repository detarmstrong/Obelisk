(defproject red-jem "1.1.0"
  :description "Obelisk is a native app for Redmine"
  :dependencies [[org.clojure/clojure "1.3.0"]
                  [seesaw "1.4.2"]
                  [cheshire "4.0.0"]
                  [clj-http "0.4.1"]
                  [org.clojure/core.memoize "0.5.2"]]
  :main red_jem.core
  :jvm-opts ["-Xdock:name=Obelisk"]) ; this doesn't work consistently

