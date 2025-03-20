(ns softplc-clj.config
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; PLC Constants similar to PLCConstants.py
(def default-config
  {:host "127.0.0.1"
   :port 1502
   :plc-program "simpleconveyor.txt"
   :scan-rate 500  ; milliseconds
   :resources-path "resources/"})

(defonce ^:private config-atom (atom default-config))

(defn get-config
  "Get the current configuration or a specific config value"
  ([] @config-atom)
  ([key] (get @config-atom key)))

(defn set-config!
  "Set a specific configuration value"
  [key value]
  (swap! config-atom assoc key value))

(defn load-config!
  "Load configuration from a file"
  [filename]
  (try
    (let [config-file (io/resource filename)
          new-config (edn/read-string (slurp config-file))]
      (reset! config-atom (merge default-config new-config))
      true)
    (catch Exception e
      (println "Error loading configuration:" (.getMessage e))
      false)))

(defn save-config!
  "Save current configuration to a file"
  [filename]
  (try
    (spit filename (pr-str @config-atom))
    true
    (catch Exception e
      (println "Error saving configuration:" (.getMessage e))
      false)))

;; Data table limits similar to MBAddrTypes.MaxExtAddrTypes
(def data-table-limits
  {:coil 9999
   :discrete 9999
   :holding-reg 9999
   :input-reg 9999
   :holding-reg32 9999
   :input-reg32 9999
   :holding-reg-float 9999
   :input-reg-float 9999})

;; Error messages
(def error-messages
  {:bad-file "Bad or missing soft logic config file:"
   :no-plc-type "Bad soft logic config - No PLC type specified."
   :no-plc-program-name "Bad soft logic config - No program name specified."
   :no-scan-rate "Bad soft logic config - Scan rate missing or invalid."
   :missing-addr-type "Bad soft logic config - address type missing in section:"
   :unsupported-addr-type "Bad soft logic config - unsupported address type in section:"
   :bad-mem-addr "Bad soft logic config - data table address out of range in section:"
   :missing-mem-addr "Bad soft logic config - data table address missing or non-numeric in section:"
   :missing-action "Bad soft logic config - bad action in section:"
   :unsupported-action "Bad soft logic config - unsupported action in section:"
   :missing-logic-table "Soft logic config - logic table spec missing in section:"
   :no-update-interval "Soft logic config - no data table save update interval specified. Default value used instead."
   :missing-mem-save-table "Soft logic config - no data table save addresses specified."
   :bad-str-len "Bad soft logic config - Bad or missing string length."})
