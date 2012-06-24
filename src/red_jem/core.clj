(ns red-jem.core
  (:require [red-jem.web-api :as web-api])
  (:use seesaw.core)
  (:use seesaw.keymap)
  (:use [clojure.string :only (join)])
  (:import (java.awt Desktop) (java.net URI)))

(native!)

(def red-jem-frame
  (frame
    :title "Obelisk"
    :id :red-jem
    :on-close :hide))

(def area (text :multi-line? true
                :text ""
                :size [300 :by 300]))

(def lb (listbox :size [160 :by 80]))

(def popup-win (window :content lb
                       :size [160 :by 80]))

(defn get-project-member-name [{:keys [user]}]
  (get-in user [:name]))

; :model (project-members "tim01")

(defn get-selected-text [text-widget]
  (let [rang (selection text-widget)]
    (subs (config text-widget :text) (first rang) (second rang))))

(defn issue-subject [id]
  (-> (web-api/issue id) (get-in [:body :issue :subject])))

(defn insert [piece string at-pos]
  (apply str (map clojure.string/join (let [v (split-at at-pos string)]
    [(first v) piece (second v)]))))

(defn insert-rm-subject [widge]
  (if-let [selectio (selection widge)]
    (text! widge (insert (str " " (issue-subject (get-selected-text widge))) (config widge :text) (second selectio)))))

(defn browse-ticket [issue-id]
  (doto 
    (Desktop/getDesktop) 
    (.browse 
      (URI/create 
        (format "http://redmine.visiontree.com/issues/%s" issue-id)))))

(map-key area "control R"
  (fn [widget]
    (insert-rm-subject widget)))

(map-key area "control G"
  (fn [widget]
    (browse-ticket (get-selected-text widget))))

(map-key area "control N"
  (fn [widget]
    (text! widget (map get-project-member-name (web-api/project-members "tim01")))))

(map-key area "control T"
  (fn [widget]
    ; treat the first selected line as subject, rest as body
    (let [selected-text (get-selected-text widget)
          text-seq (seq (.split #"\n" selected-text))
          subject (first text-seq)
          body (rest text-seq)
          project-id "tim01"
          parent-issue-id ""]
      (web-api/create-issue subject (join "\n" body) project-id parent-issue-id))))

(defn display [content]
  (config! red-jem-frame :content content))

(display area)

(defn -main [& args]
  (invoke-later
    (-> red-jem-frame
     pack!
     show!)))

(-> red-jem-frame pack! show!)
