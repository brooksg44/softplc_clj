(ns softplc-clj.gui
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [softplc-clj.ladder :as ladder]
            [softplc-clj.compiler :as compiler]
            [softplc-clj.plc-runtime :as runtime]
            [softplc-clj.data-table :as dt]
            [softplc-clj.config :as config]
            [clojure.string :as str]))

;; UI Colors
(def colors
  {:root-color [34 72 112]
   :input-color [42 68 148]
   :output-color [78 165 217]
   :button-color [100 100 100]
   :button-hover [120 120 120]
   :button-active [80 80 80]
   :button-text [255 255 255]
   :text-color [255 255 255]})

;; UI Components
(defonce ui-state (atom {:width 850
                          :height 700
                          :buttons []
                          :dropdowns []
                          :active-dropdown nil
                          :monitoring false
                          :show-data-table false}))

;; Button definition
(defn- create-button
  "Create a button definition"
  [id x y width height text callback]
  {:id id
   :x x
   :y y
   :width width
   :height height
   :text text
   :hover false
   :callback callback})

;; Dropdown definition
(defn- create-dropdown
  "Create a dropdown menu definition"
  [id x y width height label options callback]
  {:id id
   :x x
   :y y
   :width width
   :height height
   :label label
   :options options
   :selected (first options)
   :open false
   :callback callback})

;; Show networks callback function
(defn- show-networks-callback []
  (let [routine-dropdown (first (filter #(= (:id %) :routine) (:dropdowns @ui-state)))
        selected-routine (:selected routine-dropdown)
        network-count (ladder/get-network-count selected-routine)
        network-options (map str (range network-count))]
    (swap! ui-state update :dropdowns
           (fn [dropdowns]
             (map (fn [dropdown]
                    (if (= (:id dropdown) :network)
                      (assoc dropdown 
                             :options network-options
                             :selected (first network-options))
                      dropdown))
                  dropdowns)))))

;; Show ladder callback function
(defn- show-ladder-callback []
  (let [routine-dropdown (first (filter #(= (:id %) :routine) (:dropdowns @ui-state)))
        network-dropdown (first (filter #(= (:id %) :network) (:dropdowns @ui-state)))
        selected-routine (:selected routine-dropdown)
        selected-network (Integer/parseInt (:selected network-dropdown))]
    (ladder/update-ladder-view! selected-routine selected-network)))

;; Initialize UI components
(defn- setup-ui-components!
  "Setup UI components like buttons and dropdowns"
  []
  (let [buttons [(create-button :monitor 150 10 100 30 "Monitor" 
                               #(swap! ui-state update :monitoring not))
                (create-button :show-networks 150 50 140 30 "Show Networks" 
                              show-networks-callback)
                (create-button :show-ladder 150 90 140 30 "Show Ladder" 
                              show-ladder-callback)
                (create-button :toggle-data-table 700 10 120 30 "Data Table" 
                              #(swap! ui-state update :show-data-table not))]
        
        ;; Get available routines
        available-routines (or (seq (ladder/get-available-routines)) ["Main"])
        
        ;; Dropdowns
        dropdowns [(create-dropdown :routine 10 50 130 30 "Routine:" available-routines 
                                   #(ladder/set-routine! %))
                  (create-dropdown :network 10 90 130 30 "Network:" ["0"] 
                                  (fn [selected-network]
                                    (ladder/update-ladder-view! 
                                     (:selected (first (filter (fn [d] (= (:id d) :routine)) 
                                                              (:dropdowns @ui-state))))
                                     (Integer/parseInt selected-network))))]]
    
    (swap! ui-state assoc 
           :buttons buttons 
           :dropdowns dropdowns)))

;; Draw a button
(defn- draw-button
  "Draw a button with the given properties"
  [{:keys [x y width height text hover]}]
  (if hover
    (apply q/fill (:button-hover colors))
    (apply q/fill (:button-color colors)))
  
  (q/rect x y width height 5)
  
  (apply q/fill (:button-text colors))
  (q/text-align :center :center)
  (q/text-size 14)
  (q/text text (+ x (/ width 2)) (+ y (/ height 2))))

;; Draw a dropdown
(defn- draw-dropdown
  "Draw a dropdown menu"
  [{:keys [id x y width height label selected options open]}]
  ;; Draw label
  (apply q/fill (:text-color colors))
  (q/text-align :left :center)
  (q/text-size 14)
  (q/text label x (+ y (/ height 2)))
  
  ;; Draw dropdown box
  (apply q/fill (:button-color colors))
  (q/rect (+ x 80) y width height 2)
  
  ;; Draw selected option
  (apply q/fill (:button-text colors))
  (q/text-align :left :center)
  (q/text selected (+ x 90) (+ y (/ height 2)))
  
  ;; Draw dropdown arrow - Fix the arithmetic here
  (q/triangle (+ x 80 (- width 15)) (+ y 10)
              (+ x 80 (- width 5)) (+ y 10)
              (+ x 80 (- width 10)) (+ y 20))
  
  ;; Draw dropdown options if open
  (when open
    (let [option-height 25
          total-height (* option-height (count options))]
      (apply q/fill (:input-color colors))
      (q/rect (+ x 80) (+ y height) width total-height 2)
      
      (apply q/fill (:button-text colors))
      (q/text-align :left :center)
      (doseq [[idx option] (map-indexed vector options)]
        (q/text option 
               (+ x 90) 
               (+ y height (/ option-height 2) (* idx option-height)))))))

;; Draw PLC stats
(defn- draw-plc-stats
  "Draw PLC runtime statistics"
  [x y]
  (let [stats (runtime/get-stats)
        {:keys [running scan-count last-scan-time avg-scan-time 
                min-scan-time max-scan-time]} stats]
    
    (apply q/fill (:text-color colors))
    (q/text-align :left :top)
    (q/text-size 12)
    
    (q/text (str "Status: " (if running "Running" "Stopped")) x y)
    (q/text (str "Scan Count: " scan-count) x (+ y 20))
    
    ;; Fix formatting to handle different types correctly
    (q/text (str "Last Scan: " (if (number? last-scan-time) 
                                 (format "%.2f" (double last-scan-time)) 
                                 "0.00") " ms") 
            x (+ y 40))
    (q/text (str "Avg Scan: " (if (number? avg-scan-time) 
                                (format "%.2f" (double avg-scan-time)) 
                                "0.00") " ms") 
            x (+ y 60))
    (q/text (str "Min Scan: " (if (number? min-scan-time) 
                                (format "%.2f" (double min-scan-time)) 
                                "0.00") " ms") 
            x (+ y 80))
    (q/text (str "Max Scan: " (if (number? max-scan-time) 
                                (format "%.2f" (double max-scan-time)) 
                                "0.00") " ms") 
            x (+ y 100))))

;; Draw data table monitor
(defn- draw-data-table
  "Draw the data table monitor"
  [x y width height]
  (apply q/fill (:output-color colors))
  (q/rect x y width height 5)
  
  (q/fill 255)
  (q/text-align :left :top)
  (q/text-size 16)
  (q/text "Data Table Monitor" (+ x 10) (+ y 10))
  
  (q/text-size 12)
  (q/text "Boolean Variables:" (+ x 10) (+ y 40))
  
  (let [bool-data @dt/bool-data-table
        word-data @dt/word-data-table]
    
    ;; Draw boolean variables
    (let [bool-vars (take 20 (sort (keys bool-data)))]
      (doseq [[idx var-name] (map-indexed vector bool-vars)]
        (let [row (quot idx 5)
              col (rem idx 5)
              var-x (+ x 10 (* col 80))
              var-y (+ y 60 (* row 20))
              var-value (get bool-data var-name false)]
          
          ;; Draw value with color coded background
          (if var-value
            (q/fill 100 200 100)  ; Green for true
            (q/fill 200 100 100)) ; Red for false
          
          (q/rect var-x var-y 70 18 3)
          (q/fill 0)
          (q/text-align :left :center)
          (q/text (str var-name ": " var-value) (+ var-x 5) (+ var-y 9)))))
    
    ;; Draw word variables
    (q/fill 255)
    (q/text "Word Variables:" (+ x 10) (+ y 150))
    
    (let [word-vars (take 15 (sort (keys word-data)))]
      (doseq [[idx var-name] (map-indexed vector word-vars)]
        (let [row (quot idx 3)
              col (rem idx 3)
              var-x (+ x 10 (* col 140))
              var-y (+ y 170 (* row 20))
              var-value (get word-data var-name 0)]
          
          (q/fill 200 200 255)  ; Blue background
          (q/rect var-x var-y 130 18 3)
          (q/fill 0)
          (q/text-align :left :center)
          (q/text (str var-name ": " var-value) (+ var-x 5) (+ var-y 9)))))))

;; Quil setup function
(defn setup
  "Setup the Quil sketch"
  []
  (q/frame-rate 30)
  (q/color-mode :rgb)
  (q/text-font (q/create-font "Arial" 12 true))
  
  ;; Load ladder images
  (ladder/load-images!)
  
  ;; Setup UI components
  (setup-ui-components!)
  
  ;; Initialize ladder view
  (ladder/init-ladder!)
  
  ;; Initial state
  {})

;; Quil update function
(defn update-state
  "Update the application state"
  [state]
  (let [mouse-x (q/mouse-x)
        mouse-y (q/mouse-y)]
    
    ;; Update button hover states
    (swap! ui-state update :buttons
           (fn [buttons]
             (map (fn [button]
                    (let [{:keys [x y width height]} button
                          hover? (and (>= mouse-x x)
                                      (< mouse-x (+ x width))
                                      (>= mouse-y y)
                                      (< mouse-y (+ y height)))]
                      (assoc button :hover hover?)))
                  buttons)))
    
    state))

;; Quil draw function
(defn draw
  "Draw the application"
  [state]
  (q/background 255)
  
  ;; Draw frames
  (apply q/fill (:root-color colors))
  (q/rect 0 0 (:width @ui-state) (:height @ui-state))
  
  (apply q/fill (:input-color colors))
  (q/rect 5 5 (:width @ui-state) 130)
  
  ;; Draw program name
  (apply q/fill (:text-color colors))
  (q/text-align :left :center)
  (q/text-size 16)
  (q/text (str "Program: " (config/get-config :plc-program)) 10 20)
  
  ;; Draw buttons
  (doseq [button (:buttons @ui-state)]
    (draw-button button))
  
  ;; Draw dropdowns
  (doseq [dropdown (:dropdowns @ui-state)]
    (draw-dropdown dropdown))
  
  ;; Draw PLC stats
  (draw-plc-stats 280 10)
  
  ;; Draw ladder diagram
  (if (:show-data-table @ui-state)
    (draw-data-table 5 140 (:width @ui-state) 540)
    (do
      ;; Draw ladder diagram area
      (apply q/fill (:output-color colors))
      (q/rect 5 140 600 540)
      
      ;; Draw instruction list area
      (q/rect 610 140 235 540)
      
      ;; Draw ladder diagram
      (ladder/draw-ladder 10 145 590 530)
      
      ;; Draw instruction list
      (ladder/draw-instruction-list 615 145 225 530))))

;; Mouse event handling
(defn mouse-clicked
  "Handle mouse click events"
  [state event]
  (let [{:keys [x y]} event]
    
    ;; Check button clicks
    (doseq [button (:buttons @ui-state)]
      (let [{:keys [x y width height callback]} button]
        (when (and (>= (:x event) x)
                  (< (:x event) (+ x width))
                  (>= (:y event) y)
                  (< (:y event) (+ y height)))
          (callback))))
    
    ;; Check dropdown clicks
    (doseq [dropdown (:dropdowns @ui-state)]
      (let [{:keys [id x y width height options open callback]} dropdown]
        ;; Check if clicking on dropdown header
        (when (and (>= (:x event) (+ x 80))
                  (< (:x event) (+ x 80 width))
                  (>= (:y event) y)
                  (< (:y event) (+ y height)))
          (swap! ui-state update :dropdowns
                 (fn [dropdowns]
                   (map (fn [dd]
                          (if (= (:id dd) id)
                            (assoc dd :open (not open))
                            (assoc dd :open false)))
                        dropdowns))))
        
        ;; Check if clicking on dropdown options
        (when open
          (let [option-height 25
                total-height (* option-height (count options))]
            (when (and (>= (:x event) (+ x 80))
                      (< (:x event) (+ x 80 width))
                      (>= (:y event) (+ y height))
                      (< (:y event) (+ y height total-height)))
              (let [option-idx (quot (- (:y event) (+ y height)) option-height)
                    selected-option (nth options option-idx)]
                ;; Update selected value
                (swap! ui-state update :dropdowns
                       (fn [dropdowns]
                         (map (fn [dd]
                                (if (= (:id dd) id)
                                  (assoc dd :selected selected-option :open false)
                                  dd))
                              dropdowns)))
                ;; Call the callback with selected value
                (callback selected-option))))))))
  
  state)

;; Key event handling
(defn key-pressed
  "Handle key press events"
  [state event]
  (case (:key event)
    :right (ladder/next-network!)
    :left (ladder/prev-network!)
    nil)
  state)

;; Create main Quil sketch
(defn create-sketch
  "Create the main Quil sketch"
  []
  (q/defsketch softplc-gui
    :title "SoftPLC Clojure"
    :size [(:width @ui-state) (:height @ui-state)]
    :setup setup
    :update update-state
    :draw draw
    :mouse-clicked mouse-clicked
    :key-pressed key-pressed
    :features [:keep-on-top]
    :middleware [m/fun-mode]))

;; Initialize GUI
(defn init!
  "Initialize the GUI"
  []
  (runtime/set-scan-rate! (config/get-config :scan-rate))
  (runtime/load-compile-and-run! (config/get-config :plc-program))
  (create-sketch))
