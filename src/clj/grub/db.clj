(ns grub.db
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def collection "grub-lists")

(defn clear-all [db]
  (mc/drop db collection))

(defn update-db! [db state]
  (mc/drop db collection)
  (mc/insert db collection state))

(defn get-current-state [db]
  (let [state (first (mc/find-maps db collection))]
    (when state
      (dissoc state :_id))))

(defn connect [db-name]
  (let [conn (m/connect)
        db (m/get-db conn db-name)]
    (println "Connected to mongo at localhost:" db-name)
    {:conn conn
     :db db}))

(defn disconnect [conn]
  (m/disconnect conn))
