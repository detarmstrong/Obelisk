(ns red-jem.core
  (:require [red-jem.web-api :as web-api])
  (:require [red-jem.state-machine :as sm])
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

(def options-frame
  (frame
    :title "Pick"
    :id :options-frame
    :on-close :hide))

(def area (text :multi-line? true
                            :text ""))

(def scrollable-area (scrollable area 
                             :size [300 :by 350]
                             :border 0))

(def members-lb (listbox))
(def projects-lb (listbox))

(defn get-project-member [{:keys [user]}]
  {:name (get-in user [:name]) :id (get-in user [:id])})

(defn get-project [{:keys [name identifier]}]
  {:name name :id identifier})

(defn projects-listbox-model []
  (map get-project (web-api/projects)))

(defn projects-list-renderer [renderer info]
  (let [v (:value info)]
    (apply config! renderer
           [:text (str (:name v))])))

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
    (text! widge 
           (insert 
             (str " " (issue-subject 
                        (get-selected-text widge))) (config widge :text) (second selectio)))))

(defn browse-ticket [issue-id]
  (doto 
    (Desktop/getDesktop) 
    (.browse 
      (URI/create 
        (format "http://redmine.visiontree.com/issues/%s" issue-id)))))

(map-key area "control R"
  ; Read in the subject of this ticket #
  (fn [widget]
    (insert-rm-subject widget)))

(map-key area "control G"
  ; Go to this ticket in a browser
  (fn [widget]
    (browse-ticket (get-selected-text widget))))

(map-key area "control T"
  ; Ticket this
  (fn [widget]
    ; show the options frame to pick the project and then the member
    (config! projects-lb :model (projects-listbox-model)
             :renderer projects-list-renderer)
    (config! members-lb :model (map 
                                 get-project-member (web-api/project-members "tim01")))
   
    (-> options-frame pack! show!)))

(def options-ok-btn
  (button :text "Continue"
                    :margin 10))

(listen options-ok-btn :selection
  (fn [e]
    (let [project-id (:id (selection projects-lb))
          member-id (:id (selection members-lb))
          selected-text (get-selected-text area)
          text-seq (seq (.split #"\n" selected-text))
          subject (first text-seq)
          body (rest text-seq)
          parent-issue-id ""]
      (web-api/create-issue subject (join "\n" body) project-id member-id parent-issue-id))
    
    ; close window
    (println "ok")))

(def options-panel
  (scrollable (horizontal-panel 
    :class :hp 
    :items [(scrollable projects-lb) 
            (scrollable members-lb)
             options-ok-btn])))

(config! red-jem-frame :content scrollable-area)
(config! options-frame :content options-panel)

(defn -main [& args]
  (invoke-later
    (-> red-jem-frame
     pack!
     show!)))

(-> red-jem-frame pack! show!)
