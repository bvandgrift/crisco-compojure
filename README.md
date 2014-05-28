# A Clojure / Datomic Web App Tutorial -- Welcome

Welcome! Let's assume the author is friendly and welcoming,
encouraging you to move along and grow as a software developer
and that we're here to help. None of that's true, but it seems
like the kind of things that should go into a paragraph at the
beginning of a tutorial. On to the work!

## Motivation

There are several tutorials in play about building web applications
in Clojure. If you already know what you're doing, this may be a
remedial exercise. Likewise, there are a few tutorials around
getting up and running with Datomic.

My problem with the vast majority is that they seem to be written
for people who don't need a tutorial, and by and large all have
the stench of *read the code and you'll understand*, quickly coupled
with *and if you don't, you're not smart enough to use this
technology anyway*. A git repo isn't a tutorial, and neither are API docs.
Those are a way of telling your users to *fuck off, you're busy* while
handing them just enough that you can claim that you pandered to the
community.

So there's this. I hope you enjoy it.

## Audience

This series targets those who are familiar
enough with Clojure that we won't need to go through syntax details,
focusing instead on the nitty gritty of getting a web-facing application
backed by Datomic designed and shipped.

If you are new to programming,
Clojure or both, I give Kyle Kingsbury's ['Clojure from the ground up'
series](http://aphyr.com/posts/301-clojure-from-the-ground-up-welcome) two
enthusiastic thumbs up. I'd give it more praise if I had more thumbs.

If you'd like a readable overview of what Datomic is and does, Daniel
Higginbotham's ['Datomic for Five-Year-Olds'](http://www.flyingmachinestudios.com/programming/datomic-for-five-year-olds/)
is a good (if a little dated) start. You might follow up with [datomic.com](http://datomic.com)
and [docs.datomic.com](http://docs.datomic.com), or you can just keep reading.

## Goals

The litany of starting applications for learning a new
web development platform is long. Blogs are popular, todo
lists have made a big splash. Lately, it's the
[url shortener](https://github.com/search?q=url+shorten+clojure).
We'll go with that one, with the following goals:

* explain how to develop web applications w/ clojure and datomic
* shorten urls (targets) into a shorter form (called a 'slug')
* generate a slug if none is specified
* store the shortened urls in Datomic
* collect data on traffic (ip address, etc.)
* accept new urls in a nice UI
* display traffic data in a nice UI
* authenticate users
* authenticate registered applications with an API key
* be fast
* not get murdered

This is an ambitious list. It has enough complexity to make it
useful rather than just demonstrating toy code. We'll also be
evolving it organically together---instead of dumping a complete
thing on you and explaining how we did it, we'll walk through
each bit, what decisions were made, and then move on to the next
thing.

As a for instance, we'll start with a data structure in memory,
written with compojure. We'll move on to store the data in datomic,
then write a new data structure for tracking information.  After
that, we'll build a neat UI. Etcetera.

## Assumptions

We have some assumptions built in: First, that you're either running
a unix-like OS, or are prepared to deal with the overhead of trying to
do this on Windows without my help. Second, you should have lein and
Java installed. If you don't, go [here](thing). Third, this isn't your
first rodeo, and you have at least a rudimentary grasp of how the sausage gets
made--we're not launching rockets here, but we also won't be stopping
to explain what an `if` does.

# Part 1: Hello!

First, a confession: I started this project six months ago in a
hotel room in Durham, NC to get my head around Compojure and a few
other things. Then I put it down for a while, and some of the orginal
libraries I used got stale. I've updated them for the purpose of this
post, but you might notice some discrepancy between the 'early code',
and the final product. Work with me, here.

Let's start by saying that building everything from scratch makes
my head hurt. Let's start simply by getting something working we can
build off of with a minimum of fuss.

## Lein, and the `project.clj` file

I begin this little trip using
@technomancy's [lein-heroku template](https://github.com/technomancy/lein-heroku):

    lein new heroku crisco

My intention was to deploy a first version to Heroku (which I did) with
something basic to build on. Our lein template gives us a good headstart
and a project structure to play with, beginning with the `project.clj`
file:

```clojure
(defproject crisco "1.0.0-SNAPSHOT"
  :description "a project for url shortening"
  :url "http://github.com/bvandgrift/crisco"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.8"]
                 [ring/ring-jetty-adapter "1.2.2"]
                 [ring/ring-devel "1.2.2"]
                 [environ "0.5.0"]]
  :min-lein-version "2.0.0"
  :plugins [[environ/environ.lein "0.2.1"]
            [lein-ring "0.8.0"]]
  :hooks [environ.leiningen.hooks]
  :uberjar-name "crisco-standalone.jar"
  :profiles {:production {:env {:production true}}}
  :ring {:handler crisco.web/app})
```

The `project.clj` file tells lein everything it needs to know to load
dependencies, compile, jar, and eventually deploy your project. The
[leiningen docs]() do a fair job of letting you know what's what, but let's
talk about what's in front of us.

This is, naturally, a clojure file that defines a project (`defproject`).
Based on the contents of the map-like configuration structure, you can
tell `lein` to do different things.

The `:dependencies` keyword contains a vector of vectors, each of which
contains a dependency we'll require. Those you see listed here were generated
by `lein-heroku` when I started up, as were a few others I've yanked because
we won't need them. We require clojure of course, and then:

* `ring` is a popular framework for building web apps in clojure. It abstracts
  the dirty HTTP details, much like Rack does in ruby.
* `compojure` is a lightweight routing abstraction that sits on top of `ring`.
  It is similar in some respects to Sinatra.
* `environ` is a library for incorporating a cascade of configuration files.
  We won't be using that up front, but I'm leaving it in because odds are good
  we'll need it before long. It's included with heroku's loadout since
  heroku wants us to store everything in environment variables.

There's some housekeeping with `:min-lein-version` and `:uberjar-name`.

The `:plugins` keyword indicates which `lein` plugins we'll be taking
advantage of. You see `environ` in there again, but also `lein-ring`.
[Lein-ring]() will give us a few tools while developing that we'll find
useful. In particular, it lets us run a web server that keeps current
with changes to our files.

We can set up `:profiles` with different runtime configurations; that'll
be important when we're running in a production environment.

Finally, `:ring` contains the `lein-ring` plugin configuration. Right now,
we just need to let it know what handler it will be using to handle incoming
requests. `{:handler crisco.web/app}` is what the template handed us, so
let's work with that.

## web.clj: Let's Begin

Our template created this file for us. It has more than we need
at the moment, but let's look at the moving parts:

```clojure
(ns crisco.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.params :as p]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]))
```

We define our namespace with `ns crisco.web`, and require a few libraries
to help us along. I'm only display the libraries we strictly need.

* `compojure.core/handler/route` are the elements from compojure we'll
  be using to keep our routing reasonable.
* `clojure.java.io` makes an appearance in case we want to read any
  files. Spoiler alert: we do.
* `ring.middleware.stacktrace/params` allow us reasonable stack traces when
  something explodes, and the ability to cleanly handle http params,
  respectively.
* `ring.adapter.jetty` interacts with `jetty`. We'll need this when we
  want to deploy somewhere or run a standalone server.
* `environ.core` gives us access to our environment.

Next up are the routes we'll be using. This next section is primarily
what compojure brings to the party. Without compojure to handle the
routing for ring---well, let's just say this is much nicer.

```clojure
(defroutes app
  (GET "/" []
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (pr-str ["Hello" :from 'Heroku])})
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))
```

So, we get 'say hello', and anything else is a 404. Actually, ANY
route gives us a leg up on creating a real index page. Let's replace
the `GET "/"` route:

```clojure
(GET "/" []
     {:status 200
      :headers {"Content-Type" "text/plain"}
      :body (slurp (io/resource "index.html"))})
```

This will read into the response body the contents of the `index.html` file
in the `resources/` directory. `io/resource` attaches a stream reader to
the file system, and `slurp` reads everything into a string. We could've
also done:

```clojure
(GET "/" []
     {:status 200
      :headers {"Content-Type" "text/html"}
      :body (-> "index.html"
                io/resource
                slurp)})
```

That might be more idiomatic, but it's a short enough list that I don't
think we lose anything by leaving it inline.

What compojure is doing behind the scenes here is creating handler
functions that review the incoming requests and dispatch them appropriately.
It will also make our params easy to integrate, once we have a few.

Moving on, `wrap-error-page` does what you think: it catches any exceptions
thrown by our handlers.

Next up, `wrap-app [app]` applies all the ring wrappers an app wants to
use to the 'app' handler, created by `defroutes`. Remember that we used
a heroku template for this, and heroku will run using jetty, as defined in
the `-main` function. This is great if we're running that way, but since we
won't be bootstrapping that way while developing, we'll need another way
to get everything tied in. It's not incredibly important at the moment, but
you should be aware of it.

Finally, `-main`. The '-' before the method name means that it's a static
method (obviously), and as such can be run as a standalone application by
the JVM. This runs jetty on the port we specify (or 5000), and sets up the handler
function that compojure created with `defroutes` to respond to all requests.

All in all, this file doesn't do much that's related to the application
itself---it's mostly focused on setting up the structure the application
can run in.  Now that we're at the bottom of it, let's fire it up. From
your project directory:

    lein ring server-headless

The first time you run this (or `lein deps`), the project's dependencies
are downloaded, and then your service starts on `http://localhost:3000`.
Assuming you remembered to actually create a `index.html` file in your
`resources/` directory, you should be able to view it in the browser.

The `lein ring server-headless` task we get with the`lein-ring` plugin that
we set up in our `project.clj` file. That's the shortest distance to
getting to work. Another alternative would be `lein run` or `lein trampoline
run`, but they require adding `:main crisco.web` to the end of your
`project.clj` file.

Are you following along? Want to check in and see how you're doing? You
can find the code at this point tagged with 'clean-slate' in the
[crisco-compojure](https://github.com/bvandgrift/crisco-compojure/tree/clean-slate)
github repo.

## Data API, Version 1

They grow up so fast. Okay, first, we want to be able to relate a
*slug*, that is, a shortened url, to a url target. Our goal is for
`http://localhost:3000/gh` to hit `http://github.com`. The best way
to do this in the proper HTTP world is to issue a status of 301, along with
a "Location" header set with the new location.  Something like:

<pre>
HTTP/1.1 301 Moved Permanently
Date: Wed, 28 May 2014 10:46:01 GMT
Location: http://github.com
Content-Length: 0
Server: Jetty(7.6.8.v20121106)
</pre>

Easy enough; we'll add that route to our `defproject` in `web.clj`:

```clojure
(GET "/gh" []
     {:status 301
      :headers {"Location" "http://github.com"}})
```

Github isn't the only shortened url we want, however. We'd like to be
able to start the server with a few, then add to them.

Let's consider our data options: each slug maps uniquely
to a target url. As such, a map `{}` would work nicely. If we wanted to
keep track of the number of times a particular slug has been visited (listed
in our goals from above), then we can't just use a simple map, we need
a nested data structure:

```clojure
(def urls (atom {:gh {:target "http://github.com" :redirects 2}
                 :gg {:target "http://google.com" :redirects 1}}))
```

While we don't expect to be running any heavily-loaded operations in
development, we should still do the right thing and prepare our data
for concurrent access. For that, we'll use an `atom`.

We could go the extra mile and define a `record` for our slugs, but that
might be overkill, and we'd still need a hash for easy access:

```clojure
(defrecord Slug [slug target redirects])
(def urls (atom {
  :gh (->Slug :gh "http://github.com" 2)
  :gg (->Slug :gg "http://google.com" 2)
  }))
```

Let's stick with a map. Now, what functions will be operating on
our list of urls?

* given a slug and a target, if the slug isn't already used, then
  create an entry in our urls list with 0 redirects. otherwise, bail.
* given a slug, retrieve its target and update its redirect count
* there is no third thing

Easy enough! To store the slug in our list `store-slug! [slug target]` will
do. The bang(!) at the end of the function indicates we'll be making a change.
For the redirect, `request-redirect! [slug]` seems right. Again, we're changing
our application state, so the bang is recommended.

What we've described so far actually has nothing to do with web *anything*,
which seems to beg for its own namespace. Let's call it `crisco.data`, since
we know we'll be adding a persistence mechanism later. Here's what it might
look like:

```clojure
(ns crisco.data)

(def ^:private urls (atom {:gh {:target "http://github.com" :redirects 0}
                 :gg {:target "http://google.com" :redirects 0}}))

(defn- get-target [slug]
  (get-in @urls [(keyword slug) :target]))

(defn store-slug! [slug target]
  (if-not (get-target slug)
    (swap! urls #(assoc %1 (keyword slug) {:target target :redirects 0}))))

(defn request-redirect! [slug]
  (when-let [target (get-target slug)]
    (swap! urls #(update-in %1 [(keyword slug) :redirects] (fnil inc 0)))
    target))
```

I've added a private function (denoted by the '-' after `defn`, obviously)
called `get-target`, since we are using that functionality twice. I've also
made `urls` private, since we should only be interacting with it via our data
API.

We can test things out using `lein repl`:

```repl
crisco.web=> (require '[crisco.data :as data])
nil
crisco.web=> (data/request-redirect! "gh")
"http://github.com"
crisco.web=> (data/store-slug! "me" "http://ben.vandgrift.com")
{:gh {:redirects 1, :target "http://github.com"},
 :gg {:redirects 0, :target "http://google.com"},
 :me {:target "http://ben.vandgrift.com", :redirects 0}}
crisco.web=> (data/request-redirect! "me")
"http://ben.vandgrift.com"
```

## Using the Data API from web.clj

Let's tie this in to some web functionality. In `web.clj`, we'll add our
data api to the required list:

```clojure
(ns crisco.web
  (:require ;; ...
            [crisco.data :as data]))
```

Next we'll drop two new routes into our routes list:

```clojure
(GET "/:slug" [slug]
     {:status 301
      :headers {"Location" (data/request-redirect! [slug])}})
(POST "/shorten/:slug" [slug target]
      (if (data/store-slug! slug target)
        {:status 200}
        {:status 409}))
```

In order to properly parse params when running `lein ring`, we need
to wrap our app handler in `wrap-params`. First, we change `defroutes app`
to `defroutes routes`, then add a function to do the wrapping, returning the
modified app handler:

```clojure
(def app (-> routes
             p/wrap-params))
```

And that's it. Run `lein ring server-headless` and off you go. We don't
have a UI yet, so we'll be using curl to post new slugs:

    curl -i -d target=http://ben.vandgrift.com http://localhost:3000/shorten/bv

Looks like everything's working a-ok.
[Following along?](https://github.com/bvandgrift/crisco-compojure/tree/add-data-api)

From here we could go a number of directions, but if build any more of
our data API before we persist this information, we'll need to rework some
of what we do. In the interest of efficiency, let's move on to our persistence
layer.

## Persistence

It'd be nice to keep those links around after the server shuts down, no?

### The Case for Datomic

In this project, we'll be using Datomic as our data store. Here's why:

* The lookup data will be sparse, and having a bunch of `NULL`s laying around
  a data store seems sloppy to me.
* We're not going to be throwing anything away. This is something Datomic does
  extremely well.
* We'll be creating time-based reports on this data. Datomic will let us
  do that without hassling with roll-up tables or any janky warehousing
  techniques.
* SQL? JDBC from Clojure has traditionally been a shit show,
  and I haven't felt like waiting for it to sort itself out.
* redis? I love redis, but it's not great at storing potentially large
  information indefinitely.
* DynamoDB? Absolutely. We'll be using that (eventually) as Datomic's
  data store. Even if it weren't a better fit generally, Datomic's aggregate
  approach to reads makes our DynamoDB connection more efficient and drops
  the bill.
* Datalog is pretty neat, and lets us do neat things.
* We can get started, and even get pretty far without configuring a database
  of any kind, just using the in-memory Datomic API. Admittedly, this is
  something of a softball reason, as if you're anything like me you already
  have databases coming out of every available port on your system, using one
  would involve no setup at all.
* It's free to start with, and I doubt this application will require purchasing
  a supported license. If for some reason it did, it's entirely possible that
  switching data stores would be easier than figuring out Datomic's pricing
  model.

So, let's get back to the entities we're looking to store.

### Entities: Slug and Lookup

First, the slug. For each slug we
want to use as a shortened url, we have two basic properties: the *slug*, a
string, and the *target*, also a string. Either of these could be validated,
but we're not going to worry about that right now. There is a third property
we're interested in---*redirects*---but rather than store it as a number on
our slug, we'll be calculating it based on the number of lookups we've seen.

The other entity we care about holds the properties of a particular lookup.
In particular, we want the date/time, the slug requested, the IP address
of the requester, and the referring site. If we were to create a record,
it might look like:

```clojure
(defrecord Lookup [datetime slug ip-address referrer])
```

There's a relationship here that a flat record won't capture. (Yes, I know
that the lookups could be nested inside a Slug record, but then that Slug
would start to become less efficient.) If you're coming from Rails, you
might say that the Lookup belongs-to the Slug.

As we move from a map to a Datomic entity, we also have to be cognizant of
the type of data we'll be storing since Datomic's attributes are typed. With a
couple of exceptions, these should be straightforward:

* slug
  * slug: string
  * target: string
  * lookups?: *reference*
* lookup
  * when: *instant*
  * source-ip: string
  * referrer: string
  * slug: *reference*

The two things that may be unfamiliar here are *instant* and *reference*.

An `instant` is a point in time; we can extract it into the date/time format of our
choice, but it's simply a count of the milliseconds from midnight, January
1, 1970 UTC as far as Datomic is concerned.

A `reference` stores the entity-id of the related slug. This will make more
sense as we build a schema.

### Creating a Schema

In Datomic, the schema is data.  Also, data isn't stored in tables, but in
attributes. This is great for sparse data, which is what we'd like. Each entity
attribute is defined separately and has its own properties.

### On Partitions





# Q&A

If you have questions, tweet/email/etc.

*"Why are you writing another tutorial about building web apps in clojure?"*

People keep asking me how, so either the existing tutorials are
hard to find, or they're not answering the questions in a way the audience
understands.

*"Why Datomic and not another database/data store/kv store...?"*

Two reasons: First, learning Datomic became a requirement of my job
function. Turns out it's good at a few things, one of which being the
kind of data this app will be generating.

*"Why are you using ABC library instead of XYZ library?"*

The tech chosen at particular stages of development are those that appeal
to me, enjoy widespread use, and are well-documented enough to help you
out if (when) I forget to explain things.

*"do you have something against capital letters?"*

I studied (among other things) poetry in college, and went through
an e e cummings phase. My default writing style doesn't include
capitalization. For you, dear reader, I'm making an effort.

*"Is your code idiomatic?"*

Probably not, but I find the quirks of Clojure's idiomacy irritating
and hard to read.

*"Do you speak for your employer?"*

Don't be silly, this is the Internet. If it was their opinion,
it would be on their website.
