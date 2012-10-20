(ns ^{:doc "A virtual tree is one rendered in text, tab or 2 space indented lines
are children of preceeding line."}
     red-jem.virtual-tree)

(declare line-depth)

(defn make-tree-data-from-lines
  "Given input of text with leading tabs or spaces that indicate heirarchical depth 
that you want in a tree structure, output vector with sibling count
and tree depth information"
  [ws-formatted-lines]
  (loop [lines ws-formatted-lines
         result []]
    (when-let [line (first lines)]
      (for [c (children lines)]
        (recur c [])))))

(defn make-tree-root [root children]
  (conj root (make-tree children)))

(defn make-tree [children]
  (doseq [child children]
    (if (parent? child)
      (doseq [child2 (children child)]
        (make-tree child2))
      (reduce ))
    
(let [parent-nodes-w-indexes (keep-indexed #(> x y ) children)
      leaf-nodes-w-indexes (keep-indexed #(= x y) children)
      result []]
  (doseq [parent parent-nodes-w-indexes]
    (make-tree 
      

; chicken and eggs

(defn children [lines]
  "Return all sublines of the current line of document eg first encountered lines greater
 than first line depth"
  (let [[head & xs] lines
        baseline-depth (line-depth head)]
    (take-while #(> (line-depth %1) baseline-depth) xs)))

(defn siblings [lines]
  "Skip children of siblings found. Keep indexes of siblings"
  (let [[head & xs] lines
        baseline-depth (line-depth head)]
    (println "siblings of " head)
    (keep-indexed #(if (= (line-depth %2) baseline-depth)
                     %2)
                  (take-while #(>= (line-depth %1) baseline-depth) lines))))
 
(defn children? [lines]
    (> (count (children lines)) 0))

(defn line-depth [line]
  "Count the number of whitespaces at beginning of line"
  (if-not (= line nil)
    (let [matches (re-find #"^\s*" line)]
      (count matches))))

