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


class NestedExample extends App {

  val tracer: Tracer = ??? // Set your tracer here.
  val ec: ExecutionContext = new TracedExecutionContext(ExecutionContext.global, tracer)

  // Start a Span and manually finish it when the last Future/task is done.
  val scope = tracer.buildSpan("parent").startActive(false)
  try {
    Future {
      Future {
        tracer.scopeManager().active().span().setTag("step2.value", 17)
        tracer.scopeManager().active().span().finish()
        201
      }(ec)
      tracer.scopeManager().active().span().setTag("step1.value", 10)
      true
    }(ec)
  } finally {
    scope.close()
  }

}
