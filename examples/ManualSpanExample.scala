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


import io.opentracing.contrib.concurrent.TracedExecutionContext
import io.opentracing.{Scope, Span, Tracer}

import scala.concurrent.{ExecutionContext, Future}


class ManualSpanExample extends App {
  val tracer: Tracer = ??? // Set your tracer here.
  val ec: ExecutionContext = new TracedExecutionContext(ExecutionContext.global, tracer)

  val span: Span = ???
  val scope: Scope = tracer.scopeManager().activate(span, false)
  try {
    val f1 = Future[Boolean] {
      // 'parent' will automatically propagated and set active.
      val childScope: Scope = tracer.buildSpan("child1").startActive(true)
      try {
        // ...
      } finally {
        childScope.close()
      }
      true
    }(ec)

    val f2 = Future[Boolean] {

      // 'parent' will automatically propagated and set active.
      val childScope = tracer.buildSpan("child2").startActive(true)
      try {
        // ...
      } finally {
        childScope.close()
      }
      true
    }(ec)

    Future.sequence(List(f1, f2))(implicitly, ec).onComplete { _ => {
      scope.span().finish()
    }
    }(ec)

  } finally {
    scope.close()
  }

}
