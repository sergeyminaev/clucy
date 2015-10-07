(ns clucy.test.positions-searcher-ru
  (:refer-clojure :exclude [pop])
  (:use clucy.core
        clojure.test
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
        (binding [*analyzer* (make-analyzer :ru)]
          (stemming-dict #{"пшеница" "синица" "хранится"})))))

  (testing "stemming-text fn"
    (is
     (= '("хран" "синиц" "пшениц")
        (binding [*analyzer* (make-analyzer :ru)]
          (stemming-text "пшеница синица хранится"
                         :format '())))))

  (testing "stemming-word fn"
    (is
     (= "пшениц"
        (binding [*analyzer* (make-analyzer :ru)]
          (stemming-word "пшеница")))))

  (testing "stemming-phrases fn"
    (is
     (= '#{("до" "коров")
           ("ворова" "пшениц")
           ("постро" "джек")}
        (binding [*analyzer* (make-analyzer :ru)]
          (stemming-phrases #{"воровать пшеницу"
                              "построил Джек"
                              "доить корову"})))))

  (testing "make-dict-searcher fn"
    (is
     (= '([176 "пшеницу"]
          [43 "пшеница"]
          [145 "синица"]
          [19 "построил Джек"]
          [107 "построил Джек"]
          [240 "построил Джек"]
          [169 "ворует пшеницу"])
        (binding [*analyzer* (make-analyzer :ru)]
          (let [index (if (< (.length test-text)
                             *ram-allocation-threshold*)
                        (memory-index)
                        (disk-index (.getAbsolutePath (make-temp-dir))))
                _ (add index (set-field-params
                              test-text
                              {:positions-offsets true}))
                searcher (make-dict-searcher
                          #{"синица"
                            "пшеница"
                            "воры пшеницы"
                            "построит Джек"})
                result-iter (searcher index test-text)]
            result-iter)))))

  (testing "stemming exclusion"
    (is (= "чула"
           (binding [*analyzer* (make-analyzer :ru)]
             (stemming-word "чулан"))))
    (is (= "чулан"
           (binding [*analyzer* (make-analyzer
                                 :ru
                                 (file->wordset "russian_stop.txt")
                                 (-> "чулан"
                                     string->stream
                                     stream->wordset))]
             (stemming-word "чулан")))))

  (testing "show-text fn"
    (is (= [["Который" 11] ["построил" 19] ["построил Джек" 19]]
           (show-text
            [[11 18] [19 27] [19 32]]
            (string->stream test-text))))))
