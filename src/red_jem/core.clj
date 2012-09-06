(ns red-jem.core
  (:gen-class) ; required for uberjar
  (:use [seesaw core mig])
  (:require [red-jem.web-api :as web-api]
            [clojure.java.io :as io]
            [seesaw.rsyntax :as rsyntax])
  (:use [clj-http.util :only (url-encode)])
  (:use seesaw.keymap)
  (:use [red-jem.at-at :as at-at])
  (:use [clojure.string :only (join lower-case)])
  (:import (java.awt Desktop)
           (java.net URI)
           (java.awt Color)
           javax.swing.text.DefaultHighlighter$DefaultHighlightPainter))

(native!)

(declare valid-token?)
(declare highlight-matching-text)

(def at-at-pool (mk-pool))

(def api-token)

(def area (rsyntax/text-area
                :text ""
                :wrap-lines? true
                :tab-size 2
                :syntax :none))

(def obelisk-note-file
  (let [dot-file ".obelisk"
        home-dir (System/getProperty "user.home")
        file-separator (System/getProperty "file.separator")
        full-path (apply str (interpose file-separator [home-dir dot-file]))]
    (io/file full-path)))

(defn load-or-create-note-file []
  (if (-> obelisk-note-file (.isFile))
    (text! area (slurp obelisk-note-file))
    (spit obelisk-note-file "")))

(defn save-note-file []
  (spit obelisk-note-file (text area)))

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

(defn open-config-dialog [parent-frame]
  "Builds and shows dialog for configuring obelisk. Returns values of form"
  (let [ok-act (action
                 :name "Ok"
                 :handler (fn [e]
                            (let [values (value (to-frame e))
                                  api-key (:api-key values)
                                  check-connection? (:check-connection values)]
                              (if check-connection?
                                (let [passed-test (valid-token? api-key)]
                                  (do 
                                    (alert (str "Is token valid? " passed-test))
                                    (return-from-dialog e values)))
                                (do 
                                  (return-from-dialog e values)))
                              (spit web-api/obelisk-token-file-path api-key)
                              (web-api/load-token))))
        cancel-act (action
                     :name "Cancel"
                     :handler (fn [e] (return-from-dialog e nil)))
        api-key (slurp web-api/obelisk-token-file-path)]
    (-> (custom-dialog
         :title "Config"
         :parent parent-frame
         :modal? true
         :resizable? false
         :content (mig-panel
                    :items [["Redmine API key"]
                            [(text
                               :id :api-key
                               :text api-key
                               :columns 25 
                               :multi-line? false) "wrap"]
                            ;["Redmine URL"]
                            ;[(text
                            ;   :id :api-url
                            ;   :columns 25
                            ;   :multi-line? false) "wrap"]
                            [(checkbox :id :check-connection
                                       :text "Check connection"
                                       :selected? true)]
                            [(flow-panel :align :right 
                                         :items [cancel-act ok-act]) "growx, spanx 2" "alignx right"]]))
     pack!
     show!)))

(def red-jem-frame
  (frame
    :title "Obelisk"
    :id :red-jem
    :on-close :exit
    :icon "gear.png"
    :content (border-panel
               :id :red-jem-border-panel
               :north (border-panel 
                        :west (horizontal-panel
                                :items [(button
                                          :id :go-to-button
                                          :text "Go to ...")])
                        :east (horizontal-panel
                                :items [(button
                                          :id :config-button
                                          :icon (clojure.java.io/resource "gear.png"))
                                        [:fill-h 5]])
                        :south (horizontal-panel
                                 :opaque? false
                                 :id :search-panel
                                 :visible? false
                                 :items [" "
                                         "Find" 
                                         (text 
                                           :id :search-text-field)
                                         (button
                                           :id :close-search
                                           :icon (clojure.java.io/resource "close-button.png"))
                                         " "]))
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

(defn config-button-handler [e]
  (open-config-dialog red-jem-frame))

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

; comment out during uberjaring
; (-> red-jem-frame pack! show!)

(load "events")

(listen (select red-jem-frame ["#config-button"]) :action 
        config-button-handler)

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

(map-key area "control F"
         (fn [widget]
           (config! (select red-jem-frame [:#search-panel]) :visible? true)
           (request-focus! (select red-jem-frame [:#search-text-field]))))

(listen (select red-jem-frame [:#go-to-button]) :action 
        go-to-feature-button-handler)

(listen (select projects-frame [:#go-to-project-button]) :action 
  (fn [e] 
    (open-url-on-desktop
      (str "http://redmine.visiontree.com/projects/" 
         (:id (selection (select projects-frame
                                 [:#go-to-projects-lb])))))
    (-> projects-frame hide!)))

(listen projects-lb :mouse-clicked
  (fn [e]
    (def project-keys-log [])
    (handle-event on-project-selected)))

(listen (select red-jem-frame [:#close-search]) :action
        (fn [e]
          (let [highlighter (.getHighlighter area)
                highlights (.getHighlights highlighter)]
            (doseq [highlight highlights]
              (doto highlighter
                (.removeHighlight highlight))))
          (config! (select (to-root e) [:#search-panel]) :visible? false)))

(def text-search-listener (listen (select red-jem-frame [:#search-text-field]) #{:remove-update :insert-update}
        (fn [e]
          (try
            (highlight-matching-text (text e) area)
            (catch java.util.regex.PatternSyntaxException e
              (print "invalid regex"))))))

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

(listen options-ok-btn :action
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

(declare save-countdown)
(defn set-area-doc-listener [] 
  (def area-doc-listener 
	  (listen area #{:remove-update :insert-update} 
	    (fn [e]
        (at-at/stop save-countdown)
	      (def save-countdown 
         (at-at/after 
           5000 
           #(save-note-file)
           at-at-pool))))))

(defn valid-token? [api-key]
  (try (web-api/valid-token? api-key)
    (catch org.apache.http.conn.ConnectTimeoutException e 
      (do (alert "Connection timed out. On VPN?") false))))

(defn re-pos [re s]
  (loop [m (re-matcher re s)
         res []]
    (if (.find m)
      (recur m (conj res [(.start m) (.end m)]))
      res)))

(defn highlight-matching-text [search-string textarea]
  (let [text (text textarea)
        positions (re-pos (re-pattern (str "(?i)" search-string)) text)
        highlight-painter (new DefaultHighlighter$DefaultHighlightPainter Color/BLUE)
        highlighter (.getHighlighter textarea)
        highlights (.getHighlights highlighter)]

    (doseq [highlight highlights]
      (doto highlighter
        (.removeHighlight highlight)))
    
    (doseq [pos positions]
      (if-not (= search-string "")
        (doto
          highlighter
          (.addHighlight
            (first pos) (second pos) 
            highlight-painter))))))
  
(defn init-red-jem  []
  (load-or-create-note-file)
  (set-area-doc-listener)
  
  ; setting :border on the button doesn't work :(
  (config! (select red-jem-frame [:#close-search]) :border nil)
  
  (if (web-api/token?)
    (web-api/load-token)
    (do 
      (spit web-api/obelisk-token-file-path "")
      (config-button-handler nil))))

(init-red-jem)