(ns red-jem.state-machine)

(defn find-first
  "Returns the first item of coll for which (pred item) returns logical true.
  Consumes sequences up to the first match, will consume the entire sequence
  and return nil if no match is found."
  [pred coll]
  (first (filter pred coll)))

(defn state-machine [transition-table initial-state]
  (ref initial-state :meta transition-table))

(defn- switch-state? [conds]
  (if (empty? conds)
    true
    (not (some false? (reduce #(conj %1 (if (fn? %2) (%2) %2)) [] conds)))))

(defn- first-valid-transition [ts]
  (find-first #(= (second %) true)
              (map #(let [{conds :conditions 
                           transition :transition
                           on-success :on-success} %]
                      [transition (switch-state? conds) on-success]) ts)))

(defn update-state [state]
  (let [transition-list ((meta state) @state)
        [transition _ on-success] (first-valid-transition transition-list)]
    (if-not (nil? transition)
      (do 
        (if-not (nil? on-success)
          (on-success))
        (dosync (ref-set state transition))))))

(defmacro until-state [s c & body] 
  `(while (not= (deref ~s) ~c) 
     ~@body
     (update-state ~s)))


;========== red jem (obelisk) ===========

;; TODO fill in the conditions for transitioning into the next state

; to transition from inactive to project-selection is based on event of 
; keyboard shortcut to initiate process to create a ticket
(def create-ticket-transition-table
  {:inactive [{:conditions [] 
               :transition :project-selection}]
   :project-selection [{:conditions [] 
                        :transition :member-selection}]
   :member-selection [{:conditions [] 
                       :transition :api-post}]
   :api-post [{:conditions [] 
               :transition :inactive}]})

(def create-ticket-sm 
  (state-machine create-ticket-transition-table :inactive))

(update-state create-ticket-sm)

; ========== Example 1 ==============
(def traffic-light
  {:green [{:conditions [] :transition :yellow}]
   :yellow  [{:conditions [] :transition :red}]
   :red [{:conditions [] :transition :green}]})

(let [sm (state-machine traffic-light :green)] 
  (dotimes [_ 8]
    (println @sm)
    (update-state sm)))

; ============ Example 2 ===============
(defn pop-char [char-seq]
  (dosync (ref-set char-seq (rest @char-seq))))

(defn find-lisp [char-seq]
  (let [start-trans {:conditions []
                     :on-success #(pop-char char-seq)
                     :transition :start}
        found-l-trans {:conditions [#(= (first @char-seq) \l)] 
                       :on-success #(pop-char char-seq)
                       :transition :found-l}]

    {:start [found-l-trans
             start-trans]

     :found-l [found-l-trans
               {:conditions [#(= (first @char-seq) \i)] 
                :on-success #(pop-char char-seq)
                :transition :found-i}
               start-trans]

     :found-i [found-l-trans
               {:conditions [#(= (first @char-seq) \s)] 
                :on-success #(pop-char char-seq)
                :transition :found-s}
               start-trans]

     :found-s [found-l-trans
               {:conditions [#(= (first @char-seq) \p)] 
                :on-success #(do (println "Found Lisp")
                                 (pop-char char-seq))
                :transition :start}
               start-trans]}))

(let [char-seq (ref "ablislasllllispsslis")
      sm (state-machine (find-lisp char-seq) :start)] 
  (dotimes [_ (count @char-seq)]
    (update-state sm)))