(ns whitespace-tree.t-core
  (:use midje.sweet)
  (:use [whitespace-tree.core])
  (:require [clojure.zip :as zip]
            [clojure.string :as string]
            [clojure.data.xml :as xml]
            [clojure.xml :as xml2]))

(defn make-sequence [init-val]
  "make a stateful sequence starting at init-val. returns the next number"
  (let [c (atom init-val)]
    #(reset! c (+ 1 @c))))

(defn mock-redmine-post-ticket
  [the-seq id subject description assignee-id parent-id]
  "mock rm ws api for POST of ticket"
  (if (number? id)
    {:id id
     :parent-id parent-id}
    {:id (the-seq)
     :parent-id parent-id}))

(def seq2 (make-sequence 0))

(defn mock-redmine-post-ticket2 [subject description parent-id]
  "mock rm ws api for POST of ticket"
  {:id (seq2) :subject subject :parent-id parent-id})

(defn sample-text []
  (slurp "resources/sample-raw-text"))

(defn sample-text-next []
  (slurp "resources/sample-raw-text-2.0"))

(defn sample-text-next-xml []
  (xml2/parse "resources/sample-raw-text-2.0.xml"))

(facts "about SO tree-text-parser"
  (let [tree-text (sample-text)]
    (tree-text-parser tree-text)
    => '{"hierarchy"
        ({"child 1" ()}
         {"child 2"
          ({"child 2.1" ()})}
         {"child 3"
          ({"child 3.1" ()})})}))

(facts "about puredanger's visitors: collectors, finders"
       ; note that we use xml2 parse here because parse defined
       ; in clojure.data.xml returns strucutre not compatible with xml-zip
       (let [sample-text (sample-text)
             generated-xml (xml/emit-str (simple-tree-text-to-xml-parser sample-text))
             ws-tree-input-stream (java.io.ByteArrayInputStream. (.getBytes generated-xml))
             tree (zip/xml-zip (xml2/parse ws-tree-input-stream))]
           (fact "subject-finder returns first node matching subject"
                  (get-in (find-by-subject tree "child 2")
                          [:attrs :subject])
                 => "child 2")

           (fact "subject-collector returns coll of maps"
                 (reduce #(and %1 (map? %2)) true (subject-collector tree))
                 => truthy)

           (fact "the 3rd node found by leaf-and-parent-collector has
                  parent 'child 2'"
                 (nth (leaf-and-parent-collector tree) 3)
                 => {:parent "child 2", :subject "child 2.1"})

           (fact "redmine-ticket-tree-transformer sets nil id to 1"
                 (let [id-seq (make-sequence 0)
                       result-tree (redmine-ticket-tree-transformer
                                     tree
                                     (partial mock-redmine-post-ticket id-seq))
                       merge-assignee-result-tree (merge-assignees-transformer
                                                    (zip/xml-zip (:node result-tree))
                                                    [4 5 6 7 8 9])]
                   (attribute-collector (zip/xml-zip merge-assignee-result-tree) :id)
                   =>
                   [{:id 1} {:id 2} {:id 3} {:id 4} {:id 5} {:id 6}]))))

(facts "about inserting created redmine ids back into source whitespace-tree"
         (let [source-text (slurp "resources/sample-raw-text")
               result-tree (zip/xml-zip
                             (xml2/parse
                               "resources/sample-raw-text-w-ids.xml"))
               assignee-result (merge-assignees-transformer result-tree
                                                     [11 12 13 14 15 16])]
           
           (fact "input whitespace tree first line will be prepended by 1"
                 (prepend-rm-id-to-source  (zip/xml-zip assignee-result) source-text)
                 => "1 hierarchy\n  2 child 1\n  3 child 2\n    4 child 2.1\n  5 child 3\n    6 child 3.1")))

(facts "about mock-redmine-post-ticket"
       (fact "mock-redmine-post-ticket will return the next val in sequence."
             (map #(:id %1)
                  (repeatedly 6 #(mock-redmine-post-ticket2 "sub" "desc" 1)))
             => '(1 2 3 4 5 6))
       (fact "mock-redmine-post-ticket will return the id passed in"
             (mock-redmine-post-ticket (make-sequence 99) 32 "x" "y" 8 9)
             => {:id 32 :parent-id 9}))

(facts "about simple-tree-text-to-xml-parser"
       ; multiroot text selection is "root1\nroot2\n  some child"
       ; in the above root1 and root2 are really separate trees
       ;(fact "multiroot tree texts are treated as separated trees per 'root'"
       ;      false => truthy)

       (fact "input text yields specific xml"
             (let [sample-text (sample-text)
                   generated-xml (simple-tree-text-to-xml-parser sample-text)]
               ;(clojure.pprint/pprint (xml/emit-str generated-xml))
               (xml/emit-str generated-xml)
               =>
               (xml/emit-str (xml/element :task {:subject "hierarchy"}
                                          (xml/element :task {:subject "child 1"})
                                          (xml/element :task {:subject "child 2"}
                                                       (xml/element :task {:subject "child 2.1"}))
                                          (xml/element :task {:subject "child 3"}
                                                       (xml/element :task {:subject "child 3.1"})))))))


(facts "about next-tree-text-to-xml-parser"
       (fact "look-ahead-for-subject-and-description-and-id returns subject and description"
             (let [sample-text (sample-text-next)]
               (look-ahead-for-subject-and-description-and-id sample-text))
             =>
             {:subject "Ticket tree"
              :description "testing this syntax where description lines are\nstarted with tabs"
              :id "12"})

       (fact "format-description-lines: initial call of next-tree-text-to-xml-parser
              will cause all lines beginning with // to be indented
              one width greater than line above it."
             (let [sample-text (sample-text-next)]
               (format-description-lines sample-text) => "12 Ticket tree
  // testing this syntax where description lines are
\t// started with tabs
\t7832 Already ticketed - improve performance on the ssd
    //(whitespace after the // is optional) It can't be too fast!
  rewrite the whole thing
    // Indented comment for line above.
    // Second line for comment must not be dedented from comment line above. Rewrite will be profitable and fun \"he said\"
    rewrite subpart 1
  another subtask
    another sub subtask
  "))
       ; multiroot text selection is "root1\nroot2\n  some child"
       ; in the above root1 and root2 are really separate trees
       ;(fact "multiroot tree texts are treated as separate trees per 'root'"
           ;  false => truthy)
       (let [expected-tree-zipper (zip/xml-zip (sample-text-next-xml))
             sample-text (sample-text-next)
             under-test-zipper (zip/xml-zip (next-tree-text-to-xml-parser sample-text))]
         (fact "subject lines are parsed into subject xml attribute"
               (attribute-collector under-test-zipper :subject)
               =>
               (mapv #(let[subj-map %
                           subj-value (:subject subj-map)
                           fix-escaping-subj (string/replace
                                 (or (:subject subj-map) "")
                                 #"\\n"
                                 "\n")]
                        (assoc subj-map
                               :subject
                               (if (= "" fix-escaping-subj)
                                 nil
                                 fix-escaping-subj)))
                   (attribute-collector expected-tree-zipper :subject)))

         (fact "id is parsed out of subject lines"
               (attribute-collector under-test-zipper :id)
               =>
               (attribute-collector expected-tree-zipper :id))

         (fact "description lines are parsed into description xml attribute"
                 (attribute-collector under-test-zipper :description)
                 =>
                 (mapv #(let[desc-map %
                             desc-value (:description desc-map)
                             fix-escaping-desc (string/replace
                                   (or (:description desc-map) "")
                                   #"\\n"
                                   "\n")]
                          (assoc desc-map
                                 :description
                                 (if (= "" fix-escaping-desc)
                                   nil
                                   fix-escaping-desc)))
                     (attribute-collector expected-tree-zipper :description)))))

(facts "about merge-assignees-transformer"
       (fact "merge-assignees-transformertakes [assignee-id ...] (parallel vector to subjects) and walks tree"
             (let [expected-tree-zipper (zip/xml-zip
                                          (xml2/parse "resources/add-assignee-transformer-pre-filled.xml"))]
               (merge-assignees-transformer
                 expected-tree-zipper
                 [90 90 66 32])
               =>
                 {:tag :task
                  :attrs {:subject "test 1" :assignee-id 90}
                  :content [{:tag :task
                             :attrs {:subject "test 2" :assignee-id 90}
                             :content nil}
                            {:tag :task
                             :attrs {:subject "test 3" :assignee-id 66}
                             :content nil}]})))

(facts "about multiple chained visitors - first add-assignees then make rm tickets"
  (let [expected-tree-zipper (zip/xml-zip
                            (xml2/parse "resources/add-assignee-transformer-pre-filled.xml"))
        id-seq (make-sequence 0)
        visit-result (:node (tree-visitor
                                     expected-tree-zipper
                                     [90 90 66]
                                     [add-assignee-visitor
                                      (partial redmine-ticket-creator-visitor
                                               (partial mock-redmine-post-ticket id-seq))]))
        prepend-id-result (prepend-rm-id-to-source (zip/xml-zip visit-result)
                                                   (slurp "resources/add-assignee-transformer-pre-filled.txt"))]
    (fact "each node contains an assignee and was given an id"
          [(attribute-collector (zip/xml-zip visit-result) :id)
           (attribute-collector (zip/xml-zip visit-result) :assignee-id)
           (attribute-collector (zip/xml-zip visit-result) :parent-id)]
          => [[{:id 1} {:id 2} {:id 3}]
              [{:assignee-id 90} {:assignee-id 90} {:assignee-id 66}]
              [{:parent-id nil} {:parent-id 1} {:parent-id 1}]])

    (fact "result text has ids prepended"
          prepend-id-result => "1 test 1\n  2 test 2\n  3 test 3")))

(facts "about collect-ticket-ids"
       (fact "it collects ticket ids if found"
             (collect-ticket-ids "1 this\n  2 that\n3oh ho")
             =>
             '(1 2 3))
       (fact "it returns empty list if no ticket ids found"
             (collect-ticket-ids "nothing 99 to see")
             =>
             '()))

