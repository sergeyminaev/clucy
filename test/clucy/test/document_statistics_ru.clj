(ns clucy.test.document-statistics-ru
  (:use clojure.test
        clucy.core
        clucy.analyzers
        clucy.document-statistics)
  (:require clucy.test.positions-searcher-ru))

(deftest document-statistics
  (testing "stemming-dict fn"
    (is
     (= '(["джек" 3]
          ["дом" 3]
          ["котор" 6]
          ["постро" 3]
          ["пшениц" 2]
          ["тёмном" 2]
          ["хран" 2]
          ["чулан" 2]
          ["эт" 2])
        (binding [*analyzer* (make-analyzer :class :ru)]
          (let [index (doto
                          (memory-index)
                        (add (set-field-params
                              clucy.test.positions-searcher-ru/test-text
                              {:positions-offsets true})))
                iterator (get-top-words-iterator index 2)]
            (iterator)))))))
