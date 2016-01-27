(ns clucy.util
  (:import
   (java.io File
            ByteArrayInputStream)
   (java.nio.charset StandardCharsets)
   (org.apache.lucene.store NIOFSDirectory)))

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
