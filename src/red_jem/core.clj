(ns red-jem.core
  (:gen-class) ; required for uberjar
  (:require [red-jem.web-api :as web-api])
  (:require [clojure.java.io :as io])
  (:use [clj-http.util :only (url-encode)])
  (:use seesaw.core)
  (:use seesaw.keymap)
  (:use [clojure.string :only (join lower-case)])
  (:import (java.awt Desktop) 
           (java.net URI)))

(native!)

(def api-token)

(def area (text :multi-line? true
                            :text ""
                            :wrap-lines? true))

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
  (apply str (map clojure.string/join 
                  (let [v (split-at at-pos string)]
                    [(first v) piece (second v)]))))

(defn insert-rm-subject [widge]
  (if-let [selectio (selection widge)]
    (text! widge 
           (insert 
             (str " " (issue-subject 
                        (get-selected-text widge)))
             (config widge :text)
             (second selectio)))))

(defn insert-new-issue-id 
  "Insert the passed in id at position in the string of the widget"
  [issue-id position in-widget]
  (text! 
    in-widget
    (insert (str issue-id " ")
            (text in-widget)
            position))
  (config! in-widget :caret-position position))

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

(def red-jem-frame
  (frame
    :title "Obelisk"
    :id :red-jem
    :on-close :hide
    :content (border-panel 
               :vgap 5
               :north (toolbar 
                        :floatable? false
                        :items [(button 
                                  :id :save-button
                                  :text "Save")
                                :separator
                                ;(button
                                ;  :id :import-button
                                ;  :text "Import")
                                [:fill-h 5]
                                (button
                                  :id :go-to-button
                                  :text "Go to ...")])
               :center scrollable-area)))

(def options-ok-btn
  (button :text "Continue"
                    :margin 10))

(def options-panel
  (scrollable (horizontal-panel 
    :class :hp 
    :items [(scrollable projects-lb) 
            (scrollable members-lb)
             options-ok-btn])))

(def options-frame
  (frame
    :title "Pick"
    :id :options-frame
    :on-close :hide
    :size [728 :by 230]))

(def projects-frame
  (frame
    :title "Projects"
    :id :projects-frame
    :on-close :hide
    :content (horizontal-panel
               :items [(scrollable (listbox
                                     :id :go-to-projects-lb
                                     :renderer list-renderer))
                       (button
                         :id :go-to-project-button
                         :text "Go")])))

(defn go-to-feature-button-handler [event]
  (config! 
    (select projects-frame [:#go-to-projects-lb])
    :model (projects-listbox-model))
  (-> projects-frame pack! show!))

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

(listen (select red-jem-frame [:#go-to-button]) :action 
        go-to-feature-button-handler)

(listen (select projects-frame [:#go-to-project-button]) :action 
  (fn [e] 
    (open-url-on-desktop
      (str "http://redmine.visiontree.com/projects/" 
         (:id (selection (select projects-frame
                                 [:#go-to-projects-lb])))))
    (-> projects-frame hide!)))

(listen (select red-jem-frame [:#save-button]) :action
  (fn [e]
    (print "save clicked")
    (spit ".obelisk" (config area :text))))

(listen projects-lb :mouse-clicked
  (fn [e]
    (def project-keys-log [])
    (handle-event on-project-selected)))

(def project-keys-log [])

(def projects-lb-key-listener 
  (listen projects-lb :key-pressed
        (fn [e]
          (let [keyed (str (.getKeyChar e))
                keyed-code (.getKeyCode e)
                count-keys-entered (count project-keys-log)
                projects-model (.toArray (.getModel projects-lb))]
            (if (and (= keyed-code 8) (> count-keys-entered 0)) ; 8 = backspace
              (def project-keys-log 
                (subvec project-keys-log 0 (- count-keys-entered 1)))
              (def project-keys-log 
                (conj project-keys-log keyed)))
            
            (if-let [search-on (first
                                 (filter (fn [x] 
                                           (re-seq 
                                             (re-pattern 
                                               (str "(?i)^" 
                                                    (apply str project-keys-log))) 
                                             (:name x)))
                                         projects-model))]
              (do (selection! projects-lb search-on)
                (scroll! projects-lb :to [:row (.getSelectedIndex projects-lb)]))
              0)
            
            (handle-event on-project-selected)))))

(listen members-lb :selection
  (fn [e]
    (handle-event on-project-member-selected)))

(listen options-ok-btn :mouse-clicked
  (fn [e]
    (let [project-id (:id (selection projects-lb))
          member-id (:id (selection members-lb))
          selected-text (get-selected-text area)
          selected-text-start (first (selection area))
          text-seq (seq (.split #"\n" selected-text))
          subject (first text-seq)
          body (rest text-seq)
          parent-issue-id ""
          response (web-api/create-issue 
                       subject
                       (join "\n" body)
                       project-id
                       member-id
                       parent-issue-id)
          issue-id (get-in
                     response
                     [:body :issue :id])]
        (insert-new-issue-id issue-id selected-text-start area))
        
    (-> options-frame hide!)))

(defn valid-token? []
  (try (web-api/valid-token?)
    (catch java.net.ConnectException e 
      (alert "Connection timed out. On VPN?"))))

(defn init-red-jem  []
  ; test for note file
  (if (-> (io/file ".obelisk") (.isFile))
    (text! area (slurp ".obelisk"))
    (spit ".obelisk" ""))
  
  (if (web-api/token?)
    (web-api/load-token)
    (alert (str "Redmine api token not found.\n\nCreate a file called " 
                web-api/obelisk-token-file-path 
                " and paste token found under you account settings."))))

(init-red-jem)