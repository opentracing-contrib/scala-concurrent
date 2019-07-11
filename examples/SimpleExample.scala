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

import io.opentracing.Tracer
import io.opentracing.contrib.concurrent.TracedExecutionContext

import scala.concurrent.{ExecutionContext, Future}


class SimpleExample extends App {
  val tracer: Tracer = ??? // Set your tracer here.
  val ec: ExecutionContext = new TracedExecutionContext(ExecutionContext.global, tracer)

  val scope = tracer.buildSpan("parent").startActive(false)
  try {
    Future {
      val statusCode = 201
      val scope = tracer.scopeManager().active()
      scope.span().setTag("status.code", statusCode)
      statusCode
    }(ec)
      .onComplete { _ => {
        tracer.scopeManager().active().span().finish()
      }
      }(ec)
  } finally {
    scope.close()
  }

}
