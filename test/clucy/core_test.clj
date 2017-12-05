(ns clucy.core-test
  (:use clucy.core
        clojure.test
        [clojure.set :only [intersection]]))

(def people [{:name "Miles" :age 36}
             {:name "Emily" :age 0.3}
             {:name "Joanna" :age 34}
             {:name "Melinda" :age 34}
             {:name "Mary" :age 48}
             {:name "Mary Lou" :age 39}])

(deftest core

  (testing "memory-index fn"
    (let [index (memory-index)]
      (is (not (nil? index)))))

  (testing "disk-index fn"
    (let [index (disk-index "/tmp/test-index")]
      (is (not (nil? index)))))

  (testing "add fn"
    (let [index (memory-index)]
      (doseq [person people] (add index [person]))
      (is (== 1 (count (:hits (search index "name:miles"))))))
    (let [index (memory-index)]
      (add index people)
      (is (== 1 (count (:hits (search index "name:miles")))))))

  (testing "delete fn"
    (let [index (memory-index)]
      (add index people)
      (delete index [(first people)])
      (is (== 0 (count (:hits (search index "name:miles")))))))

  (testing "search fn"
    (let [index (memory-index)]
      (add index people)
      (is (== 1 (count (:hits (search index "name:miles")))))
      (is (== 1 (count (:hits (search index "name:miles age:100")))))
      (is (== 0 (count (:hits (search index "name:miles AND age:100")))))
      (is (== 0 (count (:hits (search index "name:miles age:100" {:default-operator :and})))))))

  (testing "search-and-delete fn"
    (let [index (memory-index)]
      (add index people)
      (is (< 0 (count (:hits (search index "name:mary")))))
      (search-and-delete index "name:mary" :_content)
      (is (== 0 (count (:hits (search index "name:mary")))))))

  (testing "search fn with highlighting"
    (let [index (memory-index)
          config {:field :name}]
      (add index people)
      (is (= (:_fragments (search index "name:mary" {:highlight config})))
          ["<b>Mary</b>" "<b>Mary</b> Lou"])))

  (testing "search fn returns scores in metadata"
    (let [index (memory-index)
          _ (add index people)
          results (search index "name:mary")]
      (is (true? (every? pos? (map :_score (:hits results)))))
      (is (= 2 (:_total-hits results)))
      (is (pos? (:_max-score results)))
      (is (= (count people) (:_total-hits (search index "*:*" 2))))))

  (testing "pagination"
    (let [index (memory-index)]
      (add index people)
      (is (== 3 (count (:hits (search index "name:m*" {:page 0 :results-per-page 3})))))
      (is (== 1 (count (:hits (search index "name:m*" {:page 1 :results-per-page 3})))))
      (is (empty? (intersection
                    (set (:hits (search index "name:m*" {:page 0 :results-per-page 3})))
                    (set (:hits (search index "name:m*" {:page 1 :results-per-page 3})))))))))

(deftest field-options-test
  (let [index (memory-index)]
    (add index
         [{:id 1
           :tag "fields stored indexed"
           :text "By default all fields in a map are stored and indexed."}
          {:id 2
           :tag "options argument"
           :text "If you would like more fine-grained control use"}
          {:id 3
           :tag "options"
           :text "When the map above is saved to the index"}]
         {:id {:indexed false}
          :tag {:analyzed false}
          :text {:stored false}})
    (is (= (map :id (:hits (search index "text:map")))
           ["3" "1"]))
    (is (= (map :id (:hits (search index "tag:options")))
           ["3"]))))
