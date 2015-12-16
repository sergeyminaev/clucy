(ns clucy.positions-searcher
  (:use clucy.core
        clucy.util
        clucy.analyzers)
  (:require
   clojure.set)
  (:import
   (java.io PushbackInputStream
            PushbackReader)
   (org.apache.lucene.util BytesRef)
   (org.apache.lucene.index IndexWriter
                            IndexReader
                            Terms
                            TermsEnum
                            PostingsEnum
                            IndexOptions)
   (org.apache.lucene.document Document
                               Field
                               FieldType)
   (org.apache.lucene.analysis TokenStream)
   (org.apache.lucene.analysis.tokenattributes CharTermAttribute)))

(def ^{:dynamic true}
  *words-distance-in-phrase*
  "Maximum distance (in chars) between words in phrase
  from the end of the previous word to the beginning of the next word."
  10)

(def ^{:dynamic true}
  *with-stemmed*
  "When true make-dict-searcher return the iteratior with the following
  item structure:
  [stemmed-word [start-offset end-offset]], [start-offset end-offset] otherwise.

  And show-text-matches return [[matched-text stemmed-text beg]... ],
  assume first argument has [[stemmed-word [start-offset end-offset]]... ]
  structure."
  false)

(defn stemming-text [^String text
                     & {:keys [format] :or {format #{}}}]
  "Convert words from text into stemmed form according to *analyzer*."
  (let [^TokenStream ts (.tokenStream *analyzer* (as-str *field-name*) text)
        ^CharTermAttribute ta (.addAttribute ts CharTermAttribute)
        next-token (fn []
                     (if (.incrementToken ts)
                       (.toString ta)))]
    (.reset ts)
    (loop [result format]
      (let [next (next-token)]
        (if next
          (recur (conj result next))
          (do
            (.close ts)
            result))))))

(defn stemming-dict [^clojure.lang.PersistentHashSet dict-words-set]
  "Convert words from dictionary set into stemmed form according to *analyzer*."
  (let [^String text (clojure.string/join " " dict-words-set)]
    (stemming-text text)))

(defn stemming-word [^String word]
  "Get stemmed form of word according to *analyzer*."
  (let [^TokenStream ts (.tokenStream *analyzer* (as-str *field-name*) word)
        ^CharTermAttribute ta (.addAttribute ts CharTermAttribute)]
    (.reset ts)
    (.incrementToken ts)
    (let [result (.toString ta)]
      (.close ts)
      result)))

(defn stemming-phrases [^clojure.lang.PersistentHashSet dict-phrases-set]
  "Get stemmed form of words in phrases according to *analyzer*."
  (into #{} (map (fn [phrase-words]
                   (doall
                    (map #(stemming-word %)
                         phrase-words)))
                 (map #(.split % "[ -]") dict-phrases-set))))

(defn get-positions [^TermsEnum te & {:keys [get-term
                                             get-freq
                                             to-string]
                                      :or {get-term false
                                           get-freq false
                                           to-string false}}]
  "When get-term is true return term itself with its positions
  [term-as-BytesRef [[beg end]... ]]

  Return term positions otherwise
  [[beg end]... ]"
  (let [pe ^PostingsEnum (doto (.postings te nil)
                           (.nextDoc))
        positions (loop [positions []]
                    (if (try (.nextPosition pe)
                             true
                             (catch Exception e
                               false))
                      (recur (cons [(.startOffset pe)
                                    (.endOffset pe)]
                                   positions))
                      positions))
        term-repr (if get-term
                    (if to-string (.utf8ToString (.term te)) (.term te)))]
    (cond
      (and get-term get-freq) [term-repr positions (.freq pe)]
      get-term [term-repr positions]
      get-freq [positions (.freq pe)]
      :else positions)))

(defn- filter-near-to [;; Already founded words position -
                       ;; first words in the phrase
                       previous-positions
                       ;; Positions vector [[beg end]...] of the next
                       ;; word in phrase
                       this-position]
  (if (= previous-positions [])
    []
    (filter
     not-empty
     (loop [;; Following word positions chain
            pps previous-positions
            ;; New filtered chain of matched words of the phrase
            result []]
       (let [pp (first pps)]
         (if pp
           (let [;; Next word position of the phrase
                 next-pos
                 (first
                  (filter
                   (fn [;; Matches of the searching word of the phrase
                        ;; (positions vector [beg end])
                        tp]
                     (let [;; The beginning (start-offset) of the new word
                           ;; (of the phrase) position in the text
                           start (first tp)
                           ;; The end (end-offset) of the previous (last)
                           ;; matched word of the phrase in text
                           end (llast pp)
                           delta (Math/abs (- start end))]
                       (and
                        ;; If phrase is a double word e.g. "green green" -
                        ;; we find the same word
                        (not (>= (-> pp last first) start))
                        (< delta *words-distance-in-phrase*))))
                   this-position))]
             (if next-pos
               (recur (next pps) (cons (conj pp next-pos) result))
               (recur (next pps) (cons [] result))))
           result))))))

(defn- find-phrase [^clojure.lang.PersistentVector phrase
                    ^TermsEnum te]
  "Phrase position searching."
  (let [first-positions (if (and te (.seekExact te (first phrase)))
                          (get-positions te))]
    (if first-positions
      (loop [words (next phrase)
             result (map vector first-positions)]
        (let [word (first words)]
          (if word
            (if (.seekExact te word)
              (let [filtered (filter-near-to result (get-positions te))]
                (if (= filtered [])
                  ;; Empty sequence after the last filter iteration -
                  ;; phrase is not found.
                  nil
                  (recur (next words) filtered)))
              nil)
            (map (fn [phrase-words-positions]
                   [;; The beginning of the first
                    ;; word in the phrase
                    (ffirst phrase-words-positions)
                    ;; The end of the last
                    ;; word in the phrase
                    (llast phrase-words-positions)])
                 (filter #(not (empty? %)) result))))))))


(defn get-terms-enum [index]
  "Index => [TermsEnum size]."
  (let [docID 0
        ^IndexReader reader (index-reader index)
        ^Terms terms (.getTermVector reader
                                     docID
                                     (as-str *field-name*))
        terms-lenght (if terms (.size terms) 0)
        ^TermsEnum te (if terms (.iterator terms))]
    [te terms-lenght]))

(defn get-term-iter [^TermsEnum terms-enum]
  "TermsEnum => [term-as-BytesRef [[beg end]... ]] iterator."
  (letfn
      [(next-term []
         (if (and terms-enum (.next terms-enum))
           (get-positions terms-enum :get-term true)))
       (it [] (let [term (next-term)]
                (when term
                  (cons term
                        (lazy-seq (it))))))]
    it))

(defn make-dict-searcher [dict]
  "Construct search function on index for passed dictionary.
  Function returns iterator through matches with the following structure:
  [start-offset end-offset].

  When *with-stemmed* is true, the iteratior item is the following:
  [stemmed-word [start-offset end-offset]]"
  (let [phrases (into #{} (filter #(or (.contains % " ")
                                       (.contains % "-")) dict))
        words (clojure.set/difference dict phrases)
        search-words (clojure.set/union (stemming-dict words) words)
        search-phrases (clojure.set/union (stemming-phrases phrases)
                                          (map #(.split % "[ -]") phrases))
        br-words (map #(BytesRef. %) search-words)
        br-phrases (map (fn [phrase] (map #(BytesRef. %) phrase)) search-phrases)
        iwords (into #{} br-words)
        dict-lenght (count words)
        with-stemmed *with-stemmed*
        searcher (fn [^org.apache.lucene.store.Directory index]
                   (let [[^TermsEnum te terms-lenght] (get-terms-enum index)
                         term-iter (get-term-iter te)
                         result (filter
                                 #(not (nil? %))
                                 (concat
                                  (if (< terms-lenght (* dict-lenght 5))
                                    (map (fn [[wd ; BytesRef word
                                               ps ; Positions [[beg end]... ]
                                               ]]
                                           (if (iwords wd)
                                             (if with-stemmed
                                               (let [word (.utf8ToString wd)]
                                                 (map (fn [pos]
                                                        [word pos])
                                                      ps))
                                               ps)))
                                         (term-iter))
                                    (map (fn [br-word word]
                                           (if (.seekExact te br-word)
                                             (if with-stemmed
                                               (map (fn [pos]
                                                      [word pos])
                                                    (get-positions te))
                                               (get-positions te))))
                                         br-words search-words))
                                  (map (fn [br-phrase phrase]
                                         (if with-stemmed
                                           (let [phrase-str
                                                 (clojure.string/join " " phrase)]
                                             (map (fn [pos]
                                                    [phrase-str pos])
                                                  (find-phrase br-phrase te)))
                                           (find-phrase br-phrase te)))
                                       br-phrases search-phrases)))]
                     (lazy-flatten result)))]
    searcher))

(defn show-text-matches [positions text-data]
  "Convert positions [[beg eng]...] sequence to [[matched-text beg]... ]
  from text-data (istream or String as text source).

  When *with-stemmed* is true return [[matched-text stemmed-text beg]... ],
  assume first argument has [[stemmed-word [start-offset end-offset]]... ]
  structure."
  (let [get-first (if *with-stemmed*
                    (fn [x] (-> x second first))
                    (fn [x] (first x)))
        positions (sort-by get-first positions)]
    (cond
      ;; ---
      (string? text-data)
      (if *with-stemmed*
        (reverse (map (fn [[stemmed [beg end]]]
                        [(subs text-data beg end) stemmed beg])
                      positions))
        (reverse (map (fn [[beg end]]
                        [(subs text-data beg end) beg])
                      positions)))
      ;; ---
      (istream? text-data)
      (let [pb-stream ^PushbackInputStream (PushbackInputStream. text-data)]
        (with-open [rdr (PushbackReader. (clojure.java.io/reader pb-stream)
                                         256)]
          (loop [pos-runner positions
                 prev-beg 0
                 prev-end 0
                 result '()]
            (let [item (first pos-runner)
                  pos (if *with-stemmed* (second item) item)
                  stemmed (first item)
                  beg (first pos)
                  end (second pos)]
              (if item
                (do
                  (assert (< beg end))
                  (if (< prev-end beg)
                    (read-chars rdr (- beg prev-end))
                    ;; End of the previous entity (word or phrase) is after
                    ;; the current entity beginning.
                    (do
                      (.unread rdr (char-array (ffirst result)))
                      (read-chars rdr (- beg prev-beg))))
                  (let [delta (- end beg)
                        matched-text (chars->string (read-chars rdr delta))]
                    (recur
                     (next pos-runner)
                     beg
                     end
                     (conj result
                           (if *with-stemmed*
                             [matched-text stemmed beg]
                             [matched-text beg])))))
                result))))))))
