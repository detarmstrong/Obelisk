(defproject red-jem "1.1.1"
  :description "Obelisk is a native app for Redmine"
  :dependencies [[org.clojure/clojure "1.4.0"]
                  [seesaw "1.4.4"]
                  [cheshire "5.3.1"]
                  [clj-http "0.9.1"]
                  [org.clojure/core.memoize "0.5.6"]]
  :main red_jem.core
  :jvm-opts ["-Xdock:name=Obelisk"]) ; this doesn't work consistently

