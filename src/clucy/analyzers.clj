(ns clucy.analyzers
  (:require [clojure.java.io :as io])
  (:import
   (java.io InputStream)
   (java.nio.charset StandardCharsets)
   (org.apache.lucene.analysis.util WordlistLoader
                                    CharArraySet)
   (org.apache.lucene.analysis.snowball SnowballFilter)
   (org.apache.lucene.util IOUtils)
   (org.apache.lucene.analysis.standard StandardAnalyzer)
   (org.apache.lucene.analysis.ru RussianAnalyzer)
   (org.apache.lucene.analysis.en EnglishAnalyzer)))

(def analysers-class-map
  {:standard org.apache.lucene.analysis.standard.StandardAnalyzer
   :en       org.apache.lucene.analysis.en.EnglishAnalyzer
   :ru       org.apache.lucene.analysis.ru.RussianAnalyzer})

(defn make-analyzer
  ([] (make-analyzer :standard))
  ([analyzer-type]
   (let [analyzer-class (analysers-class-map analyzer-type)]
     (.newInstance analyzer-class)))
  ([analyzer-type
    ^CharArraySet stop-set
    ^CharArraySet stem-exclusion-set]
   (let [analyzer-class (analysers-class-map analyzer-type)
         ctor (.getConstructor analyzer-class
                               (into-array [CharArraySet
                                            CharArraySet]))
         analyser (.newInstance
                   ctor
                   (into-array
                    [stop-set
                     stem-exclusion-set]))]
     analyser)))

(defn file->wordset ^CharArraySet [^String file-name]
  (WordlistLoader/getSnowballWordSet
   (IOUtils/getDecodingReader SnowballFilter
                              file-name
                              StandardCharsets/UTF_8)))

(defn resource->wordset ^CharArraySet [^String resource-file-name]
  (WordlistLoader/getSnowballWordSet
   (IOUtils/getDecodingReader
    (io/input-stream
     (io/resource resource-file-name))
    StandardCharsets/UTF_8)))

(defn stream->wordset ^CharArraySet [^InputStream istream]
  (WordlistLoader/getSnowballWordSet
   (IOUtils/getDecodingReader istream
                              StandardCharsets/UTF_8)))
