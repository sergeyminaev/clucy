(defproject org.clojars.kostafey/clucy "0.5.4.1c"
  :description "A Clojure interface to the Lucene search engine"
  :url "http://github/kostafey/clucy"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.apache.lucene/lucene-core "5.4.1"]
                 [org.apache.lucene/lucene-queryparser "5.4.1"]
                 [org.apache.lucene/lucene-analyzers-common "5.4.1"]
                 [org.apache.lucene/lucene-highlighter "5.4.1"]
                 [me.raynes/fs "1.4.6"]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:1.6  {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7  {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8  {:dependencies [[org.clojure/clojure "1.8.0"]]}}
  :plugins [[lein-cloverage "1.0.6"]])
