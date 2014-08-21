(ns red-jem.t-web-api
  (:use midje.sweet)
  (:use [red-jem.web-api]))

(defn user-home-dir []
  (System/getProperty "user.home"))

(facts "about obelisk_token_file_path"
       (fact "it is located in the user's home directory"
             obelisk-token-file-path
             =>
             (contains (user-home-dir))))