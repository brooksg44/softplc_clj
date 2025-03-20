(ns softplc-clj.data-table
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; Boolean data table for X, Y, C variables (similar to DLCkDataTable.BoolDataTable)
(defonce bool-data-table (atom {}))

;; Word data table for DS, DD, DF variables (similar to DLCkDataTable.WordDataTable)
(defonce word-data-table (atom {}))

;; Data type validation patterns
(def data-patterns
  {:bool #"^([XYC])(\d+)$"
   :word #"^(DS|XD|YD)(\d+)$"
   :double-word #"^(DD)(\d+)$"
   :float #"^(DF)(\d+)$"
   :string #"^(TXT)(\d+)$"})

(defn reset-data-tables!
  "Reset data tables to empty state"
  []
  (reset! bool-data-table {})
  (reset! word-data-table {}))

(defn- parse-address
  "Parse a PLC address and return its type and number"
  [address]
  (let [patterns (vals data-patterns)
        matches (map #(re-matches % address) patterns)
        valid-match (first (filter identity matches))]
    (when valid-match
      {:type (second valid-match)
       :number (Integer/parseInt (nth valid-match 2))})))

(defn valid-bool-address?
  "Check if an address is a valid boolean address"
  [address]
  (boolean (re-matches (:bool data-patterns) address)))

(defn valid-word-address?
  "Check if an address is a valid word address (includes doubles, floats, strings)"
  [address]
  (boolean 
   (or (re-matches (:word data-patterns) address)
       (re-matches (:double-word data-patterns) address)
       (re-matches (:float data-patterns) address)
       (re-matches (:string data-patterns) address))))

(defn get-address-type
  "Get the type prefix of an address (X, Y, C, DS, DD, etc.)"
  [address]
  (when-let [parsed (parse-address address)]
    (:type parsed)))

(defn- create-sequential-addresses
  "Create a sequence of addresses given a base address and count"
  [base-address count]
  (when-let [{:keys [type number]} (parse-address base-address)]
    (for [i (range count)]
      (str type (+ number i)))))

;; Boolean operations
(defn get-bool
  "Get a boolean value from the data table"
  [address]
  (get @bool-data-table address false))

(defn set-bool!
  "Set a boolean value in the data table"
  [address value]
  (swap! bool-data-table assoc address (boolean value)))

(defn set-bools!
  "Set multiple boolean values in the data table"
  [address-map]
  (swap! bool-data-table merge 
         (into {} (map (fn [[k v]] [k (boolean v)]) address-map))))

;; Word operations
(defn get-word
  "Get a word value from the data table"
  [address]
  (get @word-data-table address 0))

(defn set-word!
  "Set a word value in the data table"
  [address value]
  (swap! word-data-table assoc address value))

(defn set-words!
  "Set multiple word values in the data table"
  [address-map]
  (swap! word-data-table merge address-map))

;; Data table persistence
(defn save-data-table!
  "Save data tables to a file"
  [filename]
  (try
    (let [data {:bool @bool-data-table
                :word @word-data-table}]
      (spit filename (pr-str data))
      true)
    (catch Exception e
      (println "Error saving data table:" (.getMessage e))
      false)))

(defn load-data-table!
  "Load data tables from a file"
  [filename]
  (try
    (let [file (io/file filename)]
      (when (.exists file)
        (let [data (edn/read-string (slurp file))]
          (reset! bool-data-table (:bool data))
          (reset! word-data-table (:word data))
          true)))
    (catch Exception e
      (println "Error loading data table:" (.getMessage e))
      false)))

;; Data table validation
(defn validate-address
  "Validate if an address is properly formatted and within range"
  [address]
  (cond
    (valid-bool-address? address)
    {:valid true :type :bool}
    
    (valid-word-address? address)
    {:valid true :type :word}
    
    :else
    {:valid false :error (str "Invalid address format: " address)}))

;; Initialize some default values for testing
(defn init-test-data!
  "Initialize test data"
  []
  (set-bool! "X1" false)
  (set-bool! "X2" false)
  (set-bool! "X3" false)
  (set-bool! "Y1" false)
  (set-word! "DS1" 0))
