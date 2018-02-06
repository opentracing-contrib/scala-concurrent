/*
 * Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.concurrent

import java.util
import java.util.concurrent.{Callable, TimeUnit}

import io.opentracing.Span
import io.opentracing.mock.MockTracer.Propagator
import io.opentracing.mock.{MockSpan, MockTracer}
import io.opentracing.util.ThreadLocalScopeManager
import org.awaitility.Awaitility.await
import org.hamcrest.core.IsEqual.equalTo
import org.scalatest.{BeforeAndAfter, FunSuite}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

class TracedExecutionContextTest extends FunSuite with BeforeAndAfter {
  val mockTracer = new MockTracer(new ThreadLocalScopeManager, Propagator.TEXT_MAP)

  before {
    mockTracer.reset()
  }

  test("testIllegalContext") {
    intercept[IllegalArgumentException] {
      new TracedExecutionContext(null, mockTracer, false)
    }
  }

  test("testIllegalTracer") {
    intercept[IllegalArgumentException] {
      new TracedExecutionContext(ExecutionContext.global, null, false)
    }
  }

  test("testPropagation") {
    val ec = new TracedExecutionContext(ExecutionContext.global, mockTracer)
    var f: Future[Span] = null
    var span: Span = null

    val scope = mockTracer.buildSpan("one").startActive(false)


    try {
      span = scope.span
      f = Future[Span] {
        assert(mockTracer.scopeManager.active != null)
        mockTracer.scopeManager.active.span
      }(ec)
    } finally {
      scope.close()
    }


    val result = Await.result(f, Duration(15, TimeUnit.SECONDS))
    assert(span == result)
    assert(0 == mockTracer.finishedSpans.size)

    span.finish()
    assert(1 == mockTracer.finishedSpans.size)
    assert(span == mockTracer.finishedSpans.get(0))
  }

  test("testCreateSpans") {
    val ec = new TracedExecutionContext(ExecutionContext.global, mockTracer, true)
    var parentSpan: Span = null
    val scope = mockTracer.buildSpan("parent").startActive(false)
    var f: Future[Span] = null

    try {
      parentSpan = scope.span
      f = Future[Span] {
        assert(mockTracer.scopeManager.active != null)
        mockTracer.scopeManager.active.span
      }(ec)
    } finally {
      scope.close()
    }

    val span: Span = Await.result(f, Duration(15, TimeUnit.SECONDS))
    await.atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(mockTracer), equalTo(1))
    assert(1 == mockTracer.finishedSpans.size)

    parentSpan.finish()
    val finishedSpans: util.List[MockSpan] = mockTracer.finishedSpans
    assert(2 == finishedSpans.size)
    assert(parentSpan == finishedSpans.get(1))
    assert(span == finishedSpans.get(0))
    assert(finishedSpans.get(1).context.spanId == finishedSpans.get(0).parentId)

  }


  test("testNoActiveSpan") {
    val ec = new TracedExecutionContext(ExecutionContext.global, mockTracer)

    val f: Future[Boolean] = Future[Boolean] {
      assert(mockTracer.scopeManager.active == null)
      mockTracer.scopeManager.active != null
    }(ec)

    val isActive = Await.result(f, Duration(15, TimeUnit.SECONDS))
    assert(isActive === false)
    assert(0 == mockTracer.finishedSpans.size)
  }

  test("testGlobalTracer") {
    val ec = new TracedExecutionContext(ExecutionContext.global)

    val f: Future[Boolean] = Future[Boolean] {
      assert(mockTracer.scopeManager.active == null)
      mockTracer.scopeManager.active != null
    }(ec)

    val isActive = Await.result(f, Duration(15, TimeUnit.SECONDS))
    assert(isActive === false)
    assert(0 == mockTracer.finishedSpans.size)
  }

  test("testConvert") {
    val ec = new TracedExecutionContext(ExecutionContext.global, mockTracer)

    val scope = mockTracer.buildSpan("one").startActive(false)

    try {
      Future[Boolean] {
        assert(mockTracer.scopeManager.active != null)
        mockTracer.scopeManager.active.span.setTag("main", true)
        true
      }(ec).andThen {
        case Success(_) =>
          assert(mockTracer.scopeManager.active != null)
          mockTracer.scopeManager.active.span.setTag("interceptor", true)
        case Failure(_: Throwable) => fail()
      }(ec).onComplete { _ => {
        assert(mockTracer.scopeManager.active != null)
        mockTracer.scopeManager.active.span.setTag("done", true)
        mockTracer.scopeManager.active.span.finish()
      }
      }(ec)
    } finally {
      scope.close()
    }

    await.atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(mockTracer), equalTo(1))

    val finishedSpans = mockTracer.finishedSpans
    assert(1 == finishedSpans.size)

    val tags = finishedSpans.get(0).tags
    assert(3 == tags.size)
    assert(tags.containsKey("main"))
    assert(tags.containsKey("interceptor"))
    assert(tags.containsKey("done"))
  }

  private def reportedSpansSize(mockTracer: MockTracer): Callable[Int] = {
    () => mockTracer.finishedSpans().size()
  }

}
