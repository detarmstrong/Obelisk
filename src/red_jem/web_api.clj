(ns red-jem.web-api
  "Wrap the api calls - no guarding of params here"
  (:require [clj-http.client :as client])
  (:require [clojure.java.io :as io])
  (:use [cheshire.core :only (generate-string)])
  (:use [clojure.string :only (trim)]))

(def obelisk-token-file-path
  ".obelisk_rm_token")

(defn token? []
  (-> (io/file obelisk-token-file-path) (.isFile)))

(defn load-token []
  (def api-token 
    (trim (slurp obelisk-token-file-path))))

(defn project-members [project-id]
  (get-in 
    (client/get 
      (format "http://redmine.visiontree.com/projects/%s/memberships.json"
        project-id) 
      {:basic-auth [api-token "d"]
       :as :json})
    [:body :memberships]))

(defn projects []
  (sort-by :name
	  (get-in 
	    (client/get
	      "http://redmine.visiontree.com/projects.json" 
	      {:basic-auth [api-token "d"]
	       :as :json
	       :query-params {:limit 300}})
	    [:body :projects])))

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
     :as :json
     :throw-entire-message? true}))

(defn valid-token? []
  "Make a request using the token provided, expect 200"
  (let [response (client/get "http://redmine.visiontree.com/users/current.json" 
                                  {:basic-auth [api-token "d"]
                                   :as :json
                                   :throw-exceptions false})]
    (= 200 (:status response))))
