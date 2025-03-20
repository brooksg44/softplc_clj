(ns softplc-clj.core
  (:require [softplc-clj.config :as config]
            [softplc-clj.plc-runtime :as runtime]
            [softplc-clj.compiler :as compiler]
            [softplc-clj.data-table :as dt]
            [softplc-clj.gui :as gui]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io])
  (:gen-class))

;; Command-line options
(def cli-options
  [["-p" "--program PROGRAM" "PLC Program file to load"
    :default "simpleconveyor.txt"]
   ["-s" "--scan-rate RATE" "PLC Scan rate in milliseconds"
    :default 500
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 10000) "Must be a number between 0 and 10000"]]
   ["-c" "--config CONFIG" "Configuration file"
    :default "config.edn"]
   ["-h" "--help"]])

(defn usage
  "Format usage instructions"
  [options-summary]
  (->> ["SoftPLC_Clj - A Clojure implementation of a Soft PLC"
        ""
        "Usage: softplc-clj [options]"
        ""
        "Options:"
        options-summary
        ""
        "Examples:"
        "  softplc-clj -p myprogram.txt -s 100"]
       (clojure.string/join \newline)))

(defn error-msg
  "Format error message"
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn validate-args
  "Validate command line arguments"
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}

      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors) :ok? false}

      :else ; validated args
      {:options options})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn init-resources!
  "Make sure resources directory exists"
  []
  (let [dirs ["resources/programs" "resources/images"]]
    (doseq [dir dirs]
      (.mkdirs (io/file dir)))))

(defn copy-default-programs!
  "Copy default program files if they don't exist"
  []
  (let [default-program "simpleconveyor.txt"
        target-file (io/file "resources/programs" default-program)]
    (when-not (.exists target-file)
      (println "Copying default program:" default-program)
      (io/make-parents target-file)
      (spit target-file
            "NETWORK 1\nSTR X1\nAND X2\nSTR X3\nOR Y1\nANDSTR\nOUT Y1\n\nNETWORK 2\nEND"))))

(defn -main
  "Main entry point for SoftPLC_Clj"
  [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)

      (do
        ;; Initialize resources
        (init-resources!)
        (copy-default-programs!)

        ;; Load configuration
        (when (io/resource (:config options))
          (config/load-config! (:config options)))

        ;; Set configuration from command line
        (config/set-config! :plc-program (:program options))
        (config/set-config! :scan-rate (:scan-rate options))

        ;; Initialize the application
        (println "Starting SoftPLC_Clj...")
        (println "Program:" (config/get-config :plc-program))
        (println "Scan Rate:" (config/get-config :scan-rate) "ms")

        ;; Initialize GUI (this will also start the PLC runtime)
        (gui/init!)))))

(defn start!
  "Start the SoftPLC application programmatically (for REPL use)"
  []
  (init-resources!)
  (copy-default-programs!)
  (gui/init!))

;; For REPL development
(comment
  ;; Start the application from REPL
  (start!)

  ;; Load and run a specific program
  (runtime/load-compile-and-run! "simpleconveyor_subr.txt")

  ;; Manipulate data table values
  (dt/set-bool! "X1" true)
  (dt/set-bool! "X2" true)
  (dt/set-bool! "X3" true)

  ;; Get values
  (dt/get-bool "Y1")

  ;; Set scan rate
  (runtime/set-scan-rate! 100)

  ;; Get runtime stats
  (runtime/get-stats))
