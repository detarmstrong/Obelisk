(ns ^{:doc "A virtual tree is one rendered in text, tab or 2 space indented lines
are children of preceeding line."}
     red-jem.virtual-tree)

(declare line-depth)
(declare current-line-line-depth)

(defn make-tree-data-from-lines
  "Given input of text with leading tabs or spaces that indicate heirarchical depth 
that you want in a tree structure, output vector with sibling count
and tree depth information"
  [ws-formatted-lines]
  (loop [lines ws-formatted-lines
         result []]
    (when-let [line (first lines)]
      (if (children? lines)
        (recur (children lines)
               [(clojure.string/trim line)])
        (conj result (clojure.string/trim line))))))

(defn line-depth [line]
  "Count the number of whitespaces at beginning of line"
  (if-not (= line nil)
  (let [matches (re-find #"^\s*" line)]
    (count matches))))

(defn children [lines]
  "Return all sublines of the current line of document eg first encountered lines greater
 than first line depth"
  (let [[head & xs] lines
        baseline-depth (line-depth head)]
    (take-while #(> (line-depth %1) baseline-depth) xs)))

(defn children? [lines]
    (> 0 (count (children lines))))

