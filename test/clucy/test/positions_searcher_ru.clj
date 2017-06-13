(ns clucy.test.positions-searcher-ru
  (:use clojure.test
        clucy.core
        clucy.util
        clucy.analyzers
        clucy.positions-searcher))

(def ^{:dynamic true} *ram-allocation-threshold* 1048576)

(def test-text
  "Вот дом,
  Который построил Джек.

  А это пшеница,
  Которая в тёмном чулане хранится
  В доме,
  Который построил Джек.

  А это весёлая птица-синица,
  Которая часто ворует пшеницу,
  Которая в тёмном чулане хранится
  В доме,
  Который построил Джек.")

(deftest positions-searcher

  (testing "stemming-dict fn"
    (is
     (= #{"синиц" "хран" "пшениц"}
        (binding [*analyzer* (make-analyzer :class :ru)]
          (stemming-dict #{"пшеница" "синица" "хранится"})))))

  (testing "stemming-text fn"
    (is
     (= '("хран" "синиц" "пшениц")
        (binding [*analyzer* (make-analyzer :class :ru)]
          (stemming-text "пшеница синица хранится"
                         :format '())))))

  (testing "stemming-word fn"
    (is
     (= "пшениц"
        (binding [*analyzer* (make-analyzer :class :ru)]
          (stemming-word "пшеница"))))
    (is
     (= "ива"
        (binding [*analyzer* (make-analyzer :stemmer :ru)]
          (stemming-word "Иван"))))
    (is
     (= "иван"
        (binding [*analyzer* (make-analyzer :stemmer :ru-light)]
          (stemming-word "Иван")))))

  (testing "stemming-phrases fn"
    (is
     (= '#{("до" "коров")
           ("ворова" "пшениц")
           ("постро" "джек")}
        (binding [*analyzer* (make-analyzer :class :ru)]
          (stemming-phrases #{"воровать пшеницу"
                              "построил Джек"
                              "доить корову"})))))

  (testing "stemming exclusion"
    (is (= "чула"
           (binding [*analyzer* (make-analyzer :class :ru)]
             (stemming-word "чулан"))))
    (is (= "чулан"
           (binding [*analyzer* (make-analyzer
                                 :class :ru
                                 :stop-words (file->wordset "russian_stop.txt")
                                 :stem-exclusion-words (-> "чулан"
                                                           string->stream
                                                           stream->wordset))]
             (stemming-word "чулан")))))

  (testing "show-text-matches fn"
    (is (= '(["построил Джек" 19] ["построил" 19] ["Который" 11])
           (show-text-matches
            [[11 18] [19 27] [19 32]]
            (string->stream test-text))))
    (is (= '(["построил Джек" 19] ["построил" 19] ["Который" 11])
           (show-text-matches
            [[11 18] [19 27] [19 32]]
            test-text))))

  (testing "make-dict-searcher fn"
    (is
     (= '([145 151] [176 183] [43 50] [169 183] [19 32] [107 120] [240 253])
        (with-open [rdr (clojure.java.io/reader
                         (string->stream test-text))]
          (binding [*analyzer* (make-analyzer :class :ru)]
            (let [index (doto (memory-index)
                          (add (set-field-params
                                rdr
                                {:stored false
                                 :positions-offsets true
                                 :vector-positions true})))
                  searcher (make-dict-searcher
                            #{"синица"
                              "пшеница"
                              "воры пшеницы"
                              "построит Джек"})
                  result-iter (searcher index)]
              result-iter)))))

    (is
     (= [[1473519 1473540] [1472694 1472712] [800508 800526] [800396 800414]
         [377255 377273]   [2955561 2955575] [2950435 2950450] [2723804 2723819]
         [2662401 2662416] [2608846 2608860] [2524337 2524351] [2523385 2523399]
         [2431274 2431290] [2371039 2371053] [1857830 1857845] [1857430 1857445]
         [1857275 1857290] [1854984 1854998] [1854450 1854463] [1772234 1772248]
         [1571487 1571501] [1562498 1562512] [1539636 1539650] [1462403 1462417]
         [1056870 1056883] [1056436 1056451] [607041 607057] [391036 391050]
         [271274 271287]  [48398 48421]]
        (with-open [rdr (clojure.java.io/reader "test/data/book.txt")]
          (binding [*analyzer* (make-analyzer :class :ru)]
            (let [index (doto (disk-index (get-temp-dir))
                          (add (set-field-params
                                rdr
                                {:stored false
                                 :positions-offsets true
                                 :vector-positions true})))
                  searcher (make-dict-searcher
                            #{"доказательства"
                              "гости стали расходиться"
                              "непоследовательность"})
                  result-iter (searcher index)]
              result-iter)))))

    ;; find phrase by chars distance
    (is
     (= nil
        (with-open [rdr (clojure.java.io/reader
                         (string->stream
                          "построил оченьдлинноеслово Джек"))]
          (binding [*analyzer* (make-analyzer :class :ru)
                    *find-phrase-by-words-distance* false]
            (let [index (doto (memory-index)
                          (add (set-field-params
                                rdr
                                {:stored false
                                 :positions-offsets true})))
                  searcher (make-dict-searcher
                            #{"построил Джек"})
                  result-iter (searcher index)]
              result-iter)))))

    ;; find phrase by words distance
    (is
     (= '([0 31])
        (with-open [rdr (clojure.java.io/reader
                         (string->stream
                          "построил оченьдлинноеслово Джек"))]
          (binding [*analyzer* (make-analyzer :class :ru)
                    *find-phrase-by-words-distance* true]
            (let [index (doto (memory-index)
                          (add (set-field-params
                                rdr
                                {:stored false
                                 :positions-offsets true
                                 :vector-positions true})))
                  searcher (make-dict-searcher
                            #{"построил Джек"})
                  result-iter (searcher index)]
              result-iter)))))

    (is
     (= '(["синиц" [145 151]]
          ["пшениц" [176 183]]
          ["пшениц" [43 50]]
          ["вор пшениц" [169 183]]
          ["постро джек" [19 32]]
          ["постро джек" [107 120]]
          ["постро джек" [240 253]])
        (with-open [rdr (clojure.java.io/reader
                         (string->stream test-text))]
          (binding [*analyzer* (make-analyzer :class :ru)
                    *with-stemmed* true]
            (let [index (doto (memory-index)
                          (add (set-field-params
                                rdr
                                {:stored false
                                 :positions-offsets true
                                 :vector-positions true})))
                  searcher (make-dict-searcher
                            #{"синица"
                              "пшеница"
                              "воры пшеницы"
                              "построит Джек"})
                  result-iter (searcher index)]
              result-iter)))))
    (is (= '([0 6])
           (with-open [rdr (clojure.java.io/reader
                            (string->stream "синица"))]
             (binding [*analyzer* (make-analyzer :class :ru)]
               (let [index (doto (memory-index)
                             (add (set-field-params
                                   rdr
                                   {:stored false
                                    :positions-offsets true})))
                     searcher (make-dict-searcher
                               #{"синица"})
                     result-iter (searcher index)]
                 result-iter)))))
    (is (= '(["синиц" [0 6]])
           (with-open [rdr (clojure.java.io/reader
                            (string->stream "синица"))]
             (binding [*analyzer* (make-analyzer :class :ru)
                       *with-stemmed* true]
               (let [index (doto (memory-index)
                             (add (set-field-params
                                   rdr
                                   {:stored false
                                    :positions-offsets true})))
                     searcher (make-dict-searcher
                               #{"синица"})
                     result-iter (searcher index)]
                 result-iter))))))

  (testing "with disk index"
    (is
     (= '([145 151] [176 183] [43 50] [169 183] [19 32] [107 120] [240 253])
        (binding [*analyzer* (make-analyzer :class :ru)]
          (with-index [index (doto (disk-index (get-temp-dir))
                               (add (set-field-params
                                     test-text
                                     {:positions-offsets true
                                      :vector-positions true})))]
            (let [searcher (make-dict-searcher
                            #{"синица"
                              "пшеница"
                              "воры пшеницы"
                              "построит Джек"})
                  result-iter (searcher index)]
              result-iter))))))

  (testing "usage example"
    (is
     (= '(["построил Джек" 19]
          ["пшеница" 43]
          ["построил Джек" 107]
          ["синица" 145]
          ["ворует пшеницу" 169]
          ["пшеницу" 176]
          ["построил Джек" 240])
        (binding [*analyzer* (make-analyzer :class :ru)]
          (with-index [index (doto (if (< (.length test-text)
                                          *ram-allocation-threshold*)
                                     (memory-index)
                                     (disk-index (get-temp-dir)))
                               (add (set-field-params
                                     test-text
                                     {:positions-offsets true
                                      :vector-positions true})))]
            (let [searcher (make-dict-searcher
                            #{"синица"
                              "пшеница"
                              "воры пшеницы"
                              "построит Джек"})
                  result-iter (searcher index)]
              (sort-by second
                       (show-text-matches result-iter test-text)))))))
    (is (=
         '(["построил Джек" "постро джек" 19]
           ["пшеница" "пшениц" 43]
           ["построил Джек" "постро джек" 107]
           ["синица" "синиц" 145]
           ["ворует пшеницу" "вор пшениц" 169]
           ["пшеницу" "пшениц" 176]
           ["построил Джек" "постро джек" 240])
         (binding [*analyzer* (make-analyzer :class :ru)
                   *with-stemmed* true]
           (let [index (doto (memory-index)
                         (add (set-field-params
                               test-text
                               {:positions-offsets true
                                :vector-positions true})))
                 searcher (make-dict-searcher
                           #{"синица"
                             "пшеница"
                             "воры пшеницы"
                             "построит Джек"})
                 result-iter (searcher index)]
             (sort-by #(nth % 2)
                      (show-text-matches
                       result-iter
                       test-text))))))))
