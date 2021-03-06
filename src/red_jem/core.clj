(ns red-jem.core
  (:gen-class) ; required for uberjar
  (:require [red-jem.web-api :as web-api]
            [whitespace-tree.core :as ws-tree]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [seesaw.rsyntax :as rsyntax]
            [string.string])
  (:use clojure.core.memoize)
  (:use seesaw.keymap)
  (:use [seesaw core mig])
  (:use [clj-http.util :only (url-encode)])
  (:use [red-jem.at-at :as at-at])
  (:use [clojure.string :only (join lower-case trim trimr split-lines)])
  (:import (java.awt Desktop)
           (java.awt Color)
           (java.awt.event KeyEvent)
           (java.net URI)
           javax.swing.text.DefaultHighlighter$DefaultHighlightPainter))

(native!)

(declare valid-token?)
(declare highlight-matching-text)
(declare key-logs)

(def at-at-pool (mk-pool))

(def api-token)

(def highlight-color
  Color/cyan)

(def area (rsyntax/text-area
                :text ""
                :wrap-lines? true
                :tab-size 2
                :syntax :none))

(defn unbind-ctrl+d []
  "Unbind ctrl+d, which is delete line by default in rsyntax area"
  (let [mod (.. area (getToolkit) (getMenuShortcutKeyMask))
        d-key-stroke (javax.swing.KeyStroke/getKeyStroke
                       java.awt.event.KeyEvent/VK_D mod)
        input-map (.. area (getInputMap))]
    (doto input-map (.put d-key-stroke "nothing"))))

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


(def members-lb (combobox :renderer list-renderer
                          :size [200 :by 22]))
(def projects-lb (listbox :renderer list-renderer))

(defn get-project-member [{:keys [user]}]
  (let [name (get-in user [:name])
        id (get-in user [:id])]
    (if-not (= name nil)
      {:name name :id id}
      {:name "unknown" :id -1})))

(defn get-project [{:keys [name identifier]}]
  {:name name :id identifier})

(def memoized-projects (memo web-api/projects))
(defn projects-listbox-model []
  (map get-project (memoized-projects)))

(defn get-selected-text [text-widget]
  (let [rang (selection text-widget)]
    (subs (config text-widget :text) (first rang) (second rang))))

(defn issue-subject [id]
  (-> (web-api/issue id) (get-in [:body :issue :subject])))

(defn insert [piece string at-pos]
  (apply str (map clojure.string/join 
                  (let [v (split-at at-pos string)]
                    [(first v) piece (second v)]))))

(defn insert-text [piece string at-pos widge]
  "Insert some text into the text contained in the widget"
  (text! widge
         (insert piece string at-pos)))

(defn insert-rm-subject [widge]
  (if-let [selectio (selection widge)]
    (do
      (text! widge 
             (insert 
               (str " " (issue-subject 
                          (get-selected-text widge)))
               (config widge :text)
               (second selectio)))
      (config! widge :caret-position (first selectio)))))

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

(defn redmine-bulk-edit-url [ticket-ids]
  "Return bulk-edit mode url given supplied ticket-ids"
  (str "http://redmine.visiontree.com/issues/bulk_edit?"
       (apply str
              (interleave (repeat (count ticket-ids) "ids[]=")
                          ticket-ids
                          (repeat (count ticket-ids) "&")))))

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
                                    (alert (str "Token is "
                                                (if passed-test
                                                  "valid!"
                                                  "invalid")))
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
                                         :items [cancel-act ok-act])
                             "growx, spanx 2" "alignx right"]]))
     pack!
     show!)))

(def red-jem-frame
  (frame
    :title "Obelisk"
    :id :red-jem
    :on-close :exit
    :icon "obelisk_icon.png"
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
                                           :icon (clojure.java.io/resource
                                                   "close-button.png"))
                                         " "]))
               :center scrollable-area)))

(def options-ok-btn
  (button :text "Finish"
                    :margin 6))

(def options-panel
  (scrollable
		(vertical-panel :items [(horizontal-panel 
															 :class :hp 
															 :items [(vertical-panel :items [(scrollable projects-lb
																																					 :size [300 :by 400]
																																					 :border 0)
																															 :fill-v])
																			 (vertical-panel :id :members-panel
																											 :items [])
																			 (vertical-panel :id :lines-panel 
																											 :items [])
																							 :fill-h])
														(horizontal-panel :items [options-ok-btn])])))

(def options-frame
  (frame
    :title "Assign"
    :id :options-frame
    :on-close :hide
    :size [930 :by 460]))

(def projects-frame
  (frame
    :title "Projects"
    :id :projects-frame
    :on-close :hide
    :content (horizontal-panel
               :items [(scrollable (listbox
                                     :id :go-to-projects-lb
                                     :renderer list-renderer))
                       (action
                         :name "Go"
                         :handler (fn [e] 
                                   (open-url-on-desktop
                                     (str "http://redmine.visiontree.com/projects/" 
                                          (:id (selection (select projects-frame
                                                                  [:#go-to-projects-lb])))))
                                   (-> projects-frame hide!)))])))

(defn config-button-handler [e]
  (open-config-dialog red-jem-frame))

(defn go-to-feature-button-handler [event]
  (let [widge (select projects-frame [:#go-to-projects-lb])
        key-log (get-in key-logs [widge])]
    ((key-log :truncate!)))
  
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

; uncomment if uberjarring - seems to make it work
(-> red-jem-frame pack! show!)

(load "events")

(listen options-frame :window-closing
        (fn [e]
          (let [projects-key-log (get-in key-logs [projects-lb])
                members-key-log (get-in key-logs [members-lb])]
            ((projects-key-log :truncate!))
            ((members-key-log :truncate!)))
          (request-focus! projects-lb)))

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
    (on-create-ticket-form-visible widget)))

(map-key area "control F"
         (fn [widget]
           (config! (select red-jem-frame [:#search-panel]) :visible? true)
           (request-focus! (select red-jem-frame [:#search-text-field]))))

(map-key area "control D"
         (fn [widget]
           (if-let [selectio (selection widget)]
             (do 
               (text! widget
                      (let [[range-start range-end] selectio
                            full-text (config widget :text)
                            prev-new-line-pos (string.string/find-prev-occurrence-of-char
                                                "\n"
                                                full-text
                                                range-start)
                            text-to-comment (subs full-text
                                                  (inc prev-new-line-pos)
                                                  range-end)
                            formatted-comment (string.string/linewise-prepend
                                                "// "
                                                text-to-comment
                                                true)]
                            (str (subs full-text 0 (inc prev-new-line-pos))
                                 formatted-comment
                                 (subs full-text range-end (count full-text)))))

               (config! widget :caret-position (first selectio))))))

(map-key area "control E"
         (fn [widget]
           (if-let [selectio (selection widget)]
             (let [selected-text (get-selected-text widget)]
               (open-url-on-desktop
                 (redmine-bulk-edit-url
                   (ws-tree/collect-ticket-ids selected-text)))))))

(listen (select red-jem-frame [:#go-to-button]) :action 
        go-to-feature-button-handler)

(listen projects-lb :selection
  (fn [e]
    (if-not (.getValueIsAdjusting (to-widget e))
      (handle-event on-project-selected))))

(listen projects-lb :mouse-clicked
        (fn [e]
          (let [key-log (get-in key-logs [(to-widget e)])]
            ((key-log :truncate!)))))

(listen (select red-jem-frame [:#close-search]) :action
        (fn [e]
          (let [highlighter (.getHighlighter area)
                highlights (.getHighlights highlighter)]
            (doseq [highlight highlights]
              (doto highlighter
                (.removeHighlight highlight))))
          (config! (select (to-root e) [:#search-panel]) :visible? false)))

(def text-search-listener (listen (select red-jem-frame
                                          [:#search-text-field])
                                  #{:remove-update :insert-update}
        (fn [e]
          (try
            (highlight-matching-text (text e) area)
            (catch java.util.regex.PatternSyntaxException e
              (print "invalid regex"))))))

; closure time!
(defn make-key-log [init-val]
  (let [c (atom init-val)]
    {:push! #(reset! c (conj @c %))
     :pop! #(reset! c (subvec @c 0 (- (count @c) 1)))
     :truncate! #(reset! c [])
     :count #(count @c)
     :as-string #(apply str @c)}))

(def key-logs
  "array of vectors - each vector is a stack of keys pressed while a 
   listbox has focus. lb widget is key"
  {projects-lb (make-key-log [])
   members-lb (make-key-log [])
   (select projects-frame [:#go-to-projects-lb]) (make-key-log [])})

(defn on-lb-key-up
  "Generic key listener for listbox. Select as you type."
  [e]
  (let [$listbox (to-widget e)
        keyed (str (.getKeyChar e))
        keyed-code (.getKeyCode e)
        listbox-model (.toArray (.getModel $listbox))
        key-log (get-in key-logs [$listbox])
        count-keys-entered ((key-log :count))
        current-selection (selection $listbox)]
    
    (if (= keyed-code (KeyEvent/VK_BACK_SPACE))
      (if (> count-keys-entered 0)
        ((key-log :pop!)))
      ((key-log :push!) keyed))
    
    (if (= keyed-code (KeyEvent/VK_ESCAPE))
        ((key-log :truncate!)))
    
    (when-let [search-on (first
                         (filter (fn [x] 
                                   (re-seq 
                                     (re-pattern 
                                       (str "(?i)^" 
                                            ((key-log :as-string))))
                                     (:name x)))
                                 listbox-model))]
      (if-not (= search-on current-selection)
        (do (selection! $listbox search-on)
          (scroll! $listbox :to [:row (.getSelectedIndex $listbox)]))))))

(def projects-lb-key-listener 
  (listen projects-lb :key-pressed
        on-lb-key-up))

(def go-to-project-lb-key-listener
  (listen (select projects-frame [:#go-to-projects-lb]) :key-pressed
          on-lb-key-up))

(def go-to-project-lb-selection-listener
  (listen (select projects-frame [:#go-to-projects-lb]) :selection
          (fn [e]
            (if-let [selected (selection e)]
              (if-not (.getValueIsAdjusting (to-widget e))
                (print "Selection updated"))))))

(listen (select projects-frame [:#go-to-projects-lb]) :mouse-clicked
        (fn [e]
          (let [key-log (get-in key-logs [(to-widget e)])]
            ((key-log :truncate!)))))

(listen members-lb :selection
  (fn [e]
    (if-not (.getValueIsAdjusting (to-widget e))
      (handle-event on-project-member-selected))))

(def members-lb-key-listener
  (listen members-lb :key-pressed
          on-lb-key-up))

(defn options-ok-btn-action [e]
  (future
    (let [project-id (:id (selection projects-lb))
          assignments (mapv #(:id (selection %))
                           (select options-frame [:.member-combo]))
          tree-xml (ws-tree/next-tree-text-to-xml-parser
                     (get-selected-text area))
          tree-zipper (zip/xml-zip tree-xml)
          tree-zipper-w-assignees (ws-tree/merge-assignees-transformer
                                    tree-zipper
                                    assignments)
          visit-result (:node
                         (ws-tree/tree-visitor
                           tree-zipper
                           assignments
                           [ws-tree/add-assignee-visitor
                           (partial ws-tree/redmine-ticket-creator-visitor
                                    (fn [id subject description assignee-id parent-id]
                                      (invoke-later
                                        (config! options-frame
                                                 :title
                                                 (str "Assign - Creating "
                                                       subject)))
                                      (let [http-resp (web-api/create-issue
                                                        id
                                                        subject
                                                        description
                                                        project-id
                                                        assignee-id
                                                        parent-id)]
                                        ; PUT request doesn't return anything
                                        ; so the id should be returned directly
                                        ; and the parent-id should be reused from
                                        ; whatever was passed in to create-issue
                                        (if (not (empty? id))
                                          {:id id
                                           :parent-id parent-id}
                                          {:id (get-in
                                                 http-resp
                                                 [:body :issue :id])
                                           :parent-id (get-in
                                                        http-resp
                                                        [:body :issue :parent_issue_id])}))))]))
          prepend-id-visitor-result-str (ws-tree/prepend-rm-id-to-source
                                          (zip/xml-zip visit-result)
                                          (get-selected-text area))
          selection-text-range (selection area)
          sub1 (.substring (config area :text) 0 (first selection-text-range))
          sub2 (.substring (config area :text) (second selection-text-range)) 
          ]
      (println prepend-id-visitor-result-str)
      (insert-text prepend-id-visitor-result-str
                   (str sub1 sub2)
                   (first selection-text-range)
                   area))
    
    (let [projects-key-log (get-in key-logs [projects-lb])
          members-key-log (get-in key-logs [members-lb])]
      ((projects-key-log :truncate!))
      ((members-key-log :truncate!)))
    
    (-> options-frame hide!)))

(listen options-ok-btn :action options-ok-btn-action)

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
        highlight-painter (new DefaultHighlighter$DefaultHighlightPainter
                               highlight-color)
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

(defn -main  [& args]
  (load-or-create-note-file)
  (set-area-doc-listener)
  (unbind-ctrl+d)

  ;; TODO test this! on linux and mac
  (.. area (discardAllEdits))

  ; setting :border on the button doesn't work :(
  (config! (select red-jem-frame [:#close-search]) :border nil)

  (if (web-api/token?)
    (web-api/load-token)
    (do
      (spit web-api/obelisk-token-file-path "")
      (config-button-handler nil))))

