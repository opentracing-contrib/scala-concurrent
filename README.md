[![Build Status][ci-img]][ci] [![Released Version][maven-img]][maven]

# OpenTracing instrumentation for `scala.concurrent`
OpenTracing instrumentation for `scala.concurrent` package.

## Installation

build.sbt
```sbt
libraryDependencies += "io.opentracing.contrib" % "opentracing-scala-concurrent" % "VERSION"
```

## Usage

Please see the examples directory. Overall, an `ExecutionContext` is wrapped
so the active Span can be captured and activated for a given Scala `Future`.

Create a `TracedExecutionContext` wrapping the actually used `ExecutionContext`,
and pass it around when creating `Future`s:

```scala
// Instantiate tracer
val tracer: Tracer = ...
val ec: ExecutionContext = new TracedExecutionContext(executionContext, tracer);
```

### Span Propagation

```scala
Future {
  // The active Span at Future creation time, if any,  
  // will be captured and restored here.
  tracer.scopeManager().active().setTag("status.code", getStatusCode())
}(ec)
```

`Future.onComplete` and other `Future` methods will
capture too *any* active `Span` by the time they were registered, so you have
to make sure that both happened under the same active `Span`/`Scope` for this
to work smoothly.

`Span` lifetime handling is not done at the `TracedExecutionContext`,
and hence explicit calls to `Span.finish()` must be put in place - usually
either in the last `Future`/message block or in a `onComplete` callback
function:

```scala
Future {  
   ...
}(ec).onComplete { _ => {
  tracer.scopeManager().active().span().finish()
}
}(ec)
```

### Auto finish Span handling

Span auto-finish is supported through a reference-count system using the specific
`AutoFinishScopeManager` -which needs to be provided at `Tracer` creation time-,
along with using `TracedAutoFinishExecutionContext`:

```scala
val scopeManager = new AutoFinishScopeManager();
val tracer: Tracer = ??? // Use the created scopeManager here.
val ec = new TracedAutoFinishExecutionContext(executionContext, tracer)
...
val scope = tracer.buildSpan("request").startActive(true)
try {
    Future {
	// Span will be reactivated here
	...
	Future {
	    // Span will be reactivated here as well.  
	    // By the time this future is done,  
	    // the Span will be automatically finished.
	  } (ec)
  } (ec)
} finally {
 scope.close()
}
```

Reference count for `Span`s is set to 1 at creation, and is increased when
registering `onComplete`, `andThen`, `map`, and similar
`Future` methods - and is decreased upon having such function/callback executed:

```scala
Future {
    ...
}(ec)
.map {...}(ec)
.onComplete {
    // No need to call `Span.finish()` here at all, as  
    // lifetime handling is done implicitly.
}(ec)
```

[ci-img]: https://travis-ci.org/opentracing-contrib/scala-concurrent.svg?branch=master
[ci]: https://travis-ci.org/opentracing-contrib/scala-concurrent
[maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-scala-concurrent.svg
[maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-scala-concurrent
