(ns softplc-clj.util
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [java.security MessageDigest]
           [java.util Date]
           [java.text SimpleDateFormat]))

;; Time utilities
(defn current-time-ms
  "Get current time in milliseconds"
  []
  (System/currentTimeMillis))

(defn timestamp
  "Format current timestamp as a string"
  []
  (.format (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss") (Date.)))

;; File utilities
(defn file-exists?
  "Check if a file exists"
  [filename]
  (let [file (io/file filename)]
    (.exists file)))

(defn resource-path
  "Get path to a resource"
  [path]
  (let [resource (io/resource path)]
    (when resource
      (.getPath resource))))

;; String utilities
(defn split-by-line
  "Split a string into lines, removing comments and empty lines"
  [text]
  (let [lines (str/split-lines text)]
    (filter #(and (not (str/blank? %))
                  (not (str/starts-with? (str/trim %) "#")))
            lines)))

;; Cryptographic utilities
(defn calc-signature
  "Calculate a signature (MD5 hash) for a string"
  [content]
  (let [md (MessageDigest/getInstance "MD5")
        digest (.digest md (.getBytes content))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) digest))))

(defn calc-file-signature
  "Calculate a signature (MD5 hash) for a file"
  [filename]
  (when (file-exists? filename)
    (calc-signature (slurp filename))))

;; Conversion utilities
(defn bool->int
  "Convert a boolean to an integer (1 for true, 0 for false)"
  [b]
  (if b 1 0))

(defn int->bool
  "Convert an integer to a boolean (0 is false, anything else is true)"
  [i]
  (not= i 0))

;; JSON utilities
(defn to-json
  "Convert a Clojure data structure to a JSON string"
  [data]
  (json/write-str data))

(defn from-json
  "Parse a JSON string to a Clojure data structure"
  [json-str]
  (json/read-str json-str :key-fn keyword))

;; Logging utilities
(defn log
  "Simple logging function"
  [& args]
  (let [log-msg (str (timestamp) " - " (str/join " " args))]
    (println log-msg)
    log-msg))

;; PLC address utilities
(defn address-pattern
  "Create a regex pattern for a certain PLC address type"
  [prefix]
  (re-pattern (str "^" prefix "(\\d+)$")))

(defn parse-address
  "Parse an address into type and number components"
  [address]
  (let [patterns {"X" #"^X(\d+)$"
                  "Y" #"^Y(\d+)$"
                  "C" #"^C(\d+)$"
                  "DS" #"^DS(\d+)$"
                  "DD" #"^DD(\d+)$"
                  "DF" #"^DF(\d+)$"
                  "TXT" #"^TXT(\d+)$"}
        matches (for [[type pattern] patterns
                      :let [match (re-matches pattern address)]
                      :when match]
                  [type (Integer/parseInt (second match))])]
    (when (seq matches)
      (let [[type num] (first matches)]
        {:type type :number num}))))

(defn generate-address
  "Generate an address from type and number"
  [type number]
  (str type number))

;; String formatting utilities
(defn pad-left
  "Pad a string on the left with a specified character to a given length"
  [s length pad-char]
  (let [padding-needed (max 0 (- length (count s)))
        padding (apply str (repeat padding-needed pad-char))]
    (str padding s)))

(defn pad-right
  "Pad a string on the right with a specified character to a given length"
  [s length pad-char]
  (let [padding-needed (max 0 (- length (count s)))
        padding (apply str (repeat padding-needed pad-char))]
    (str s padding)))

;; Collection utilities
(defn group-consecutive
  "Group consecutive numbers in a sequence"
  [coll]
  (when (seq coll)
    (let [sorted (sort coll)]
      (reduce (fn [result v]
                (let [current-group (peek result)]
                  (if (and (seq current-group)
                           (= (inc (last current-group)) v))
                    (conj (pop result) (conj current-group v))
                    (conj result [v]))))
              [[(first sorted)]]
              (rest sorted)))))
