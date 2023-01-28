(ns app.sci
  (:require
   ["@codemirror/view" :as view]
   [app.error :refer [error-handler]]
   [app.problem-restrictions :as problem-restrictions]
   [applied-science.js-interop :as j]
   [clojure.string]
   [goog.string]
   [goog.string.format]
   [nextjournal.clojure-mode.extensions.eval-region :as eval-region]
   [sci.core :as sci]
   [sci.impl.evaluator]))

(defonce context
  (sci/init {:classes {'js goog/global
                       :allow :all}
             :namespaces {'clojure.core {'format goog.string/format}}}))

(defn eval-string [source]
  (try (sci/eval-string* context source)
       (catch :default e
         (with-out-str (error-handler source e)))))

(defn join-human [xs]
  (if (= 1 (count xs))
    (first xs)
    (str (clojure.string/join ", " (butlast xs))
         " and "
         (last xs))))
#_(join-human ['a 'b 'c])

(defn restricted-symbol-msg [restricted-symbols]
  (str (if (= 1 (count restricted-symbols))
         (str (first restricted-symbols) " is")
         (str (join-human restricted-symbols) " are"))
       " not allowed for this problem"))

(defn eval-string-w-restricted [restricted-symbols source]
  (js/console.log (clj->js (problem-restrictions/find-restricted-symbols
                            restricted-symbols
                            source)))
  (if-let [restricted-symbols (problem-restrictions/find-restricted-symbols
                               restricted-symbols
                               source)]
    (restricted-symbol-msg restricted-symbols)
    (eval-string source)))

(j/defn eval-at-cursor [on-result ^:js {:keys [state]}]
  (some->> (eval-region/cursor-node-string state)
           (eval-string)
           (on-result))
  true)

(j/defn eval-top-level [on-result ^:js {:keys [state]}]
  (some->> (eval-region/top-level-string state)
           (eval-string)
           (on-result))
  true)

(j/defn eval-cell [on-result ^:js {:keys [state]}]
  (-> (str "(do " (.-doc state) " )")
      (eval-string)
      (on-result))
  true)

(defn keymap* [modifier]
  {:eval-cell
   [{:key "Mod-Enter"
     :doc "Evaluate cell"}]
   :eval-at-cursor
   [{:key (str modifier "-Enter")
     :doc "Evaluates form at cursor"}]
   :eval-top-level
   [{:key (str modifier "-Shift-Enter")
     :doc "Evaluates top-level form at cursor"}]})

(defn extension [{:keys [modifier
                         on-result]}]
  (.of view/keymap
       (j/lit
        [{:key "Mod-Enter"
          :run (partial eval-cell on-result)}
         {:key (str modifier "-Enter")
          :shift (partial eval-top-level on-result)
          :run (partial eval-at-cursor on-result)}])))
