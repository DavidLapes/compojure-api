(ns compojure.api.core
  (:require [clojure.tools.macro :refer [name-with-attributes]]
            [compojure.api.meta :refer [restructure]]
            [compojure.api.middleware :refer [api-middleware]]
            [compojure.api.routes :as routes]
            [compojure.core :refer :all]
            [potemkin :refer [import-vars]]
            [ring.swagger.middleware :as rsm]
            [ring.swagger.common :refer [extract-parameters]]
            [backtick :refer [syntax-quote]]))

(defn api-middleware-with-swagger-data
  "Returns a compojure.api.middleware/api-middlware wrapped handler,
  which publishes the handler meta as swagger-data and has it's own
  meta data."
  [handler options]
  (let [swagger-data (meta handler)]
    (-> handler
        (rsm/wrap-swagger-data swagger-data)
        (api-middleware options)
        (with-meta swagger-data))))

(defmacro api
  "Returns a ring handler wrapped in compojure.api.middleware/api-middlware.
   Creates the route-table at compile-time and passes that into the request via
   ring-swagger middlewares. The mounted api-middleware can be configured by
   optional options map as the first parameter:

      (api
        {:formats [:json :edn}
        (context* \"/api\" []
          ...))

   ... see compojure.api.middleware/api-middleware for possible options."
  [& body]
  (let [[opts body] (extract-parameters body)]
    `(api-middleware-with-swagger-data
       (routes/api-root ~@body)
       ~opts)))

(defmacro defapi
  "Returns a ring handler wrapped in a `api`. Behind the scenes,
   creates the route-table at compile-time and passes that into the request via
   ring-swagger middlewares. The mounted api-middleware can be configured by
   optional options map as the first parameter:

      (defapi app
        {:formats [:json :edn}
        (context* \"/api\" []
          ...))

   ... see compojure.api.middleware/api-middleware for possible options."
  [name & body]
  `(def ~name
     (api ~@body)))

(import-vars [compojure.api.meta middlewares])

(defmacro defroutes*
  "Define a Ring handler function from a sequence of routes. The name may
  optionally be followed by a doc-string and metadata map. Generates an
  extra private Var with `_` + name to the given namespace holding the
  actual routes, caller doesn't have to care about this. Accessing defroutes*
  over Var add tiny run-time penalty, but allows massive better development
  speed as the defroutes* can be compiled seperately."
  [name & routes]
  (let [source (drop 2 &form)
        [name routes] (name-with-attributes name routes)
        route-sym (symbol (str "_" name))
        route-meta {:source `(syntax-quote ~source)
                    :inline true}]
    `(do
       (def ~route-sym (with-meta (routes ~@routes) ~route-meta))
       (alter-meta! (var ~route-sym) assoc :private true)
       (def ~name (var ~route-sym)))))

(defmacro GET*     [& args] (restructure #'GET     args))
(defmacro ANY*     [& args] (restructure #'ANY     args))
(defmacro HEAD*    [& args] (restructure #'HEAD    args))
(defmacro PATCH*   [& args] (restructure #'PATCH   args))
(defmacro DELETE*  [& args] (restructure #'DELETE  args))
(defmacro OPTIONS* [& args] (restructure #'OPTIONS args))
(defmacro POST*    [& args] (restructure #'POST    args))
(defmacro PUT*     [& args] (restructure #'PUT     args))
(defmacro context* [& args] (restructure #'context args {:body-wrap 'compojure.core/routes}))
