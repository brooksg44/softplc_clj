(ns softplc-clj.plc-runtime
  (:require [softplc-clj.data-table :as dt]
            [softplc-clj.compiler :as compiler]
            [softplc-clj.config :as config]
            [clojure.core.async :as async]))

;; PLC runtime state
(defonce runtime-state 
  (atom {:running false
         :scan-count 0
         :last-scan-time 0
         :scan-times []  ; Track the last 100 scan times for statistics
         :avg-scan-time 0
         :min-scan-time Long/MAX_VALUE
         :max-scan-time 0
         :scan-rate 500  ; ms (default)
         :logic-stack []
         :stack-top false}))

;; Channel for controlling the PLC scan
(defonce scan-control-ch (async/chan))

;; Resets the runtime state
(defn reset-runtime!
  "Reset the PLC runtime state"
  []
  (swap! runtime-state assoc
         :running false
         :scan-count 0
         :last-scan-time 0
         :scan-times []
         :avg-scan-time 0
         :min-scan-time Long/MAX_VALUE
         :max-scan-time 0
         :scan-rate (config/get-config :scan-rate)
         :logic-stack []
         :stack-top false))

;; Executes a single instruction
(defn- execute-instruction
  "Execute a single instruction"
  [state {:keys [instruction address]}]
  (case instruction
    "STR" (-> state
              (update :logic-stack conj (:stack-top state))
              (assoc :stack-top (dt/get-bool address)))
    
    "AND" (assoc state :stack-top (and (:stack-top state) 
                                        (dt/get-bool address)))
    
    "OR" (assoc state :stack-top (or (:stack-top state) 
                                      (dt/get-bool address)))
    
    "NOT" (assoc state :stack-top (not (dt/get-bool address)))
    
    "ANDNOT" (assoc state :stack-top (and (:stack-top state) 
                                           (not (dt/get-bool address))))
    
    "ORNOT" (assoc state :stack-top (or (:stack-top state) 
                                         (not (dt/get-bool address))))
    
    "ANDSTR" (let [stack (:logic-stack state)
                   stack-top (:stack-top state)
                   popped-value (peek stack)
                   new-stack (pop stack)]
               (assoc state
                      :stack-top (and stack-top popped-value)
                      :logic-stack new-stack))
    
    "ORSTR" (let [stack (:logic-stack state)
                  stack-top (:stack-top state)
                  popped-value (peek stack)
                  new-stack (pop stack)]
              (assoc state
                     :stack-top (or stack-top popped-value)
                     :logic-stack new-stack))
    
    "OUT" (do (dt/set-bool! address (:stack-top state))
              state)
    
    "SET" (if (:stack-top state)
            (do (dt/set-bool! address true)
                state)
            state)
    
    "RST" (if (:stack-top state)
            (do (dt/set-bool! address false)
                state)
            state)
    
    "END" state  ; End of program
    
    ;; Default case
    (do (println "Warning: Unsupported instruction" instruction)
        state)))

;; Execute a network of instructions
(defn- execute-network
  "Execute a complete network of instructions"
  [network]
  (let [initial-state {:logic-stack [false]
                       :stack-top false}
        instructions (:instructions network)]
    (reduce execute-instruction initial-state instructions)))

;; Execute a full PLC scan
(defn- execute-scan
  "Execute one complete PLC scan"
  []
  (let [compiled-program (compiler/get-compiled-program)
        start-time (System/nanoTime)]
    
    ;; Execute each network in the compiled program
    (doseq [network compiled-program]
      (execute-network network))
    
    ;; Calculate scan statistics
    (let [end-time (System/nanoTime)
          scan-time (/ (- end-time start-time) 1000000.0) ; Convert to ms
          current-scan-times (take 99 (:scan-times @runtime-state))
          new-scan-times (conj current-scan-times scan-time)
          avg-scan-time (if (empty? new-scan-times) 
                          0 
                          (/ (reduce + new-scan-times) (count new-scan-times)))]
      
      ;; Update runtime state with statistics
      (swap! runtime-state 
             (fn [state]
               (-> state
                   (update :scan-count inc)
                   (assoc :last-scan-time scan-time)
                   (assoc :scan-times new-scan-times)
                   (assoc :avg-scan-time avg-scan-time)
                   (assoc :min-scan-time (min (:min-scan-time state) scan-time))
                   (assoc :max-scan-time (max (:max-scan-time state) scan-time))))))))

;; Start the PLC scan loop
(defn start-scanner!
  "Start the PLC scanner process"
  []
  (let [scan-rate (:scan-rate @runtime-state)
        running? (atom true)]
    
    (swap! runtime-state assoc :running true)
    
    (async/go-loop []
      (let [timeout-ch (async/timeout scan-rate)
            [val ch] (async/alts! [scan-control-ch timeout-ch])]
        
        ;; Check if we should stop
        (if (and (= ch scan-control-ch) (= val :stop))
          (swap! runtime-state assoc :running false)
          
          ;; Otherwise execute a scan and continue
          (do
            (when (compiler/program-valid?)
              (execute-scan))
            (recur)))))))

;; Stop the PLC scanner
(defn stop-scanner!
  "Stop the PLC scanner process"
  []
  (async/put! scan-control-ch :stop))

;; Get current PLC runtime stats
(defn get-stats
  "Get current PLC runtime statistics"
  []
  (select-keys @runtime-state 
               [:running :scan-count :last-scan-time 
                :avg-scan-time :min-scan-time :max-scan-time]))

;; Set the scan rate
(defn set-scan-rate!
  "Set the PLC scan rate in milliseconds"
  [rate-ms]
  (when (pos? rate-ms)
    (swap! runtime-state assoc :scan-rate rate-ms)
    (config/set-config! :scan-rate rate-ms)))

;; Load, compile and run a program
(defn load-compile-and-run!
  "Load, compile and run a PLC program"
  [filename]
  (stop-scanner!)
  (reset-runtime!)
  (when (compiler/load-and-compile! filename)
    (dt/init-test-data!)
    (start-scanner!)
    true))
