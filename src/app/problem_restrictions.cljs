(ns app.problem-restrictions
  (:require
   clojure.edn
   clojure.set
   clojure.walk))

(defn find-restricted-symbols [problem user-input]
  (let [restricted-symbols (set (map symbol (:restricted problem)))]
    (->> user-input
         clojure.edn/read-string
         vector
         (clojure.walk/prewalk (fn [form]
                                 (cond (map? form) (concat (keys form) (vals form))
                                       (set? form) (seq form)
                                       :else form)))
         flatten
         set
         (clojure.set/intersection restricted-symbols)
         seq)))

#_(find-restricted-symbols {:restricted ["flatten" "into"]} "(flatten (into [] [[1]]))")
#_(find-restricted-symbols {:restricted ["flatten" "into"]} "#{:my-set-item flatten}")
#_(find-restricted-symbols {:restricted ["flatten" "into"]} "{}")
#_(find-restricted-symbols {:restricted ["flatten" "into"]} "{flatten []}")
#_(find-restricted-symbols {:restricted ["flatten" "into"]} "{flatten {:my-map-key into} }")
#_(find-restricted-symbols {:restricted ["flatten" "into"]} "{flatten {:my-map-key #{[into]}} }")
#_(find-restricted-symbols {:restricted ["flatten" "into"]} "flatten")
#_(find-restricted-symbols {:restricted ["max" "max-key"]} "[max \"max-key\"]")

