; Handle events and flow here
(in-ns 'red-jem.core)

; TODO gather all SM state and put it here
; store current listbox selections here or references to them or just call globally?
(def initial-state "inactive")
(def current-state initial-state)
(def current-project-id "nothing")
(def memoized-project-members (memo web-api/project-members))

(defn handle-event [event-fn]
  ; for the current state, call transition to next state
  ; TODO use action-transition-functions map
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
  (config! members-lb :model [])
  
  ; next state is project selection
  )

(defn on-project-selected [event]
  (if-let [selected-project-id (:id (selection projects-lb))]
    (let [project-members-response (memoized-project-members selected-project-id)
          project-members-as-map (map get-project-member project-members-response)
          project-members-as-map (sort-by :name project-members-as-map)]
      (config! members-lb :model project-members-as-map))
    (print "no project selection")))

(defn on-project-member-selected [event]
  ; enable save button
  (config! options-ok-btn :enabled? true))

(def action-transition-functions
  {:inactive {:on-create-ticket on-create-ticket-form-visible}
   :project-selection {:on-project-selected on-project-selected}})