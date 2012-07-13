(ns red-jem.web-api
  "Wrap the api calls - no guarding of params here"
  (:require [clj-http.client :as client])
  (:use [cheshire.core :only (generate-string)])
  (:use [clojure.string :only (trim)]))

(def api-token 
  "e647bfc4397bc3f8010521b36c72d182477d11ee")

(defn project-members [project-id]
  (get-in 
    (client/get 
      (format "http://redmine.visiontree.com/projects/%s/memberships.json"
        project-id) 
      {:basic-auth [api-token "d"]
       :as :json})
    [:body :memberships]))

(defn projects []
  (get-in 
    (client/get
      "http://redmine.visiontree.com/projects.json" 
      {:basic-auth [api-token "d"]
       :as :json
       :query-params {:limit 300}})
    [:body :projects]))

(defn issue [id]
  (client/get 
    (format "http://redmine.visiontree.com/issues/%d.json" 
      (#(Integer/parseInt %) (trim id))) 
    {:as :json
     :basic-auth [api-token "random"]
     :query-params {:include "children"}}))

(defn create-issue [subject body project-id member-id parent-issue-id]
  (println (generate-string {:issue {:subject subject
                                     :description body
                                     :project_id project-id
                                     :assigned_to_id member-id
                                     :parent_issue_id parent-issue-id}}))
  (client/post "http://redmine.visiontree.com/issues.json"
    {:basic-auth [api-token "random"]
     :body (generate-string {:issue {:subject subject 
                                     :description body
                                     :project_id project-id
                                     :assigned_to_id member-id
                                     :parent_issue_id parent-issue-id}})
     :content-type :json
     :socket-timeout 8000
     :conn-timeout 8000
     :accept :json
     :throw-entire-message? true}))
