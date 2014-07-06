(ns whitespace-tree.core
  (:require [clojure.zip :as zip]
            [clojure.string :as string]
            [clojure.data.xml :as xml]))

(declare find-by-subject)

;===== stackoverflow answer tree parser ====
(defn tree-text-parser [text]
  {(re-find #"(?m)^.+" text)
   (map tree-text-parser
        (map #(string/replace % #"(?m)^\s{2}" "")
             (map first (re-seq #"(?m)(^\s{2}.+(\n\s{4}.+$)*)" text))))})

(defn simple-tree-text-to-xml-parser [text]
  (xml/element :task {:subject (re-find #"(?m)^.+" text)}
     (map simple-tree-text-to-xml-parser
            (map #(string/replace % #"(?m)^\s{2}" "")
                 (map
                   first
                   (re-seq #"(?m)(^\s{2}[^\n]+(\n\s{4}.+$)*)" text))))))

(defn simple-tree-text-to-xml-parser2 [text]
  {:tag :task
   :attrs {:subject (re-find #"(?m)^.+" text)}
   :content (let [new-tree (mapv simple-tree-text-to-xml-parser2
              (map #(string/replace % #"(?m)^\s{2}" "")
                   (map
                     first
                     (re-seq #"(?m)(^\s{2}[^\n]+(\n\s{4}.+$)*)" text))))]
              (if (= new-tree [])
                nil
                new-tree))})

(def description-lines-pattern #"^\s*//.*$")
(def subject-pattern #"\d*\s*([\s\S]+)$")
(def leading-id-pattern #"\s*(\d+)\s+")

(defn look-ahead-for-subject-and-description-and-id [text]
  "Given a whitespacetree, start from first line
  and identify subject (line that does not start with s*//)
  and description lines (one or more lines that start with s*//)"
  (let [lines (string/split text #"(?m)\n")
        subject (second (re-find subject-pattern (first lines)))
        id (second (re-find leading-id-pattern (first lines)))
        description-lines (take-while #(re-find description-lines-pattern %)
                                      (rest lines))
        formatted-description-lines (map
                                      #(string/replace % #"^\s*//\s*" "")
                                      description-lines)
        formatted-description (string/join "\n" formatted-description-lines)]
    {:subject subject
     :description formatted-description
     :id id}))

(defn remove-description [text]
  "format the text so the next description line(s)
  isn't misinterpreted as a subject"
  (let [lines (string/split text #"(?m)\n")]
    (string/join "\n" (cons (first lines)
                            (drop-while #(re-find description-lines-pattern %)
                                        (rest lines))))))

(defn format-description-lines [text]
  "Normalize all description lines (lines matching description-lines-pattern)
   so that they are indented one width from non-description line above.
   This is to satisfy the tree-text-to-xml parser so
   that description lines don't have to be indented in the source text, they can
   be on the same indent level or less than the subject"
  (string/join "\n" (reverse
               (:lines-accum
                  (reduce (fn [state line]
                            (let [lines (:lines-accum state)
                                  stripped-line (string/trim line)
                                 prev-line (:prev-line state)
                                 prev-line-indent-width (/ (count (re-find #"^\s*" prev-line)) 2)
                                 current-line line
                                 current-indent-width (/ (count (re-find #"^\s*" current-line)) 2)
                                 prev-line-description-line? (> (count (re-find #"\s*//" prev-line)) 1)
                                 current-line-description-line? (> (count
                                                                     (re-find #"\s*//" current-line)) 1 )]

                              (if (and (not prev-line-description-line?)
                                       current-line-description-line?)
                                ;adjust current line to be one indent greater than previous line
                                {:prev-line current-line
                                 :lines-accum (conj lines
                                                (apply str (reverse
                                                             (conj
                                                               (repeat
                                                                 (+ prev-line-indent-width 1) "  ")
                                                               stripped-line ))))}
                                {:prev-line current-line
                                 :lines-accum (conj lines line)})))
                          {:prev-line "" :lines-accum '()}
                          (string/split text #"\n"))))))

(defn prepend-rm-id-to-source [result-zipper source-text]
  "Find line in source-text by finding it in result-zipper. If found
  prepend id to the line"

  (let [source-text-coll (string/split-lines source-text)
        idified (map #(let [detach-whitespace (re-matches #"(\s*)(.+)" %1)
                            leading-whitespace (nth detach-whitespace 1)
                            trimmed-text (nth detach-whitespace 2)
                            matched-node (find-by-subject result-zipper
                                                          (clojure.string/trim %1))
                            id (:id (:attrs matched-node))]
                        (str leading-whitespace
                             id
                             " "
                             trimmed-text))
                     source-text-coll)]
    (string/join "\n" idified)))

(defn next-tree-text-to-xml-parser
  "This should be called whitespace-tree/parse or parse-to-xml"
  ([text]
    (next-tree-text-to-xml-parser
      ((comp format-description-lines
             #(string/replace %1
                              #"(?m)^\t+"
                              (fn [matched]
                                (apply str (repeat (count matched) "  ")))))
        text)
      true))

  ([text pre-formatted?]
    (let [subject-and-description (look-ahead-for-subject-and-description-and-id text)
          subject (:subject subject-and-description)
          description (let [desc (:description subject-and-description)]
                        (if (= desc "")
                          nil
                          desc))
          id (:id subject-and-description)
          new-text (remove-description text)]
      {:tag :task
       :attrs {:subject subject :description description :id id}
       :content (let [new-tree (mapv #(next-tree-text-to-xml-parser % true)
                        (map #(string/replace % #"(?m)^\s{2}" "")
                             (map
                               first
                               (re-seq #"(?m)(^\s{2}[^\n]+(\n\s{4}.+$)*)" new-text))))]
                  (if (= new-tree [])
                    nil
                    new-tree))})))

(defn my-make-node [existing-node children]
  (cons (first existing-node) children))

(defn my-zipper [tree]
  (zip/zipper coll? clojure.core/next my-make-node tree))

;; grokbase tree visitor
(defn print-tree [loc]
  (when-not (zip/end? loc)
    ;; when-not wraps body in implicit do
    (println
      (str (string/join "" (repeat (count (zip/path loc)) " "))
           (if (zip/branch? loc)
             (first (zip/node loc))
             (zip/node loc))))
    (recur (zip/next loc))))

;; puredanger's tree visitor
(defn visit-node
  [start-node start-state path visitors]
  (loop [node start-node
         state start-state
         [first-visitor & rest-visitors] visitors]
    (let [context (merge {:node node, :state state, :stop false, :next false}
                         (first-visitor node state path))
          {new-node :node
           new-state :state
           ;; :keys is a destructuring directive to specify keys that you would like
           ;; as locals with the same name
           :keys (stop next)} context]
      (if (or next stop (nil? rest-visitors))
        {:node new-node, :state new-state, :stop stop}
        (recur new-node new-state rest-visitors)))))

(defn tree-visitor
  ([zipper visitors]
   (tree-visitor zipper nil visitors))
  ([zipper initial-state visitors]
   (loop [loc zipper
          state initial-state]
     (let [context (visit-node (zip/node loc) state (zip/path loc) visitors)
           new-node (:node context)
           new-state (:state context)
           stop (:stop context)
           new-loc (if (= new-node (zip/node loc))
                     loc
                     (zip/replace loc new-node))
           next-loc (zip/next new-loc)]
       (if (or (zip/end? next-loc) (= stop true))
         {:node (zip/root new-loc) :state new-state}
         (recur next-loc new-state))))))

(defn subject-visitor [node state path]
  (when (string? (:subject (:attrs node)))
    {:state (conj state {:subject (:subject (:attrs node))
                         :path (zip/path node)})}))

(defn subject-collector [node]
  (:state (tree-visitor node [] [subject-visitor])))

(defn attribute-visitor [attribute node state path]
  (when (string? (:subject (:attrs node)))
    {:state (conj state {attribute (attribute (:attrs node))})}))

(defn attribute-collector [node attribute]
  (:state (tree-visitor node [] [(partial attribute-visitor attribute)])))

(defn matched [subject node state path]
  (when (= subject (:subject (:attrs node)))
    {:stop true
     :state node}))

(defn find-by-subject [node subject]
  "Finds first node by subject text and returns it"
  (:state
    (tree-visitor node [(partial matched subject)])))

(defn parent-visitor [node state path]
  (when (string? (:subject (:attrs node)))
    {:state (conj state {:subject (:subject (:attrs node))
                         :parent (:subject (:attrs (last path)))})}))

(defn leaf-and-parent-collector [node]
  (:state (tree-visitor node [] [parent-visitor])))

(defn redmine-ticket-creator-visitor [creation-service node state path]
  (if (and (contains? (:attrs node) :id) (number? (:id (:attrs node))))
    {:state (conj state {:subject (:subject (:attrs node))
                         :parent (:subject (:attrs (last path)))})}
    ; create ticket and add id to node
    (let [parent-id (get-in (first path) [:attrs :id])]
      {:node (assoc node :attrs (merge (:attrs node) 
                                       (creation-service
                                         (:subject (:attrs node))
                                         (:description (:attrs node))
                                         ;:project-id already known in creation-service fn
                                         (:assignee-id (:attrs node))
                                         parent-id)))})))

(defn redmine-ticket-tree-transformer [node creation-service]
  "Run creation-service func on each node"
  (tree-visitor node [] [(partial redmine-ticket-creator-visitor creation-service)]))

(defn add-assignee-visitor [node state path]
  ;mutate the node
  (let [assignee-id (first state)
        new-state (rest state)]
    {:node (assoc-in node [:attrs :assignee-id] assignee-id)
     :state new-state}))

(defn merge-assignees-transformer [node assignments]
  "Given a list of assignees parallel to assignments, merge into
   the tree"
  (:node (tree-visitor node assignments [add-assignee-visitor])))

