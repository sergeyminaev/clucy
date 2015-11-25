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
          (let [index (doto (memory-index)
                        (add (set-field-params
                              clucy.test.positions-searcher-ru/test-text
                              {:positions-offsets true})))
                iterator (get-top-words-iterator index 2)]
            (iterator))))))

  (testing "stemming-dict fn"
    (is
     (= '(["дом котор" {:pos ([224 239] [91 106] [4 18]), :count 3}]
          ["котор постро джек" {:pos ([232 253] [99 120] [11 32]), :count 3}]
          ["дом котор постро" {:pos ([224 248] [91 115] [4 27]), :count 3}]
          ["котор постро" {:pos ([232 248] [99 115] [11 27]), :count 3}]
          ["постро джек" {:pos ([240 253] [107 120] [19 32]), :count 3}])
        (binding [*analyzer* (make-analyzer :class :ru)]
          (let [index (doto (memory-index)
                        (add (set-field-params
                              clucy.test.positions-searcher-ru/test-text
                              {:positions-offsets true})))]
            (get-top-phrases index 3)))))))
