/*
 * Copyright 2018-2019 The OpenTracing Authors
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

import java.util.Random
import java.util.concurrent.{Callable, TimeUnit}

import io.opentracing.Span
import io.opentracing.mock.MockTracer
import io.opentracing.mock.MockTracer.Propagator
import org.awaitility.Awaitility.await
import org.hamcrest.core.IsEqual.equalTo
import org.scalatest.{BeforeAndAfter, FunSuite}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

class TracedAutoFinishExecutionContextTest extends FunSuite with BeforeAndAfter {
  val mockTracer = new MockTracer(new AutoFinishScopeManager, Propagator.TEXT_MAP)

  before {
    mockTracer.reset()
  }

  test("testIllegalContext") {
    intercept[IllegalArgumentException] {
      new TracedAutoFinishExecutionContext(null, mockTracer)
    }
  }

  test("testIllegalTracer") {
    intercept[IllegalArgumentException] {
      new TracedAutoFinishExecutionContext(ExecutionContext.global, null)
    }
  }

  test("testSimple") {
    val ec = new TracedAutoFinishExecutionContext(ExecutionContext.global, mockTracer)
    val scope = mockTracer.buildSpan("one").startActive(true)

    var span: Span = null
    var f: Future[Span] = null
    try {
      span = scope.span
      f = Future[Span] {
        val activeScope = mockTracer.scopeManager.active
        val activeSpan = activeScope.span
        activeSpan.setTag("done", true)
        activeSpan
      }(ec)
    } finally {
      scope.close()
    }

    val result = Await.result(f, Duration(15, TimeUnit.SECONDS))
    await.atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(mockTracer), equalTo(1))
    assert(span == result)
    assert(1 == mockTracer.finishedSpans.size)

    val mockSpan = mockTracer.finishedSpans.get(0)
    assert("one" == mockSpan.operationName)
    assert(mockSpan.tags.get("done") === true)

  }

  test("testMultiple") {
    val ec = new TracedAutoFinishExecutionContext(ExecutionContext.global, mockTracer)
    val futures: ListBuffer[Future[Span]] = ListBuffer()
    val rand: Random = new Random
    val scope = mockTracer.buildSpan("one").startActive(true)

    try
      for (_ <- 0 until 5) {
        futures += Future[Span] {
          val sleepMs: Int = rand.nextInt(500)
          Thread.sleep(sleepMs)

          val activeSpan: Span = mockTracer.scopeManager.active.span
          assert(activeSpan != null)
          activeSpan.setTag(Integer.toString(sleepMs), true)
          activeSpan
        }(ec)
      }
    finally {
      scope.close()
    }

    implicit val implicitContext = ec
    Await.result(Future.sequence(futures), Duration(15, TimeUnit.SECONDS))
    await.atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(mockTracer), equalTo(1))
    assert(1 == mockTracer.finishedSpans.size)
    assert(5 == mockTracer.finishedSpans.get(0).tags.size)
  }

  test("testPipeline") {
    val ec = new TracedAutoFinishExecutionContext(ExecutionContext.global, mockTracer)
    val scope = mockTracer.buildSpan("one").startActive(true)
    var f: Future[Future[Boolean]] = null

    try {
      f = Future[Future[Boolean]] {
        assert(mockTracer.scopeManager.active != null)
        mockTracer.scopeManager.active.span.setTag("1", true)

        Future[Boolean] {
          assert(mockTracer.scopeManager.active != null)
          mockTracer.scopeManager.active.span.setTag("2", true)
          true
        }(ec)

      }(ec)
    } finally {
      scope.close()
    }

    val f2 = Await.result(f, Duration(15, TimeUnit.SECONDS))
    Await.result(f2, Duration(15, TimeUnit.SECONDS))
    await.atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(mockTracer), equalTo(1))
    assert(1 == mockTracer.finishedSpans.size)

    val mockSpan = mockTracer.finishedSpans.get(0)
    assert("one" == mockSpan.operationName)
    assert(2 == mockSpan.tags.size)
    assert(true === mockSpan.tags.get("1"))
    assert(true === mockSpan.tags.get("2"))
  }

  test("testConvert") {
    val ec = new TracedAutoFinishExecutionContext(ExecutionContext.global, mockTracer)
    val scope = mockTracer.buildSpan("one").startActive(true)
    var f: Future[String] = null

    try {
      f = Future[Int] {
        val result: Int = 1099
        mockTracer.scopeManager.active.span.setTag("before.map", result)
        result
      }(ec).map { x => {
        val result = x.toString
        mockTracer.scopeManager.active.span.setTag("after.map", result)
        result
      }
      }(ec).andThen {
        case Success(_) => mockTracer.scopeManager.active.span.setTag("error", false)
        case Failure(_: Throwable) => mockTracer.scopeManager.active.span.setTag("error", true)
      }(ec)
    } finally {
      scope.close()
    }

    Await.result(f, Duration(15, TimeUnit.SECONDS))
    await.atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(mockTracer), equalTo(1))
    assert(1 == mockTracer.finishedSpans.size)
    assert(3 == mockTracer.finishedSpans.get(0).tags.size)
  }


  private def reportedSpansSize(mockTracer: MockTracer): Callable[Int] = {
    new Callable[Int] {
      override def call(): Int = mockTracer.finishedSpans().size()
    }
  }

}
