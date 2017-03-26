(ns compojure-describe.parse
  (:require [clojure.tools.reader :as rr]
            [clojure.tools.reader.reader-types
             :refer [string-push-back-reader]]
            [clojure.string :as string]))

(declare parse-route-body)

(def ^:private normalized-name
  (memoize (comp string/lower-case name)))

(def ^:private normalized-name-kw
  (memoize (comp keyword string/lower-case name)))

(defn- match-symbol?
  [symbol* expected]
  (.endsWith (normalized-name symbol*) (normalized-name expected)))

(defn- match-symbols?
  [symbol* & expected]
  (some #(match-symbol? symbol* %) expected))

(def ^:private def-atom (atom {}))

; TODO handle doc string
(defn- store-def-form!
  [[def_ var-name body]]
  {:pre [(match-symbol? def_ :def)]}
  (swap! def-atom assoc (normalized-name-kw var-name) body))

(defn- deref-def-form
  [var-name]
  ; log missing case
  (@def-atom (normalized-name-kw var-name)))

(defn- defroutes?
  [form]
  (and (seq? form)
       (match-symbols? (first form) :defroutes :defroutes*)))

(defn- def?
  [form]
  (and (seq? form)
       (match-symbol? (first form) :def)))

(defn- scan
  [reader]
  (loop [forms []]
    (let [form (rr/read {:eof ::eof} reader)]
      (cond
        (= form ::eof)
        forms

        (defroutes? form)
        (recur (conj forms form))

        (def? form)
        (do
          (store-def-form! form)
          (recur forms))

        :else
        (recur forms)))))

(defn- bind-context-to-route
  [{path-prefix :path} {path :path :as route}]
  (assoc route :path (str path-prefix path)))

(defn- parse-route-context
  [[context_ path-prefix args_ & context-body]]
  {:pre [(match-symbols? context_ :context :context*)]}
  (let [context {:path path-prefix}]
    (->> context-body
         (map parse-route-body)
         (filter identity)
         flatten
         (map #(bind-context-to-route context %))
         not-empty)))

(defn- parse-route-method
  [[method path args_ & body_]]
  {:method (normalized-name-kw method) :path path})

(defn- parse-route-body
  [route-body]
  (cond
    (seq? route-body)
    (let [body-type (first route-body)]
      (cond
        (match-symbols? body-type :context :context*)
        (parse-route-context route-body)

        (match-symbols? body-type
          :get :post :put :option :delete :any
          :get* :post* :put* :option* :delete* :any*)
        (parse-route-method route-body)))

    (symbol? route-body)
    (parse-route-body (deref-def-form route-body))))

(defn parse-route
  "Parses a route defination. Route should be defined with `defroutes`.
  Returns nil when no valid route can be parsed."
  [[defroutes_ var-name_ & route-bodies]]
  (->> route-bodies
       (map parse-route-body)
       (filter identity)
       not-empty))

(defn parse-string
  "Parses route definations from string s."
  [s]
  (->> (string-push-back-reader s)
       scan
       (map parse-route)
       (filter identity)
       flatten))

(comment
  ; route.clj
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

  ; test.clj
  (def s (slurp "route.clj"))
  (clojure.pprint/pprint (parse-string s))
  ; =>
  ; ({:method :get, :path "/v1/books"}
  ;  {:method :post, :path "/v1/books"}
  ;  {:method :get, :path "/admin/dashboard"})
  )
