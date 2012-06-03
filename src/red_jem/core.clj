(ns red-jem.core
  (:use seesaw.core)
  (:use seesaw.keymap)
  (:use cheshire.core)
  (:use [clojure.string :only (trim join)])
  (:require [clj-http.client :as client]))

(native!)

(def red-jem-frame
  (frame
    :title "Obelisk"
    :id :red-jem
    :on-close :hide))

(def area (text :multi-line? true
                :text "Obelisk for redmine"
                :size [300 :by 100]))

(defn get-selected-text [text-widget]
  (let [rang (selection text-widget)]
    (subs (config text-widget :text) (first rang) (second rang))))

(defn issue [id]
  (client/get 
    (format "http://demo.redmine.org/issues/%d.json" 
      (#(Integer/parseInt %) (trim id))) {:as :json}))

;(listen area :key-released
;  (fn [e]
;    (if-let [selectio (selection e)]
;      (println (format "selection is '%s'" selectio)))))

(defn insert [piece string at-pos]
  (apply str (map clojure.string/join (let [v (split-at at-pos string)]
    [(first v) piece (second v)]))))

(defn insert-rm-subject [widge]
  (if-let [selectio (selection widge)]
    (text! widge (insert (issue (get-selected-text widge)) (config widge :text) (second selectio)))))


(map-key area "control R"
  (fn [e]
    (text! e 
      (-> (issue (get-selected-text e))
        (get-in [:body :issue :subject])))))

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
