(ns clucy.core
  (:import (java.io StringReader)
           (java.nio.file Paths)
           (java.net URI)
           (org.apache.lucene.analysis Analyzer TokenStream)
           (org.apache.lucene.analysis.standard StandardAnalyzer)
           (org.apache.lucene.document Document Field FieldType
                                       Field$Index Field$Store Field$TermVector)
           (org.apache.lucene.index IndexWriter IndexReader Term
                                    IndexWriterConfig DirectoryReader FieldInfo
                                    IndexOptions)
           (org.apache.lucene.queryparser.classic QueryParser)
           (org.apache.lucene.search BooleanClause BooleanClause$Occur
                                     BooleanQuery IndexSearcher Query ScoreDoc
                                     Scorer TermQuery)
           (org.apache.lucene.search.highlight Highlighter QueryScorer
                                               SimpleHTMLFormatter)
           (org.apache.lucene.util Version AttributeSource)
           (org.apache.lucene.store NIOFSDirectory RAMDirectory Directory))
  (:use [clucy.util :only (reader? file?)]))

(def ^{:dynamic true} *version* Version/LUCENE_CURRENT)
(def ^{:dynamic true} *analyzer* (StandardAnalyzer.))

;; To avoid a dependency on either contrib or 1.2+
(defn as-str ^String [x]
  (cond
    (keyword? x) (name x)
    :default (str x)))

;; flag to indicate a default "_content" field should be maintained
(def ^{:dynamic true} *content* true)
;; default field name
(def ^{:dynamic true} *field-name* :_content)

(defn memory-index
  "Create a new index in RAM."
  []
  (RAMDirectory.))

(defn disk-index
  "Create a new index in a directory on disk."
  [^String dir-path]
  (NIOFSDirectory.
   (Paths/get (URI. (str "file:///" dir-path)))))

(defn index-writer
  "Create an IndexWriter."
  ^IndexWriter
  [index]
  (IndexWriter. index
                (IndexWriterConfig. *analyzer*)))

(defn index-reader
  "Create an IndexReader."
  ^IndexReader
  [index]
  (DirectoryReader/open ^Directory index))

(defn set-field-params [item params]
  (with-meta
    (if (instance? clojure.lang.IObj item)
      item
      #(identity item)) params))

(defn make-field-type [meta-map]
  (let [stored? (boolean (get meta-map :stored true))
        omit-norms? (not (boolean (get meta-map :norms true)))
        positions-offsets? (boolean (get meta-map :positions-offsets false))
        vector-positions? (boolean (get meta-map :vector-positions false))]
    (doto (FieldType.)
      (.setIndexOptions (if (get meta-map :indexed true)
                          IndexOptions/DOCS
                          IndexOptions/NONE))
      (.setStored stored?)
      (.setOmitNorms omit-norms?)
      (.setStoreTermVectors (or positions-offsets? vector-positions?))
      (.setStoreTermVectorOffsets positions-offsets?)
      (.setStoreTermVectorPositions vector-positions?))))

(defn add-field
  "Add a Field to a Document.
  Following options are allowed for meta-map:
  :stored  - when false, then do not store the field value in the index.
  :indexed - when false, then do not index the field.
  :norms   - when :indexed is enabled user this option to disable/enable
             the storing of norms.
  :positions-offsets - when true store token positions into the term vector
                       for this field and field's indexed form should be also
                       stored into term vectors.
  :vector-positions  - when true store token positions into the term vector
                       for this field."
  ([document key value]
   (add-field document key value {}))

  ([document key value meta-map]
   (.add ^Document document
         (Field. (as-str key)
                 value
                 (make-field-type meta-map)))))

(defn- map-stored
  "Returns a hash-map containing all of the values in the map that
  will be stored in the search index."
  [map-in]
  (merge {}
         (filter (complement nil?)
                 (map (fn [item]
                        (if (or (= nil (meta map-in))
                                (not= false
                                      (:stored ((first item) (meta map-in)))))
                          item)) map-in))))

(defn- concat-values
  "Concatenate all the maps values being stored into a single string."
  [map-in]
  (apply str (interpose " " (vals (map-stored map-in)))))

(defn- map->document
  "Create a Document from a map."
  [map]
  (let [document (Document.)]
    (doseq [[key value] map]
      (add-field document key (as-str value) (key (meta map))))
    (if *content*
      (add-field document :_content (concat-values map)))
    document))

(defn- set->document
  "Create a Document from a set."
  [set]
  (let [document (Document.)]
    (add-field document
               *field-name*
               (clojure.string/join " " set)
               (meta set))
    document))

(defn- string->document
  "Create a Document from String or java.io.Reader."
  [s]
  (let [document (Document.)]
    (add-field document
               *field-name*
               (s)
               (meta s))
    document))

(defn- file->document
  "Create a Document from java.io.File."
  [s]
  (let [document (Document.)]
    (with-open [rdr (clojure.java.io/reader s)]
      (add-field document
                 *field-name*
                 rdr
                 (meta s)))
    document))

(defn add
  "Add hash-maps to the search index."
  [index & items]
  (with-open [writer (index-writer index)]
    (doseq [i items]
      (cond
        (map? i) (.addDocument writer
                               (map->document i))
        (set? i) (.addDocument writer
                               (set->document i))
        (and (fn? i) (string? (i))) (.addDocument
                                     writer
                                     (string->document i))
        (and (fn? i) (reader? (i))) (.addDocument
                                     writer
                                     (string->document i))
        (and (fn? i) (file? (i))) (.addDocument
                                     writer
                                     (file->document i))))))

(defn delete
  "Deletes hash-maps from the search index."
  [index & maps]
  (with-open [^IndexWriter writer (index-writer index)]
    (doseq [m maps]
      (let [query (BooleanQuery.)]
        (doseq [[key value] m]
          (.add query
                (BooleanClause.
                 (TermQuery. (Term. (.toLowerCase (as-str key))
                                    (.toLowerCase (as-str value))))
                 BooleanClause$Occur/MUST)))
        (.deleteDocuments writer (into-array [query]))))))

(defn- document->map
  "Turn a Document object into a map."
  ([^Document document score]
     (document->map document score (constantly nil)))
  ([^Document document score highlighter]
     (let [m (into {} (for [^Field f (.getFields document)]
                        [(keyword (.name f)) (.stringValue f)]))
           fragments (highlighter m) ; so that we can highlight :_content
           m (dissoc m :_content)]
       (with-meta
         m
         (-> (into {}
                   (for [^Field f (.getFields document)
                         :let [field-type (.fieldType f)]]
                     [(keyword (.name f)) {:stored (.stored field-type)
                                           :tokenized (.tokenized field-type)}]))
             (assoc :_fragments fragments :_score score)
             (dissoc :_content))))))

(defn- make-highlighter
  "Create a highlighter function which will take a map and return highlighted
fragments."
  [^Query query ^IndexSearcher searcher config]
  (if config
    (let [indexReader (.getIndexReader searcher)
          scorer (QueryScorer. (.rewrite query indexReader))
          config (merge {:field :_content
                         :max-fragments 5
                         :separator "..."
                         :pre "<b>"
                         :post "</b>"}
                        config)
          {:keys [field max-fragments separator fragments-key pre post]} config
          highlighter (Highlighter. (SimpleHTMLFormatter. pre post) scorer)]
      (fn [m]
        (let [str (field m)
              token-stream (.tokenStream ^Analyzer *analyzer*
                                         (name field)
                                         (StringReader. str))]
          (.getBestFragments ^Highlighter highlighter
                             ^TokenStream token-stream
                             ^String str
                             (int max-fragments)
                             ^String separator))))
    (constantly nil)))

(defn search
  "Search the supplied index with a query string."
  [index query max-results
   & {:keys [highlight default-field default-operator page results-per-page]
      :or {page 0 results-per-page max-results}}]
  (if (every? false? [default-field *content*])
    (throw (Exception. "No default search field specified"))
    (with-open [reader (index-reader index)]
      (let [default-field (or default-field :_content)
            searcher (IndexSearcher. reader)
            parser (doto (QueryParser. (as-str default-field)
                                       *analyzer*)
                     (.setDefaultOperator (case (or default-operator :or)
                                            :and QueryParser/AND_OPERATOR
                                            :or  QueryParser/OR_OPERATOR)))
            query (.parse parser query)
            hits (.search searcher query (int max-results))
            highlighter (make-highlighter query searcher highlight)
            start (* page results-per-page)
            end (min (+ start results-per-page) (.totalHits hits))]
        (doall
         (with-meta (for [hit (map (partial aget (.scoreDocs hits))
                                   (range start end))]
                      (document->map (.doc ^IndexSearcher searcher
                                           (.doc ^ScoreDoc hit))
                                     (.score ^ScoreDoc hit)

                                     highlighter))
           {:_total-hits (.totalHits hits)
            :_max-score (.getMaxScore hits)}))))))

(defn search-and-delete
  "Search the supplied index with a query string and then delete all
of the results."
  ([index query]
     (if *content*
       (search-and-delete index query :_content)
       (throw (Exception. "No default search field specified"))))
  ([index query default-field]
     (with-open [writer (index-writer index)]
       (let [parser (QueryParser. (as-str default-field) *analyzer*)
             query  (.parse parser query)]
         (.deleteDocuments writer (into-array [query]))))))
