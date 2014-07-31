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

 ; (request-focus! widget)
  
  ; disable save button
  (config! options-ok-btn :enabled? false)
  
  ; load projects in project picker  
  (config! projects-lb :model (projects-listbox-model))
  
  ; reset state of assignees and lines
  (config! (select options-frame [:#members-panel])
                 :items [])
  (config! (select options-frame [:#lines-panel])
                   :items [])
  (config! members-lb :model [])
  (-> options-frame show!)) ; not calling pack! because it negates size setting

(defn on-project-selected [event]
  (if-let [selected-project-id (:id (selection projects-lb))]
    (let [project-members-response (memoized-project-members selected-project-id)
          project-members-as-map (map get-project-member project-members-response)
          project-members-as-map (sort-by :name project-members-as-map)
          selected-text (trimr (get-selected-text area))
          lines (clojure.string/split-lines selected-text)
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