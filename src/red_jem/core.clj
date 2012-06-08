(ns red-jem.core
  (:use seesaw.core)
  (:use seesaw.keymap)
  (:use cheshire.core)
  (:use [clojure.string :only (trim join)])
  (:require [clj-http.client :as client])
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
(def api-token 
  "e647bfc4397bc3f8010521b36c72d182477d11ee")

(defn project-members [project-id]
  (client/get 
    (format "http://redmine.visiontree.com/projects/%s/memberships.json"
      project-id) 
    {:basic-auth [api-token "d"]}))

; :model (project-members "tim01")


(def popup-win (window :content (listbox :size [160 :by 80])
                       :size [160 :by 80]))

(defn get-selected-text [text-widget]
  (let [rang (selection text-widget)]
    (subs (config text-widget :text) (first rang) (second rang))))

(defn issue [id]
  (client/get 
    (format "http://redmine.visiontree.com/issues/%d.json" 
      (#(Integer/parseInt %) (trim id))) 
    {:as :json
     :basic-auth [api-token "random"]
     :query-params {:include "children"}}))

(defn issue-subject [id]
  (-> (issue id) (get-in [:body :issue :subject])))


(defn create-issue [subject body project-id parent-issue-id]
  (client/post "http://redmine.visiontree.com/issues.json"
    {:basic-auth [api-token "random"]
     :body (generate-string {:issue {:subject subject 
                                     :description body
                                     :project_id project-id
                                     :parent_issue_id parent-issue-id}})
     :content-type :json
     :socket-timeout 1000
     :conn-timeout 1000
     :accept :json
     :throw-entire-message? true}))

;(listen area :key-released
;  (fn [e]
;    (if-let [selectio (selection e)]
;      (println (format "selection is '%s'" selectio)))))

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

(defn display [content]
  (config! red-jem-frame :content content)
  content)

(display area)

(defn -main [& args]
  (invoke-later
    (-> red-jem-frame
     pack!
     show!)))



(-> red-jem-frame pack! show!)
