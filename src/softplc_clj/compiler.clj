(ns softplc-clj.compiler
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [softplc-clj.config :as config]
            [softplc-clj.data-table :as dt]))

;; Instruction definitions (similar to DLCkInstructions.InstrDefList)
(def instruction-defs
  {"STR" {:type :bool, :action :start-branch}
   "AND" {:type :bool, :action :and}
   "OR" {:type :bool, :action :or}
   "NOT" {:type :bool, :action :not}
   "ANDNOT" {:type :bool, :action :and-not}
   "ORNOT" {:type :bool, :action :or-not}
   "OUT" {:type :bool, :action :output}
   "SET" {:type :bool, :action :set}
   "RST" {:type :bool, :action :reset}
   "ANDSTR" {:type :bool, :action :and-branch}
   "ORSTR" {:type :bool, :action :or-branch}
   "END" {:type :control, :action :end}
   "NOP" {:type :control, :action :nop}
   "JMP" {:type :control, :action :jump}
   "LBL" {:type :control, :action :label}
   "NETWORK" {:type :control, :action :network}})

;; Compiler state
(defonce compiler-state (atom {:program []
                               :compiled-program []
                               :errors []
                               :program-loaded false
                               :compile-time nil
                               :valid false}))

(defn reset-compiler!
  "Reset compiler state"
  []
  (reset! compiler-state {:program []
                          :compiled-program []
                          :errors []
                          :program-loaded false
                          :compile-time nil
                          :valid false}))

(defn- add-error!
  "Add an error message to the compiler state"
  [line message]
  (swap! compiler-state update :errors conj
         {:line line :message message}))

(defn- valid-instruction?
  "Check if instruction is valid"
  [instr]
  (contains? instruction-defs instr))

(defn- validate-address
  "Validate if an address argument is valid"
  [instr addr line]
  (let [instr-def (get instruction-defs instr)
        expected-type (:type instr-def)
        validation (dt/validate-address addr)]
    (cond
      (not (:valid validation))
      (do (add-error! line (str "Invalid address format: " addr))
          false)

      (and (= expected-type :bool) (not= (:type validation) :bool))
      (do (add-error! line (str "Expected boolean address for " instr ", got: " addr))
          false)

      (and (= expected-type :word) (not= (:type validation) :word))
      (do (add-error! line (str "Expected word address for " instr ", got: " addr))
          false)

      :else true)))

(defn- parse-instruction-line
  "Parse an instruction line into instruction and address"
  [line text]
  (let [parts (str/split (str/trim text) #"\s+")
        instr (first parts)
        addr (second parts)]
    (cond
      (nil? instr)
      nil

      (not (valid-instruction? instr))
      (do (add-error! line (str "Unknown instruction: " instr))
          nil)

      (and (not (#{"END" "NOP" "NETWORK"} instr)) (nil? addr))
      (do (add-error! line (str "Missing address for instruction: " instr))
          nil)

      (and (not (#{"END" "NOP" "NETWORK"} instr))
           (not (validate-address instr addr line)))
      nil

      :else
      {:instruction instr
       :address (when addr addr)
       :line line})))

(defn- parse-program
  "Parse a program into a sequence of instructions"
  [program-text]
  (let [lines (str/split-lines program-text)]
    (keep-indexed
     (fn [idx line]
       (let [trimmed (str/trim line)]
         (when-not (or (str/blank? trimmed) (str/starts-with? trimmed "#"))
           (parse-instruction-line (inc idx) trimmed))))
     lines)))

(defn- compile-instructions
  "Convert parsed instructions to executable code"
  [parsed-instructions]
  (let [network-groups (partition-by #(= (:instruction %) "NETWORK") parsed-instructions)
        networks (filter #(not= (:instruction (first %)) "NETWORK") network-groups)]

    (map-indexed
     (fn [network-idx network]
       {:network (inc network-idx)
        :instructions (vec network)
        :matrix-data (generate-matrix-data network)})
     networks)))

(defn- generate-matrix-data
  "Generate matrix representation for ladder visualization"
  [instructions]
  (loop [remaining instructions
         row 0
         col 0
         result []]
    (if (empty? remaining)
      result
      (let [instr (first remaining)
            {:keys [instruction address]} instr
            instr-def (get instruction-defs instruction)
            instr-type (:type instr-def)
            instr-action (:action instr-def)
            element-type (cond
                           (= instruction "STR") "noc"
                           (= instruction "ANDSTR") "branchttr"
                           (= instruction "ORSTR") "branchr"
                           (= instruction "AND") "noc"
                           (= instruction "OR") "branchttl"
                           (= instruction "OUT") "out"
                           (= instruction "END") "end"
                           :else "hbar")

            ;; Determine next position based on instruction
            [next-row next-col] (cond
                                  (= instruction "STR") [(inc row) 0]
                                  (= instruction "ANDSTR") [row (inc col)]
                                  (= instruction "ORSTR") [(inc row) col]
                                  (= instruction "OUT") [row (inc col)]
                                  (= instruction "END") [row col]
                                  :else [row (inc col)])

            ;; Create matrix element
            element {:type instr-type
                     :row row
                     :col col
                     :addr address
                     :value element-type
                     :monitor false}]

        (recur (rest remaining)
               next-row
               next-col
               (conj result element))))))

(defn load-program!
  "Load a PLC program from a file"
  [filename]
  (try
    (let [file (io/resource (str "programs/" filename))
          program-text (slurp file)]
      (swap! compiler-state assoc
             :program (str/split-lines program-text)
             :program-loaded true
             :compile-time (System/currentTimeMillis))
      true)
    (catch Exception e
      (swap! compiler-state assoc
             :program-loaded false
             :errors [{:line 0 :message (str "Failed to load program: " (.getMessage e))}])
      false)))

(defn compile-program!
  "Compile the loaded program"
  []
  (reset! compiler-state (assoc @compiler-state :errors []))

  (let [program-text (str/join "\n" (:program @compiler-state))
        parsed (parse-program program-text)]

    (if (empty? (:errors @compiler-state))
      (let [compiled (compile-instructions parsed)]
        (swap! compiler-state assoc
               :compiled-program compiled
               :valid true)
        true)

      (do (swap! compiler-state assoc :valid false)
          false))))

(defn get-compiled-program
  "Get the compiled program"
  []
  (:compiled-program @compiler-state))

(defn get-errors
  "Get any compiler errors"
  []
  (:errors @compiler-state))

(defn program-valid?
  "Check if the compiled program is valid"
  []
  (:valid @compiler-state))

;; Convert compiled program to JSON for ladder display
(defn compiled-to-json
  "Convert compiled program to JSON format for ladder display"
  []
  (let [compiled (:compiled-program @compiler-state)
        routines {"Main" {:subrdata compiled}}]
    routines))

;; Load and compile a program
(defn load-and-compile!
  "Load and compile a program"
  [filename]
  (when (load-program! filename)
    (compile-program!)))
