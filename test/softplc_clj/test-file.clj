(ns softplc-clj.core-test
  (:require [clojure.test :refer :all]
            [softplc-clj.compiler :as compiler]
            [softplc-clj.data-table :as dt]
            [softplc-clj.plc-runtime :as runtime]
            [clojure.java.io :as io]))

(defn test-setup []
  (dt/reset-data-tables!)
  (compiler/reset-compiler!)
  (runtime/reset-runtime!))

(deftest test-data-table
  (testing "Boolean data table operations"
    (dt/set-bool! "X1" true)
    (is (true? (dt/get-bool "X1")))
    
    (dt/set-bool! "X1" false)
    (is (false? (dt/get-bool "X1")))
    
    (dt/set-bools! {"X1" true "Y2" true "C3" false})
    (is (true? (dt/get-bool "X1")))
    (is (true? (dt/get-bool "Y2")))
    (is (false? (dt/get-bool "C3"))))
  
  (testing "Word data table operations"
    (dt/set-word! "DS1" 42)
    (is (= 42 (dt/get-word "DS1")))
    
    (dt/set-words! {"DS1" 100 "DD2" 200 "DF3" 3.14})
    (is (= 100 (dt/get-word "DS1")))
    (is (= 200 (dt/get-word "DD2")))
    (is (= 3.14 (dt/get-word "DF3")))))

(deftest test-compiler
  (testing "Loading and compiling a program"
    (with-redefs [io/resource (fn [_] (io/file "resources/programs/simpleconveyor.txt"))]
      (is (true? (compiler/load-program! "simpleconveyor.txt")))
      (is (true? (compiler/compile-program!)))
      (is (true? (compiler/program-valid?)))
      
      (let [compiled (compiler/get-compiled-program)]
        (is (= 2 (count compiled)))
        (is (= 1 (:network (first compiled))))
        (is (= "STR" (:instruction (first (:instructions (first compiled))))))))))

(deftest test-runtime
  (testing "PLC runtime execution"
    (with-redefs [io/resource (fn [_] (io/file "resources/programs/simpleconveyor.txt"))]
      (test-setup)
      (dt/set-bool! "X1" true)  ; E-Stop not pressed (NC)
      (dt/set-bool! "X2" true)  ; Stop not pressed (NC)
      (dt/set-bool! "X3" false) ; Start not pressed
      (dt/set-bool! "Y1" false) ; Conveyor initially off
      
      (is (true? (compiler/load-and-compile! "simpleconveyor.txt")))
      
      ;; Simulate one scan
      (let [compiled (compiler/get-compiled-program)
            network (first compiled)
            result (runtime/execute-network network)]
        
        ;; Conveyor should still be off since Start wasn't pressed
        (is (false? (dt/get-bool "Y1")))
        
        ;; Now press Start button
        (dt/set-bool! "X3" true)
        (runtime/execute-network network)
        
        ;; Conveyor should turn on
        (is (true? (dt/get-bool "Y1")))
        
        ;; Release Start button, conveyor should stay on (latched)
        (dt/set-bool! "X3" false)
        (runtime/execute-network network)
        (is (true? (dt/get-bool "Y1")))
        
        ;; Press Stop button
        (dt/set-bool! "X2" false)
        (runtime/execute-network network)
        (is (false? (dt/get-bool "Y1")))
        
        ;; Release Stop, press Start again
        (dt/set-bool! "X2" true)
        (dt/set-bool! "X3" true)
        (runtime/execute-network network)
        (is (true? (dt/get-bool "Y1")))
        
        ;; Press E-Stop
        (dt/set-bool! "X1" false)
        (runtime/execute-network network)
        (is (false? (dt/get-bool "Y1")))))))

(deftest test-plc-program
  (testing "Full PLC program execution"
    (with-redefs [io/resource (fn [_] (io/file "resources/programs/simpleconveyor.txt"))]
      (test-setup)
      
      ;; Setup initial conditions
      (dt/set-bool! "X1" true)  ; E-Stop not pressed (NC)
      (dt/set-bool! "X2" true)  ; Stop not pressed (NC)
      (dt/set-bool! "X3" false) ; Start not pressed
      (dt/set-bool! "Y1" false) ; Conveyor initially off
      
      ;; Load and compile program
      (is (true? (runtime/load-compile-and-run! "simpleconveyor.txt")))
      
      ;; Run a few scans to stabilize
      (dotimes [_ 5]
        (runtime/execute-scan))
      
      ;; Confirm initial state
      (is (false? (dt/get-bool "Y1")))
      
      ;; Start conveyor
      (dt/set-bool! "X3" true)
      (runtime/execute-scan)
      (is (true? (dt/get-bool "Y1")))
      
      ;; Release start button, conveyor stays on
      (dt/set-bool! "X3" false)
      (runtime/execute-scan)
      (is (true? (dt/get-bool "Y1")))
      
      ;; Press E-Stop, conveyor stops
      (dt/set-bool! "X1" false)
      (runtime/execute-scan)
      (is (false? (dt/get-bool "Y1"))))))
