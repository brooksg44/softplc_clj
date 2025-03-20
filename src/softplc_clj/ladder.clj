(ns softplc-clj.ladder
  (:require [quil.core :as q]
            [clojure.java.io :as io]
            [softplc-clj.compiler :as compiler]
            [softplc-clj.data-table :as dt]))

;; Image resources for ladder elements
(def ladder-images (atom {}))

;; Current ladder view state
(defonce ladder-state (atom {:routine "Main"
                             :network 0
                             :il-text ""
                             :matrix [[]]
                             :rows 9
                             :cols 9}))

(defn- load-image
  "Load an image from the resources directory"
  [filename]
  (try
    (q/load-image (str "images/" filename))
    (catch Exception e
      (println "Failed to load image:" filename)
      nil)))

(defn load-images!
  "Load all ladder element images"
  []
  (reset! ladder-images
          {"noc" (load-image "no60x60.png")
           "nc" (load-image "nc60x60.png")
           "hbar" (load-image "hbar60x60.png")
           "branchttr" (load-image "branchttr60x60.png")
           "branchr" (load-image "branchr60x60.png")
           "branchttl" (load-image "branchttl60x60.png")
           "branchl" (load-image "branchl60x60.png")
           "out" (load-image "coil60x60.png")
           "end" (load-image "func60x60.png")}))

(defn- clear-ladder-diagram!
  "Clear the ladder diagram matrix"
  []
  (let [rows (:rows @ladder-state)
        cols (:cols @ladder-state)
        empty-matrix (vec (repeat rows (vec (repeat cols {:image "hbar" :text ""}))))]
    (swap! ladder-state assoc :matrix empty-matrix)))

(defn get-available-routines
  "Get a list of available routines in the compiled program"
  []
  (keys (compiler/compiled-to-json)))

(defn get-network-count
  "Get the number of networks in the current routine"
  [routine]
  (let [program-json (compiler/compiled-to-json)
        routine-data (get program-json routine)]
    (count (:subrdata routine-data))))

(defn update-ladder-view!
  "Update the ladder diagram view for a specific routine and network"
  [routine network-num]
  (clear-ladder-diagram!)

  (let [program-json (compiler/compiled-to-json)
        routine-data (get program-json routine)]

    (when routine-data
      (let [networks (:subrdata routine-data)]
        (when (and (>= network-num 0) (< network-num (count networks)))
          (let [network (nth networks network-num)
                matrix-data (:matrix-data network)
                il-data (:instructions network)]

            ;; Update IL text
            (swap! ladder-state assoc :il-text
                   (clojure.string/join "\n"
                                        (map #(str (:instruction %) " " (:address %))
                                             il-data)))

            ;; Update matrix data for drawing
            (doseq [instruction matrix-data]
              (let [{:keys [row col addr value]} instruction
                    img-key value
                    display-text (if (= value "end")
                                   "END"
                                   (or addr ""))]

                ;; Update the matrix cell
                (swap! ladder-state
                       (fn [state]
                         (let [matrix (:matrix state)
                               new-matrix (assoc-in matrix [row col] {:image img-key
                                                                      :text display-text})]
                           (assoc state :matrix new-matrix)))))))))))

  ;; Update current routine and network in state
  (swap! ladder-state assoc
         :routine routine
         :network network-num))

(defn draw-ladder
  "Draw the ladder diagram using Quil"
  [x y width height]
  (let [{:keys [matrix rows cols]} @ladder-state
        cell-width (/ width cols)
        cell-height (/ height rows)]

    ;; Draw background
    (q/fill 240 240 240)
    (q/rect x y width height)

    ;; Draw grid cells with ladder elements
    (doseq [row (range rows)
            col (range cols)]
      (let [cell-data (get-in matrix [row col])
            img-key (:image cell-data)
            text (:text cell-data)
            cell-x (+ x (* col cell-width))
            cell-y (+ y (* row cell-height))
            img (get @ladder-images img-key)]

        ;; Draw the ladder element image
        (when img
          (q/image img cell-x cell-y cell-width cell-height))

        ;; Draw text label if present
        (when (not-empty text)
          (q/fill 0)
          (q/text-align :center :bottom)
          (q/text-size 12)
          (q/text text
                  (+ cell-x (/ cell-width 2))
                  (+ cell-y cell-height)))))))

(defn draw-instruction-list
  "Draw the instruction list panel"
  [x y width height]
  (let [il-text (:il-text @ladder-state)]

    ;; Draw background
    (q/fill 255)
    (q/stroke 200)
    (q/rect x y width height)

    ;; Draw title
    (q/fill 0)
    (q/text-align :left :top)
    (q/text-size 14)
    (q/text "Instruction List" (+ x 10) (+ y 10))

    ;; Draw instruction text
    (q/text-size 12)
    (q/text il-text (+ x 10) (+ y 40))))

(defn next-network!
  "Move to the next network if possible"
  []
  (let [routine (:routine @ladder-state)
        current-network (:network @ladder-state)
        network-count (get-network-count routine)]
    (when (< (inc current-network) network-count)
      (update-ladder-view! routine (inc current-network)))))

(defn prev-network!
  "Move to the previous network if possible"
  []
  (let [routine (:routine @ladder-state)
        current-network (:network @ladder-state)]
    (when (pos? current-network)
      (update-ladder-view! routine (dec current-network)))))

(defn set-routine!
  "Set the current routine"
  [routine-name]
  (when (some #(= routine-name %) (get-available-routines))
    (update-ladder-view! routine-name 0)))

;; Initialize ladder view with a program
(defn init-ladder!
  "Initialize the ladder view with the first routine and network"
  []
  (let [routines (get-available-routines)]
    (when (seq routines)
      (update-ladder-view! (first routines) 0))))