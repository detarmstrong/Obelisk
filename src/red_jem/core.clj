(ns red-jem.core
  (:require [red-jem.web-api :as web-api])
  (:require [red-jem.state-machine :as sm])
  (:use [clj-http.util :only (url-encode)])
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

(defn list-renderer [renderer info]
  (let [v (:value info)]
    (apply config! renderer
           [:text (str (:name v))])))


(def members-lb (listbox :renderer list-renderer))
(def projects-lb (listbox :renderer list-renderer))

(defn get-project-member [{:keys [user]}]
  {:name (get-in user [:name]) :id (get-in user [:id])})

(defn get-project [{:keys [name identifier]}]
  {:name name :id identifier})

(defn projects-listbox-model []
  (map get-project (web-api/projects)))


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

(defn open-url-on-desktop
  "Given a url open it in the default desktop browser"
  [url]
  (doto 
    (Desktop/getDesktop) 
    (.browse 
      (URI/create url))))
  
(defn redmine-search-url
  "Generate query string that is a search of rm"
  [search-string project resources]
  (let [query-map {:all_words 1
                   :q (url-encode search-string)}
        resources-map (apply hash-map 
           (interleave resources (repeat 
                                   (count resources) 1)))
        combined-map (merge query-map resources-map)]

    (str "http://redmine.visiontree.com/"
         (if (= project "")
           "search"
           (str "projects/" project))
         "?"
         (join "&" 
               (for [[k v] combined-map] 
                 (str (name k) "=" v))))))

(def resources
  '(:issues :news :documents :wiki_pages :changesets))

(def options-ok-btn
  (button :text "Continue"
                    :margin 10))

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

(load "events")

(map-key area "control R"
  ; Read in the subject of this ticket #
  (fn [widget]
    (insert-rm-subject widget)))

(map-key area "control G"
  ; Go search this text
  (fn [widget]
    (open-url-on-desktop 
      (redmine-search-url (get-selected-text widget) "" resources))))

(map-key area "control T"
  ; Ticket this
  (fn [widget]
    (handle-event on-create-ticket-form-visible)))

(listen projects-lb :selection
  (fn [e]
    (handle-event on-project-selected)))

(listen members-lb :selection
  (fn [e]
    (handle-event on-project-member-selected)))

(listen options-ok-btn :mouse-clicked
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
    (-> options-frame hide!)
    
    (println "ok")))