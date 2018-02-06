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
package io.opentracing.contrib.concurrent.examples

import io.opentracing.contrib.concurrent.TracedAutoFinishExecutionContext
import io.opentracing.{Scope, Tracer}

import scala.concurrent.{ExecutionContext, Future}


class AutoFinishExample extends App {

  val tracer: Tracer = ??? // Set your tracer here, and make sure AutoFinishScopeManager is used.
  val ec: ExecutionContext = new TracedAutoFinishExecutionContext(ExecutionContext.global, tracer)

  // Span will be propagated and finished when all the 3 tasks are done.
  val scope: Scope = tracer.buildSpan("one").startActive(true)

  try {
    Future {
      tracer.scopeManager().active().span().setTag("server1.result", 7)
      true
    }(ec)

    Future {
      Future {
        tracer.scopeManager().active().span().setTag("server3.result", 54)
        true
      }(ec)
      tracer.scopeManager().active().span().setTag("server2.result", 37)
      true
    }(ec);
  } finally {
    scope.close()
  }

}
