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
package io.opentracing.contrib.concurrent.examples

import io.opentracing.contrib.concurrent.TracedAutoFinishExecutionContext
import io.opentracing.{Scope, Tracer}

import scala.concurrent.{ExecutionContext, Future}


class AutoFinishMapExample extends App {
  val tracer: Tracer = ??? // Set your tracer here, and make sure AutoFinishScopeManager is used.
  val ec: ExecutionContext = new TracedAutoFinishExecutionContext(ExecutionContext.global, tracer)

  // The Span will be finished once the last callback is done - that is,
  // the OnComplete invocation, thus no need to manually finish it.
  val scope: Scope = tracer.buildSpan("one").startActive(true)
  try {
    Future[Int] {
      val result: Int = 127
      tracer.scopeManager().active().span().setTag("original.value", 127)
      result
    }(ec)
      .map { x => {
        val result = x.toString
        tracer.scopeManager().active().span().setTag("final.value", result)
        result
      }
      }(ec)
      .onComplete { - => {
        tracer.scopeManager().active().span().setTag("error", false)
      }
      }(ec)
  } finally {
    scope.close()
  }

}
