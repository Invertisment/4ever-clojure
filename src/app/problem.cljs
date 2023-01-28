(ns app.problem
  (:require
   [app.data :as data]
   [app.editor :as editor]
   [app.editor-settings :as editor-settings]
   [app.modal :as modal]
   [app.sci]
   [app.state :as state :refer [db]]
   [clojure.string :as strs]
   [reagent.core :as r]))

(def user-data (r/cursor db [:solutions]))

(defn get-problem [id]
  (first
   (filter #(= (:id %) id) data/problems)))

(defn next-problem [id]
  (some #(when (> (:id %) id) %) data/problems))

(defn check-solution [problem user-solution]
  (let [replaced (mapv #(strs/replace % "__" user-solution) (:tests problem))
        results  (mapv (partial app.sci/eval-string-w-restricted (:restricted problem)) replaced)
        passed   (count (filter true? results))
        failed   (count (filter (comp not true?) results))]
    (swap! user-data assoc (:id problem) {:code   user-solution
                                          :passed passed
                                          :failed failed})
    (if (or (> failed 0) (> passed 0))
      results
      (throw (ex-info "Evaluation error" {:stacktrace (first results)})))))

(def results-style {:width "100%"
                    :display "flex"
                    :font-family "monospace"
                    :justify-content "space-between"})

(defn render-result-item [test maybe-result]
  [:li
   [:pre
    [:code {:style results-style}
     [:span {:style {:overflow-x "hidden"}} test]
     (when maybe-result maybe-result)]]])

(defn result-list [items]
  [:ul {:style {:list-style "none" :padding 0}}
   items])

(defn test-list-section [tests]
  [result-list
   (doall
    (for [[i test] (map-indexed vector tests)]
      ^{:key i}
      [render-result-item test]))])

(defn result-info-item [color text]
  [:span {:style {:color color
                  :align-self "center"}}
   text])

(defn test-results-section [results tests]
  [result-list
   (let [test-attempts (map vector tests results)]
     (for [[i [test passed?]] (map-indexed vector test-attempts)]
       ^{:key i}
       [render-result-item
        test
        (if (true? passed?)
          [result-info-item "green" "🟢 pass"]
          [result-info-item "red"   "🔴 uh-oh"])]))])

(defn restricted-alert [problem]
  [:p
   {:style {:color "#FF0000",
            :border-color "darkred",
            :border-style "dashed",
            :padding "10px"}} "Special Restrictions : "
   (strs/join ", " (:restricted problem))])

(defn user-code-section [id problem solution]
  (r/with-let [code (r/atom (if-let [code (:code solution "")]
                              ;; can sometimes be {:code nil}
                              code ""))
               !editor-view (r/atom nil)
               editor-extension-mode (r/atom
                                      (:extension-mode
                                       @editor-settings/editor-settings))
               get-editor-value #(some-> @!editor-view .-state .-doc str)
               results (r/atom '())
               success-modal-is-open (r/atom false)
               success-modal-on-close #(reset! success-modal-is-open false)
               settings-modal-is-open (r/atom false)
               settings-modal-on-close #(reset! settings-modal-is-open false)
               solution-attempted (r/atom false)
               error-stacktrace (r/atom nil)
               tests (:tests problem)]
    (let [next-prob (next-problem id)
          on-run (fn []
                   (try
                     (let [editor-value (get-editor-value)
                           _ (reset! code editor-value)
                           attempts (check-solution problem editor-value)]
                       (when attempts
                         (reset! results attempts)
                         (reset! solution-attempted true)
                         (when (every? true? attempts)
                           (reset! success-modal-is-open true))))
                     (catch ExceptionInfo e
                       (reset! error-stacktrace (-> e ex-data :stacktrace)))))]
      [:div
       (if @solution-attempted
         [test-results-section @results tests]
         [test-list-section tests])
       (when (:restricted problem)
         [restricted-alert problem])
       [:p "Write code which will fill in the above blanks:"]

       ;; Force resetting editor state when input source code changed
       ;; e.g., when manually trigger run
       ^{:key [@code @editor-extension-mode]}
       [editor/editor @code !editor-view
        {:eval? true
         :extension-mode @editor-extension-mode}]
       [:div {:style {:display "flex"
                      :justify-content "space-between"}}
        [:button {:on-click on-run
                  :style {:margin-top "1rem"}}
         "Run"]
        [:button {:on-click #(reset! settings-modal-is-open true)
                  :style {:margin-top "1rem"}}
         "Settings"]]
       [modal/box {:is-open settings-modal-is-open
                   :on-close settings-modal-on-close}
        [editor-settings/modal
         (fn [{:keys [extension-mode] :as _editor-settings}]
           (reset! code (get-editor-value))
           (reset! editor-extension-mode extension-mode))]]
       [:p {:style {:margin-top "1rem"}}
        [:small
         "Alt+Enter will eval the local form in the editor box above. There are
          lots of nifty such features and keybindings. More docs coming soon! (Try
          playing with alt + arrows / ctrl + enter) in the meanwhile."]]
       [modal/box {:is-open success-modal-is-open
                   :on-close success-modal-on-close}
        ^{:key "congrats-title"}
        [:h4 (str "Congratulations on solving problem " "#" id "!")]
        ^{:key "congrats-content"}
        [:div
         [:p {:on-click #(reset! success-modal-is-open false)}
          "Next problem "
          [:a {:href (state/href :problem/item {:id (:id next-prob)})}
           (str "#" (:id next-prob) " " (:title next-prob))]]]]])))

(defn view [_]
  (fn [{:keys [path-params] :as _props}]
    (let [id (js/parseInt (:id path-params))
          solution (get @user-data id)
          {:keys [title description difficulty] :as problem} (get-problem id)]
      [:div
       [:h3 "Problem " id ", " title]
       [:div {:style {:margin-top "0.5rem" :margin-bottom "2rem"}}
        [:b "Difficulty: "] difficulty]
       [:p description]
       ^{:key (str "problem-" id)}
       [user-code-section id problem solution]
       [:hr]
       [:p
        "Want to see how others have solved this? "
        [:a {:href (state/href :solution/list {:id id})}
         "View problem #" id " solutions archive"]
        " No cheating please! :)"]])))
