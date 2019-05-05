(ns textpoints.core
  (:require [quil.core :as q :include-macros true]
            [quil.middleware :as m]))

(def log (.-log js/console))

(defn setup []
  ; Set frame rate to 30 frames per second.
  (q/frame-rate 30)
  ; Set color mode to HSB (HSV) instead of default RGB.
  (q/color-mode :hsb)
  ; setup function returns initial state.
  (let [font (q/load-font "Objectivity-Bold.woff")] ;"AvenirNextLTPro-Demi.otf")]
    {:sentence "Hello there, have a nice day!"
     :font-size 192
     :font font
     :max-distance (* 0.5 (Math/sqrt (+ (* (q/width) (q/width))
                                        (* (q/height) (q/height)))))}))

(defn add
  "Adds two points (vectors)"
  [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn sub
  "Subtracts two vectors"
  [[x1 y1] [x2 y2]]
  [(- x1 x2) (- y1 y2)])

(defn magnitude
  "Returns the magnitude of a vector"
  [[x y]]
  (Math/sqrt (+ (* x x)
                (* y y))))

(defn set-magnitude
  "Returns a new vector which is the same as `v` but with its magnitude set to `new-mag`"
  [[x y :as v] new-mag]
  (let [current-mag (magnitude v)]
    [(/ (* x new-mag)
        current-mag)
     (/ (* y new-mag)
        current-mag)]))

(defn limit
  "Returns a vector which is `v` with its magnitude limited to `limit`"
  [v limit]
  (if (< (magnitude v) limit)
    v
    (set-magnitude v limit)))

(defn scale
  "Scale a vector by a value"
  [[x y] scale]
  [(* x scale) (* y scale)])

(def mult scale)

(defn map-val
  "Map a value `x` in the source domain (which is defined between `src-min` and `src-max`
  to a value in the destination domain (between `dest-min` and `dest-max`"
  [x src-min src-max dest-min dest-max]
  (let [ratio (/ (- x src-min)
                 src-max)]
    (+ dest-min (* ratio dest-max))))

(defn arrive
  "Calculate the steering vector needed for a point to move towards its target"
  [{:keys [pos target velocity max-speed max-force] :as _point}]
  (let [desired (sub target pos)
        d (magnitude desired)
        speed (if (>= d 100)
                max-speed
                (map-val d 0 100 0 max-speed))]
    (-> desired
        (set-magnitude speed)
        (sub velocity)
        (limit max-force))))

(defn flee
  "Calculate the steering vector needed for a point to flee
  the given target (when its within the flee-range of the point):"
  [{:keys [pos velocity max-speed max-force flee-distance] :as _point} target]
  (let [desired (sub target pos)
        d (magnitude desired)]
    (if (> d flee-distance)
      [0 0]
      (-> desired
          (set-magnitude max-speed)
          (mult -1)
          (sub velocity)
          (limit max-force)))))

(defn update-point [{:keys [velocity acceleration] :as point}]
  (-> point
      (update :pos add velocity)
      (update :velocity add acceleration)
      (assoc :acceleration [0 0])))

(defn behaviour [point]
  (let [arrive-force (arrive point)
        flee-force (flee point [(q/mouse-x) (q/mouse-y)])]
    (-> point
        (update :acceleration add arrive-force)
        (update :acceleration add (mult flee-force 5)))))

(def update-transducer
  (comp (map behaviour)
        (map update-point)))

(defn update-points [points]
  (into [] (transduce update-transducer
                      conj [] points)))

(defn make-point [ppoint]
  (let [x (.-x ppoint)
        y (.-y ppoint)]
    {:target [x y]
     :size 5
     :max-speed 10
     :max-force 1
     :flee-distance 50
     :pos [(rand-int (q/width)) (rand-int (q/height))]
     :velocity [0 0]
     :acceleration [0 0]}))

(defn reset-points [{:keys [font font-size text] :as state}]
  (let [points (.textToPoints font text 20 200 font-size #js {:sampleFactor 0.14})]
    (mapv make-point points)))

(defn points->new-word [points {:keys [font font-size]} new-word]
  (let [new-points (.textToPoints font new-word 20 200 font-size #js {:sampleFactor 0.14})
        interleaved (->> (interleave points new-points)
                         (partition 2)
                         (reduce (fn [mem [old new]]
                                   (conj mem (assoc old :target [(.-x new) (.-y new)])))
                                 []))
        interleaved (if (< (count interleaved)
                           (count new-points))
                      (reduce (fn [mem new-point]
                                (conj mem (make-point new-point)))
                              interleaved
                              (drop (count interleaved)
                                    new-points))
                      interleaved)]
    interleaved))

(defn transition [{:keys [sentence] :as state}]
  (if-not (seq sentence)
    state
    (let [sentence-words (clojure.string/split sentence " ")]
      (-> state
          (assoc :text (first sentence-words))
          (update :points points->new-word state (first sentence-words))
          (assoc :sentence (clojure.string/join " " (rest sentence-words)))))))

(defn update-state [state]
  (if-not (:points state)
    (transition state)
    (-> state
        (update :points update-points))))

(defn draw-state [{:keys [points max-distance] :as _state}]
  ; Clear the sketch by filling it with dark-grey color.
  (q/background 55)

  (q/no-stroke)

  (doseq [{:keys [pos target size]} points]
    (let [[x y] pos
          dist (magnitude (sub target pos))
          hue (-> (map-val dist
                           0 max-distance
                           0 255)
                  (+ 128)
                  (mod 255))]

      (q/fill hue 255 255)
      (q/ellipse x y size size))))

; this function is called in index.html
(defn ^:export run-sketch []
  (q/defsketch textpoints
    :host "textpoints"
    :size [1024 300]
    ; setup function called only once, during sketch initialization.
    :setup setup
    ; update-state is called on each iteration before draw-state.
    :update update-state
    :draw draw-state
    ; This sketch uses functional-mode middleware.
    ; Check quil wiki for more info about middlewares and particularly
    ; fun-mode.
    :middleware [m/fun-mode]
    :mouse-clicked (fn [state event]
                     (condp = (:button event)
                       :left (assoc state :points (reset-points state))
                       true state))
    :key-pressed (fn [state event]
                   (if-not (= :ArrowRight (:key event))
                     state
                     (transition state)))))

; uncomment this line to reset the sketch:
; (run-sketch)
