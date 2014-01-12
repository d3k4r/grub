(ns grub.websocket
  (:require [grub.db :as db]
            [org.httpkit.server :as httpkit]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def incoming-events (chan))

(def connected-clients (atom {}))

(def ws-channel-id-count (atom 0))

(defn get-unique-ws-id []
  (swap! ws-channel-id-count inc))

(defn add-connected-client! [ws-channel]
  (let [ws-channel-id (get-unique-ws-id)
        client-chan (chan)]
    (swap! connected-clients #(assoc % ws-channel-id client-chan))
    [ws-channel-id client-chan]))

(defn remove-connected-client! [status ws-channel ws-channel-id client-chan]
  (println "Client disconnected:" 
           (.toString ws-channel)
           (str "(" ws-channel-id ")")
           "with status" status)
  (swap! connected-clients #(dissoc % ws-channel-id))
  (println (count @connected-clients) "client(s) still connected")
  (a/close! client-chan))

(defn send-current-grubs-and-recipes-to-client [client-chan]
  (let [add-grubs-event {:event :add-grub-list
                         :grubs (db/get-current-grubs)}
        add-recipes-event {:event :add-recipe-list
                           :recipes (db/get-current-recipes)}]
    (go (>! client-chan add-grubs-event)
        (>! client-chan add-recipes-event))))

(defn on-receive [raw-event ws-channel-id client-chan]
  (let [parsed-event (read-string raw-event)
        event (assoc parsed-event :ws-channel ws-channel-id)]
    (println "Received event" event)
    (if (= (:event event) :send-all-items)
      (send-current-grubs-and-recipes-to-client client-chan)
      (go (>! incoming-events event)))))

(defn forward-other-events-to-client [c ws-channel]
  (a/go-loop [] 
             (when-let [event (<! c)]
               (println "Send to client '" (str event) "'")
               (httpkit/send! ws-channel (str event))
               (recur))))

(defn set-up-new-connection [ws-channel]
  (let [[ws-channel-id client-chan] (add-connected-client! ws-channel)]
    (println "Client connected:" (.toString ws-channel) (str "(" ws-channel-id ")"))
    (println (count @connected-clients) "client(s) connected")
    (httpkit/on-close ws-channel #(remove-connected-client! % ws-channel ws-channel-id client-chan))
    (httpkit/on-receive ws-channel #(on-receive % ws-channel-id client-chan))
    (forward-other-events-to-client client-chan ws-channel)))

(defn websocket-handler [request]
  (when (:websocket? request)
    (httpkit/with-channel request channel (set-up-new-connection channel))))

(defn get-other-client-channels [my-ws-channel-id]
  (-> @connected-clients
       (dissoc my-ws-channel-id)
       (vals)))

(defn push-event-to-others [orig-event]
  (let [my-ws-channel-id (:ws-channel orig-event)
        event (dissoc orig-event :ws-channel)]
    (go (doseq [c (get-other-client-channels my-ws-channel-id)]
            (>! c event)))))

(defn pass-received-events-to-clients-and-db [db-chan]
  (let [in' (a/mult incoming-events)
        to-others (chan)
        to-database (chan)]
    (a/tap in' to-others)
    (a/tap in' to-database)
    (a/go-loop [] (let [event (<! to-others)]
                    (push-event-to-others event)
                    (recur)))
    (a/pipe to-database (a/map> #(dissoc % :ws-channel) db-chan))))
