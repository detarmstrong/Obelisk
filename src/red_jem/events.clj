; Handle events and flow here
(in-ns 'red-jem.core)

(def initial-state "inactive")
(def current-state initial-state)
(def current-project-id "nothing")
(def memoized-project-members (memo web-api/project-members))

(defn handle-event [event-fn]
  (event-fn current-state))


;Action transition functions
(defn on-create-ticket-form-visible [widget]
  ; only state is inactive
  ; show picker frame
  (-> options-frame show!) ; not calling pack! because it negates size setting

  (request-focus! widget)
  
  ; disable save button
  (config! options-ok-btn :enabled? false)
  
  ; load projects in project picker  
  (config! projects-lb :model (projects-listbox-model))
  
  ; clear members in member picker
  (config! members-lb :model []))

(defn on-project-selected [event]
  (if-let [selected-project-id (:id (selection projects-lb))]
    (let [project-members-response (memoized-project-members selected-project-id)
          project-members-as-map (map get-project-member project-members-response)
          project-members-as-map (sort-by :name project-members-as-map)
          selected-text (trimr (get-selected-text area))
;OK TODO show description lines too in right pane. no dropdown on those lines (see mockup)
;OK TODO enable ok  button on selection of project
;TODO hook up the options-ok-btn with saving the tickets
;TODO give feedback that saving is in process (process includes fail or success paths)
  ; REDO Mockup: simply prepend animated random rm # then replace with the real number?
  ; spinner in lower left? 
          lines (clojure.string/split-lines selected-text)
          ;lines (filter #(empty? (re-matches #"\s*//.*$" %)) lines)
          member-items (conj (mapv (fn [_] (if (empty? (re-matches #"\s*//.*$" _))
                                             (combobox :model project-members-as-map
                                                         :renderer list-renderer
                                                         :size [200 :by 22]
                                                         :class :member-combo)
                                             [200 :by 22]))
                                   lines)
                             :fill-v)
          lines-items (conj (mapv (fn [line-text] (text :text line-text
                                                        :multi-line? true
                                                        :tab-size 2
                                                        :editable? false
                                                        :size [600 :by 22]
                                                        :border (seesaw.border/empty-border))) lines)
                            :fill-v)]
        (config! (select options-frame [:#members-panel])
                 :items member-items)
        (config! (select options-frame [:#lines-panel])
                 :items lines-items)
        (config! options-ok-btn :enabled? true))
    (print "no project selection")))

(defn on-project-member-selected [event]
  ; enable save button
  (config! options-ok-btn :enabled? true))