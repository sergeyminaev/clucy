(ns clucy.document-statistics
  (:use clucy.core
        clucy.analyzers)
  (:require [clucy.positions-searcher :as pos])
  (:import
   (org.apache.lucene.index IndexReader
                            Terms
                            TermsEnum
                            PostingsEnum)))

(defn get-word-count [index]
  "Number of words in index."
  (let [[te _ ] (pos/get-terms-enum index)]
    (loop [word-count 0]
      (if (.next te)
        (let [^PostingsEnum pe (.postings te nil)]
          (.nextDoc pe)
          (recur (+ word-count (.freq pe))))
        word-count))))

(defn get-top-words-iterator [index & [top]]
  "Return iterator through words in index with the frequency
(amount of appearance in text).
Use top to limit words with frequency higher or equal to it.
Iterator item structure: [word frequency]."
  (let [[te _ ] (pos/get-terms-enum index)]
    (letfn [(next-terms-enum []
              (if (.next te)
                (let [[term pos freq] (pos/get-positions te
                                                         :get-term  true
                                                         :get-freq  true
                                                         :to-string true)]
                  (if (or (nil? top) (>= freq top))
                    [term {:count freq :pos pos}]
                    (recur)))))
            (it [] (let [term-freq (next-terms-enum)]
                     (when term-freq
                       (cons term-freq
                             (lazy-seq (it))))))]
      it)))

(defn get-top-phrases [index & [top]]
  "Return top most common phrases in index.
Output structure: ([phrase {:pos [beg end] :count frequency}]... )"
  (let [[te _ ] (pos/get-terms-enum index)
        ;; [[term-as-BytesRef [[beg end]... ]]... ]
        terms-iter (pos/get-term-iter te)
        ;; [[word [beg end]]... ]
        flatten-terms-vector (loop [result (list)
                                    rest-terms (terms-iter)]
                               (let [term-item (first rest-terms)]
                                 (if term-item
                                   (let [word (.utf8ToString
                                               (first term-item))
                                         positions (second term-item)]
                                     (recur (concat
                                             (map #(vector word %)
                                                  positions)
                                             result)
                                            (next rest-terms)))
                                   result)))
        ordered-words (sort-by #(-> % second first) flatten-terms-vector)
        ;; ((["word1" [beg end]] ["word2" [beg end]])... )
        combinations (concat (partition 3 1 ordered-words)
                             (partition 2 1 ordered-words))
        all-phrases (loop [result {}
                           rest-comb combinations]
                      (let [comb-item (first rest-comb)]
                        (if comb-item
                          (let [stemmed-phrase
                                (clojure.string/join " " (map first comb-item))
                                pos-phrase [(-> comb-item
                                                first
                                                second
                                                first)
                                            (-> comb-item
                                                last
                                                second
                                                second)]]
                            (recur
                             (update-in result [stemmed-phrase]
                                        (fn [x] {:count (if (:count x)
                                                          (+ 1 (:count x))
                                                          1)
                                                 :pos (if (:pos x)
                                                        (cons pos-phrase
                                                              (:pos x))
                                                        (list pos-phrase))}))
                             (next rest-comb)))
                          result)))
        top-phrases (filter (fn [x]
                              (>= (-> x second :count) top)) all-phrases)]
    top-phrases))
