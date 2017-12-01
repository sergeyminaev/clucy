;; пока artifactory не умеет https
(require 'cemerick.pomegranate.aether)
(cemerick.pomegranate.aether/register-wagon-factory!
 "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))

(defproject clucy "0.4.0"
  :description "A Clojure interface to the Lucene search engine"
  :url "http://github/weavejester/clucy"
  :dependencies [[org.clojure/clojure "1.9.0-RC2"]

                 [org.apache.lucene/lucene-core "5.5.5"]
                 [org.apache.lucene/lucene-queryparser "5.5.5"]
                 [org.apache.lucene/lucene-analyzers-common "5.5.5"]
                 [org.apache.lucene/lucene-highlighter "5.5.5"]

                 ;; [org.apache.lucene/lucene-core "7.1.0"]
                 ;; [org.apache.lucene/lucene-queryparser "7.1.0"]
                 ;; [org.apache.lucene/lucene-analyzers-common "7.1.0"]
                 ;; [org.apache.lucene/lucene-highlighter "7.1.0"]
                 ]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"})
