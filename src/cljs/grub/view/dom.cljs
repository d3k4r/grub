(ns grub.view.dom
  (:require [dommy.core :as dommy]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :refer [go]]))

(defn listen
  ([el type] (listen el type nil))
  ([el type f] (listen el type f (chan)))
  ([el type f out]
     (let [push-fn (fn [e] (when f (f e)) (go (>! out e)))
           unlisten #(do (dommy/unlisten! el type push-fn)
                         (a/close! out))]
         (dommy/listen! el type push-fn)
         {:chan out :unlisten unlisten})))

(defn listen-once
  ([el type] (listen el type nil))
  ([el type f] (listen el type f (chan)))
  ([el type f out]
     (let [push-fn (fn [e] (when f (f e)) (go (>! out e)))
           unlisten #(do (dommy/unlisten! el type push-fn)
                         (a/close! out))]
         (dommy/listen-once! el type push-fn)
         {:chan out :unlisten unlisten})))

(defn get-away-clicks [elem]
  (let [{c :chan unlisten :unlisten} (listen (sel1 :body) :click)
        filtered-chan (a/filter< #(not (dommy/descendant? (.-target %) elem)) c)]
    {:unlisten unlisten :chan filtered-chan}))

(def add-grub-text 
  (node [:input.form-control {:id "add-grub-input" :type "text" :placeholder "2 grubs"}]))

(def add-grub-btn 
  (node [:button.btn.btn-primary {:id "add-grub-btn" :type "button"} "Add"]))

(def clear-all-btn
  (node [:button.btn.hidden.pull-right 
         {:id "clear-all-btn" :type "button"}
         "Clear all"]))

(defn clear-grubs! []
  (dommy/set-html! (sel1 :#grub-list) ""))

(defn get-grub-completed-glyph [completed]
  (node (if completed
    [:span.glyphicon.glyphicon-check]
    [:span.glyphicon.glyphicon-unchecked])))

(defn make-grub-node [id grub completed]
  (node [:li.list-group-item.grub-item 
         {:id id
          :class (when completed "completed")} 
         [:span.grub-static
          (get-grub-completed-glyph completed)
          [:span.grub-text grub]]
         [:input.grub-input {:type "text" :value grub}]]))

(defn grubs-selector []
  [(sel1 :#grub-list) :.grub-item])

(defn make-recipe-node 
  ([id name grubs] (make-recipe-node id name grubs false))
  ([id name grubs new-recipe]
    (node [:div.panel.panel-default.recipe-panel
           {:id id}
           [:div.panel-heading.recipe-header
            [:input.form-control.recipe-header-input 
             {:id "recipe-name"
              :type "text" 
              :placeholder "Grub pie"
              :value name}]]
           [:div.panel-body.recipe-grubs.hidden
            [:textarea.form-control.recipe-grubs-input
             {:id "recipe-grubs"
              :rows 3 
              :placeholder "2 grubs"}
             grubs]
            (when-not new-recipe 
              [:button.btn.btn-primary.pull-left.recipe-btn.recipe-add-grubs-btn
             {:type "button"} "Add Grubs"])
            [:button.btn.btn-primary.hidden.pull-right.recipe-btn.recipe-done-btn
             {:type "button"} "Done"]]])))

(def new-recipe (make-recipe-node "new-recipe" "" "" true))

(defn recipes-selector []
  [(sel1 :#recipe-list) :.recipe-panel])

(defn recipe-done-btns-selector []
  [(sel1 :body) :.recipe-done-btn])

(defn recipe-add-grubs-btns-selector []
  [(sel1 :body) :.recipe-add-grubs-btn])

(deftemplate main-template []
  [:div.container
   [:div.row
    [:div.col-sm-6.leftmost-column
     [:h3 "Grub List"]
     [:div.input-group 
      add-grub-text
      [:span.input-group-btn
       add-grub-btn]]
     [:ul#grub-list.list-group]
     clear-all-btn]
    [:div.col-sm-6
     [:h3.recipes-title "Recipes"]
     new-recipe
     [:ul#recipe-list.list-group.recipe-list]]]])

(defn render-body []
  (dommy/prepend! (sel1 :body) (main-template)))

(defn get-add-grub-text []
  (dommy/value add-grub-text))

(defn clear-add-grub-text []
  (dommy/set-value! add-grub-text ""))


(defprotocol IHideable
  (-hide! [this])
  (-show! [this]))

(defprotocol IGrub
  (-activate! [this])
  (-deactivate! [this])

  (-complete! [this])
  (-uncomplete! [this])
  (-completed? [this])

  (-set-editing! [this])
  (-unset-editing! [this])
  (-editing? [this])
  (-update-grub! [this grub]))

(defprotocol IRecipe
  (-expand! [this])
  (-unexpand! [this])
  
  (-update-recipe! [this])
  
  (-get-name [this])
  (-get-grubs-str [this])
  (-get-grubs [this]))

(defprotocol IClearable
  (-clear! [this]))

(extend-type js/HTMLElement
  IHideable
  (-hide! [this]
    (dommy/add-class! this :hidden))
  (-show! [this]
    (dommy/remove-class! this :hidden)))


(extend-type js/HTMLElement
  IGrub
  (-activate! [this]
    (dommy/add-class! this :grub-active))
  (-deactivate! [this]
    (dommy/remove-class! this :grub-active))

  (-complete! [this]
    (dommy/add-class! this :completed)
    (dommy/replace! (sel1 this ".glyphicon") 
                     (get-grub-completed-glyph true)))
  (-uncomplete! [this]
    (dommy/remove-class! this :completed)
    (dommy/replace! (sel1 this ".glyphicon") 
                     (get-grub-completed-glyph false)))
  (-completed? [this]
    (dommy/has-class? this :completed))

  (-set-editing! [this]
    (-deactivate! this)
    (dommy/add-class! this :edit)
    (.focus (sel1 this :input)))
  (-unset-editing! [this]
    (dommy/remove-class! this :edit))
  (-editing? [this]
    (dommy/has-class? this :edit)))

(defrecord Grub [elem id grub completed]
  dommy.template/PElement
  (-elem [this] elem)

  IGrub
  (-set-editing! [this] (-set-editing! elem))
  (-unset-editing! [this] (-unset-editing! elem))
  (-editing? [this] (-editing? elem))

  (-complete! [this] (-complete! elem))
  (-uncomplete! [this] (-uncomplete! elem))
  (-completed? [this] (-completed? elem))
  
  (-set-editing! [this] (-set-editing! elem))
  (-unset-editing! [this] (-unset-editing! elem))
  (-editing? [this] (-editing? elem))
  
  (-update-grub! [this grub]
    (dommy/set-text! (sel1 elem ".grub-text") grub)
    (dommy/set-value! (sel1 elem ".grub-input") grub)))

(defn add-new-grub [id grub completed]
  (let [node (make-grub-node id grub completed)
        grub (Grub. node id grub completed)
        grub-list (sel1 :#grub-list)]
    (dommy/append! grub-list grub)
    (dommy/set-value! (sel1 :#add-grub-input) "")
    grub))

(extend-type js/HTMLDivElement
  IRecipe
  (-expand! [this]
    (dommy/remove-class! (sel1 this ".recipe-grubs") :hidden)
    (dommy/remove-class! (sel1 this ".recipe-done-btn") :hidden))
  (-unexpand! [this]
    (dommy/add-class! (sel1 this ".recipe-grubs") :hidden)
    (dommy/add-class! (sel1 this ".recipe-done-btn") :hidden))

  (-get-name [this]
    (dommy/value (sel1 this :#recipe-name)))
  (-get-grubs-str [this]
    (dommy/value (sel1 this :#recipe-grubs)))
  (-get-grubs [this]
    (let [split-grubs (clojure.string/split-lines (-get-grubs-str this))]
      (when split-grubs (into [] split-grubs)))))


(extend-type js/HTMLDivElement
  IClearable
  (-clear! [this]
    (dommy/set-value! (sel1 this "#recipe-name") "")
    (dommy/set-value! (sel1 this "#recipe-grubs") "")))

(defrecord Recipe [elem id name grubs]
  dommy.template/PElement
  (-elem [this] elem)

  IRecipe
  (-expand! [this] (-expand! elem))
  (-unexpand! [this] (-unexpand! elem))

  (-clear! [this] (-clear! elem))

  (-update-recipe! [this]
    (dommy/set-value! (sel1 this :#recipe-name) name)
    (dommy/set-text! (sel1 this :#recipe-grubs) grubs)))

(defn add-new-recipe [id name grubs]
  (let [node (make-recipe-node id name grubs)
        recipe (Recipe. node id name grubs)
        recipe-list (sel1 :#recipe-list)]
    (dommy/append! recipe-list recipe)
    recipe))
    