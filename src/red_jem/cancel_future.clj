(ns red-jem.cancel-future
  "Just for testing"
  (:use seesaw.core))

(def status-label (label "Ready"))

(def response-text (text :multi-line? true
                         :editable? true
                         :font "MONOSPACED-14"))

(defn update-status-label [status-text]
  (text! status-label status-text))

(defn start-handler [e]
  (def future-request (future 
                        (try 
	                        (let [slurped (slurp "http://redmine.visiontree.com")]
	                          (invoke-later 
	                            (do 
	                              (update-status-label "done") 
	                              (text! response-text slurped))))
                         (catch java.net.ConnectException e (update-status-label (str "Error: " (.getMessage e)))))))
  (update-status-label "working"))

; !ACHTUNG this works even though the def is nested in the defn; it's of type Unbound
(print future-request)

(defn stop-handler [e]
  (if (and (future? future-request)
           (not (future-cancelled? future-request)))
    (do (future-cancel future-request) (update-status-label "Cancelled"))
    (text! status-label "No future or future cancelled.")))

(defn make-frame []
  (frame 
    :title "Cancel me"
    :on-close :hide
    :content (border-panel
               :north (horizontal-panel
                        :items [(action :handler start-handler :name "Start")
                                (action :handler stop-handler :name "Stop")])
               :center (vertical-panel
                         :items [(text "type here")
                                 response-text])
               :south status-label)))

(-> (make-frame) pack! show!)