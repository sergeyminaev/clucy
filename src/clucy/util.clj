(ns clucy.util
  (:import
   (java.io File
            ByteArrayInputStream)
   (java.nio.charset StandardCharsets)
   (org.apache.lucene.store NIOFSDirectory))
  (:require [clojure.string :as string]))

(defn ^File make-temp-dir
  ([^String prefix]
   (let [path (new File "/var/tmp/")]
     (let [temp-file (File/createTempFile prefix "tmp" path)]
       (.delete temp-file)
       (.mkdir temp-file)
       temp-file)))
  ([] (make-temp-dir "lucene")))

(defn ^String get-temp-dir
  ([^String prefix]
   (.getAbsolutePath (make-temp-dir prefix)))
  ([] (.getAbsolutePath (make-temp-dir))))

(defn llast [x]
  (-> x last last))

(defn lazy-flatten [s]
  (when-not (empty? s)
    (concat (first s) (lazy-seq (lazy-flatten (rest s))))))

(defn string->stream [s]
  (ByteArrayInputStream.
   (.getBytes s StandardCharsets/UTF_8)))

(defn chars->string [chars]
  "Make a string from a sequence of characters."
  (apply str chars))

(defn read-chars [^java.io.Reader reader
                  ^Integer num-of-chars]
  (let [cbuf (char-array num-of-chars)]
    (.read reader cbuf 0 num-of-chars)
    cbuf))

(defn istream? [arg]
  (instance? java.io.InputStream arg))

(defn reader? [arg]
  (instance? java.io.Reader arg))

(defn file? [arg]
  (instance? java.io.File arg))

(defn file-index? [arg]
  (instance? NIOFSDirectory arg))

(defn ends-with [s tail]
  (let [lt (count tail)
        ls (count s)]
    (and (>= ls lt)
         (= (subs s (- ls lt)) tail))))

(defmacro third [x]
  `(get ~x 2))

;; To avoid a dependency on either contrib or 1.2+
(defn as-str ^String [x]
  (cond
    (keyword? x) (name x)
    :default (str x)))

(defn get-value-or-vector 
  "If the given map contains the key it returns a vector of current 
  values corresponding to the key with value appended to it."
  [in-map key value]
  (if (contains? in-map key)
    (let [existing-value (get in-map key)]
      (if (vector? existing-value)
        (into existing-value [value])
        [existing-value value]))
    value))

(defn nested-key-map->dotted-key-map 
  "Converts a nested map into a single level map keywords being dotted.
  {:a {:b {:c 3} :d 2} :h 1} -> {:a.b.c 3 :a.d 2 :h 1}"
  [a-map]
  (let [r (reduce (fn [result [k v]]
                  (if-not (map? v)
                    {:result (assoc (:result result)
                                    k v)
                     :changed (:changed result)}
                    (let [m (into {} (map (fn [[a-k a-v]]
                                            [(keyword (str (as-str k) "." 
                                                           (as-str a-k))) a-v])
                                          v))]
                      {:result (merge (:result result)
                                      m)
                       :changed true}))) {:result {} :changed false} a-map)]
    (if (:changed r)
      (nested-key-map->dotted-key-map (:result r))
      (:result r))))

(defn dotted-key-map->nested-key-map 
  "Converts a dotted map into a nested map.
  {:a.b.c 3 :a.d 2 :h 1} -> {:a {:b {:c 3} :d 2} :h 1}"
  [dotted-map]
  (reduce (fn [r [k v]]
            (assoc-in r (into [] (map keyword (string/split (as-str k) #"\."))) 
                        v)) {} dotted-map))
