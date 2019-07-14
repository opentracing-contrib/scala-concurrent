[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov] [![Released Version][maven-img]][maven]

# OpenTracing instrumentation for `scala.concurrent`
OpenTracing instrumentation for `scala.concurrent` package.

## Installation

build.sbt
```sbt
libraryDependencies += "io.opentracing.contrib" % "opentracing-scala-concurrent_2.13" % "VERSION"
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

## License

[Apache 2.0 License](./LICENSE).

[ci-img]: https://travis-ci.org/opentracing-contrib/scala-concurrent.svg?branch=master
[ci]: https://travis-ci.org/opentracing-contrib/scala-concurrent
[cov-img]: https://coveralls.io/repos/github/opentracing-contrib/scala-concurrent/badge.svg?branch=master
[cov]: https://coveralls.io/github/opentracing-contrib/scala-concurrent?branch=master
[maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-scala-concurrent_2.13.svg
[maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-scala-concurrent_2.13
