(ns clucy.test.document-statistics-ru
  (:use clojure.test
        clucy.core
        clucy.analyzers
        clucy.document-statistics)
  (:require clucy.test.positions-searcher-ru))

(deftest document-statistics
  (testing "stemming-dict fn"
    (is
     (= '(["джек" {:count 3, :pos ([249 253] [116 120] [28 32])}]
          ["дом" {:count 3, :pos ([224 228] [91 95] [4 7])}]
          ["котор" {:count 6, :pos ([232 239] [187 194] [155 162] [99 106] [54 61] [11 18])}]
          ["постро" {:count 3, :pos ([240 248] [107 115] [19 27])}]
          ["пшениц" {:count 2, :pos ([176 183] [43 50])}]
          ["тёмном" {:count 2, :pos ([197 203] [64 70])}]
          ["хран" {:count 2, :pos ([211 219] [78 86])}]
          ["чулан" {:count 2, :pos ([204 210] [71 77])}]
          ["эт" {:count 2, :pos ([127 130] [39 42])}])
        (binding [*analyzer* (make-analyzer :class :ru)]
          (let [index (doto (memory-index)
                        (add (set-field-params
                              clucy.test.positions-searcher-ru/test-text
                              {:positions-offsets true})))
                iterator (get-top-words-iterator index 2)]
            (iterator))))))

  (testing "stemming-dict fn"
    (is
     (= '(["дом котор" {:count 3, :pos ([224 239] [91 106] [4 18])}]
          ["котор постро джек" {:count 3, :pos ([232 253] [99 120] [11 32])}]
          ["дом котор постро" {:count 3, :pos ([224 248] [91 115] [4 27])}]
          ["котор постро" {:count 3, :pos ([232 248] [99 115] [11 27])}]
          ["постро джек" {:count 3, :pos ([240 253] [107 120] [19 32])}])
        (binding [*analyzer* (make-analyzer :class :ru)]
          (let [index (doto (memory-index)
                        (add (set-field-params
                              clucy.test.positions-searcher-ru/test-text
                              {:positions-offsets true})))]
            (get-top-phrases index 3))))))

  (testing "get-word-count fn"
    (is
     (= 30
        (binding [*analyzer* (make-analyzer :class :ru)]
          (let [index (doto (memory-index)
                        (add (set-field-params
                              clucy.test.positions-searcher-ru/test-text
                              {:positions-offsets true})))]
            (get-word-count index)))))))
