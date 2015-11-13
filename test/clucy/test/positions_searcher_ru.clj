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
          (stemming-word "пшеница")))))

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
                                 :positions-offsets true})))
                  searcher (make-dict-searcher
                            #{"синица"
                              "пшеница"
                              "воры пшеницы"
                              "построит Джек"})
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
                                 :positions-offsets true})))
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
          (let [index (doto
                          (if (< (.length test-text)
                                 *ram-allocation-threshold*)
                            (memory-index)
                            (disk-index (.getAbsolutePath (make-temp-dir))))
                        (add (set-field-params
                              test-text
                              {:positions-offsets true})))
                searcher (make-dict-searcher
                          #{"синица"
                            "пшеница"
                            "воры пшеницы"
                            "построит Джек"})
                result-iter (searcher index)]
            (sort-by second
                     (show-text-matches result-iter test-text))))))
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
                               {:positions-offsets true})))
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
