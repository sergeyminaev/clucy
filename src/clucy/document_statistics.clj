(ns clucy.document-statistics
  (:use clucy.core
        clucy.analyzers)
  (:import
   (org.apache.lucene.index IndexReader
                            Terms
                            TermsEnum
                            PostingsEnum)))

(defn get-top-words-iterator [index & [top]]
  "Return iterator through words in index with the frequency
(amount of appearance in text).
Use top to limit words with frequency higher or equal to it.
Iterator item structure: [word frequency]."
  (let [docID 0
        ^IndexReader reader (index-reader index)
        ^Terms terms (.getTermVector reader
                                     docID
                                     (as-str *field-name*))
        ^TermsEnum te (.iterator terms)]
    (letfn [(next-terms-enum []
              (if (.next te)
                (let [^PostingsEnum pe (.postings te nil)]
                  (.nextDoc pe)
                  (if (or (nil? top) (>= (.freq pe) top))
                    [(.utf8ToString (.term te)) (.freq pe)]
                    (recur)))))
            (it [] (let [term-freq (next-terms-enum)]
                     (when term-freq
                       (cons term-freq
                             (lazy-seq (it))))))]
      it)))
