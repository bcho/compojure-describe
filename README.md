# compojure-describe

A Clojure library designed to parse compojure route definations.

## Example

`route.clj`
```
(ns route
  (:require [compojure.core :refer :all]))

(defroutes routes
  (context "/v1" []
    (GET "/books" []
      (list-books))

    (POST "/books" []
      (create-books)))

  (context "/admin" []
    (GET "/dashboard" []
      (show-dashboard))))
```

In repl:
```
user=>(def s (slurp "route.clj"))
user=>(clojure.pprint/pprint (compojure-describe.parse/parse-string s))
({:method :get, :path "/v1/books"}
 {:method :post, :path "/v1/books"}
 {:method :get, :path "/admin/dashboard"})
nil
```

## Usage

TODO

## License

[MIT](https://hbc.mit-license.org/)
