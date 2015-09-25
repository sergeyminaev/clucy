(ns clucy.positions-searcher
  (:refer-clojure :exclude [pop])
  (:use clucy.core
        clucy.util
        clucy.analyzers)
  (:import
   (org.apache.lucene.util BytesRef)
   (org.apache.lucene.index IndexWriter
                            DirectoryReader
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

(defn stemming-dict [^clojure.lang.PersistentHashSet dict-words-set]
  "Convert words from dictionary set into stemmed form according to *analyzer*."
  (let [^String text (reduce #(str %1 " " %2) "" dict-words-set)
        ^TokenStream ts (.tokenStream *analyzer* (as-str *field-name*) text)
        ^CharTermAttribute ta (.addAttribute ts CharTermAttribute)
        next-token (fn []
                     (if (.incrementToken ts)
                       (.toString ta)))]
    (.reset ts)
    (loop [result #{}]
      (let [next (next-token)]
        (if next
          (recur (conj result next))
          (do
            (.close ts)
            result))))))

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
                 (map #(.split % " ") dict-phrases-set))))

(defn- get-data-from-terms-enum [^TermsEnum te ^String text]
  (let [^PostingsEnum pe (.postings te nil)]
    (.nextDoc pe)
    (loop [text-occurencies []]
      (if (try (.nextPosition pe)
               true
               (catch Exception e
                 false))
        (recur (cons [(.startOffset pe)
                      ;; word in text as-is
                      (subs text
                            (.startOffset pe)
                            (.endOffset pe))]
                     text-occurencies))
        text-occurencies))))

(defn- get-positions [^TermsEnum te]
  (let [^PostingsEnum pe (.postings te nil)]
    (.nextDoc pe)
    (loop [positions []]
      (if (try (.nextPosition pe)
               true
               (catch Exception e
                 false))
        (recur (cons [(.startOffset pe)
                      (.endOffset pe)]
                     positions))
        positions))))

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
                           end (llast pp)]
                       (< (Math/abs (- start end))
                          *words-distance-in-phrase*)))
                   this-position))]
             (if next-pos
               (recur (next pps) (cons (conj pp next-pos) result))
               (recur (next pps) (cons [] result))))
           result))))))

(defn- find-phrase [^clojure.lang.PersistentVector phrase
                    ^TermsEnum te
                    ^String text]
  "Phrase position searching."
  (let [first-positions (if (.seekExact te (BytesRef. (first phrase)))
                          (get-positions te))]
    (if first-positions
      (loop [words (map #(BytesRef. %) (next phrase))
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
                   (let [phrase-text (subs text
                                           ;; The beginning of the first
                                           ;; word in the phrase
                                           (ffirst phrase-words-positions)
                                           ;; The end of the last
                                           ;; word in the phrase
                                           (llast phrase-words-positions))]
                     [(ffirst phrase-words-positions) phrase-text]))
                 (filter #(not (empty? %)) result))))))))

(defn make-dict-searcher [dict]
  "Construct search function on index for passed dictionary.
Function returns iterator through matches with the following structure:
[start-offset matched-text]."
  (let [docID 0
        phrases (into #{} (filter #(.contains % " ") dict))
        words (clojure.set/difference dict phrases)
        search-terms (stack (map #(BytesRef. %)
                                 (stemming-dict words)))
        search-phrases-set (stack (stemming-phrases phrases))
        searcher (fn [^org.apache.lucene.store.Directory index
                      ^String text]
                   (let [^DirectoryReader ireader (DirectoryReader/open index)
                         ^Terms terms (.getTermVector ireader
                                                      docID
                                                      (as-str *field-name*))
                         ^TermsEnum te (.iterator terms)
                         next-result (fn []
                                       (let [term-from-dict (pop search-terms)]
                                         (if term-from-dict
                                           (if (.seekExact te term-from-dict)
                                             (get-data-from-terms-enum
                                              te text)
                                             (recur))
                                           (let [phrase
                                                 (pop search-phrases-set)]
                                             (if phrase
                                               (or (find-phrase phrase
                                                                te
                                                                text)
                                                   (recur)))))))]
                     (letfn [(it []
                               (let [term (next-result)]
                                 (when term
                                   (cons term
                                         (lazy-seq (it))))))]
                       (lazy-flatten (it)))))]
    searcher))
