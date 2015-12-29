Clucy
=====

[![License EPL](https://img.shields.io/badge/license-EPL-yellow.svg)](https://www.eclipse.org/legal/epl-v10.html)
[![Build Status](https://travis-ci.org/kostafey/clucy.svg?branch=master)](https://travis-ci.org/kostafey/clucy)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.kostafey/clucy.svg)](https://clojars.org/org.clojars.kostafey/clucy)

Clucy is a Clojure interface to [Lucene](http://lucene.apache.org/).

Installation
------------

To install Clucy, add the following dependency to your `project.clj`
file:

[![Clojars Project](http://clojars.org/org.clojars.kostafey/clucy/latest-version.svg)](http://clojars.org/org.clojars.kostafey/clucy)

Usage
-----

#### Search in documents

To use Clucy, first require it:

    (ns example
      (:require [clucy.core :as clucy]))

Then create an index. You can use `(memory-index)`, which stores the search
index in RAM, or `(disk-index "/path/to/a-folder")`, which stores the index in
a folder on disk.

    (def index (clucy/memory-index))

Next, add Clojure maps to the index:

    (clucy/add index
       {:name "Bob", :job "Builder"}
       {:name "Donald", :job "Computer Scientist"})

You can remove maps just as easily:

    (clucy/delete index
       {:name "Bob", :job "Builder"})

Once maps have been added, the index can be searched:

    user=> (clucy/search index "bob" 10)
    ({:name "Bob", :job "Builder"})

    user=> (clucy/search index "scientist" 10)
    ({:name "Donald", :job "Computer Scientist"})

You can search and remove all in one step. To remove all of the
scientists...

    (clucy/search-and-delete index "job:scientist")

#### Search text positions in single document

```clojure
(ns example
  (:use [clucy.core
         clucy.analyzers
         clucy.positions-searcher]))

(binding [*analyzer* (make-analyzer :class :en)]
  (let [test-text "This is the house that Jack built.
                   This is the malt
                   That lay in the house that Jack built."
        index (doto (memory-index)
                (add (set-field-params
                      test-text
                      {:positions-offsets true
                       :vector-positions true})))
        searcher (make-dict-searcher
                  #{"house"
                    "lay"
                    "Jack built"})
        result-iter (searcher index)]
    (sort-by second
             (show-text-matches result-iter test-text))))
```

    => (["house" 12] ["Jack built" 23] ["lay" 95] ["house" 106] ["Jack built" 117])


Storing Fields
--------------

By default all fields in a map are stored and indexed. If you would
like more fine-grained control over which fields are stored and index,
add this to the meta-data for your map.

    (with-meta {:name "Stever", :job "Writer", :phone "555-212-0202"}
      {:phone {:stored false}})

When the map above is saved to the index, the phone field will be
available for searching but will not be part of map in the search
results. This example is pretty contrived, this makes more sense when
you are indexing something large (like the full text of a long
article) and you don't want to pay the price of storing the entire
text in the index.

Default Search Field
--------------------

A field called "\_content" that contains all of the map's values is
stored in the index for each map (excluding fields with {:stored false}
in the map's metadata). This provides a default field to run all
searches against. Anytime you call the search function without
providing a default search field "\_content" is used.

This behavior can be disabled by binding *content* to false, you must
then specify the default search field with every search invocation.
