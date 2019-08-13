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

import io.opentracing.util.GlobalTracer
import io.opentracing.{Span, Tracer}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TracedExecutionContext(ec: ExecutionContext, tracer: Tracer, createSpans: Boolean) extends ExecutionContextExecutor {
  def this(ec: ExecutionContext) = this(ec, GlobalTracer.get(), false)

  def this(ec: ExecutionContext, tracer: Tracer) = this(ec, tracer, false)

  if (ec == null) throw new IllegalArgumentException("ec")
  if (tracer == null) throw new IllegalArgumentException("tracer")

  override def prepare(): ExecutionContext = {
    if (!createSpans && tracer.scopeManager.activeSpan() == null) ec else new TracedExecutionContextImpl
  }

  override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)

  override def execute(command: Runnable): Unit = ec.execute(command)

  class TracedExecutionContextImpl extends ExecutionContextExecutor {

    val activeSpan: Span = if (createSpans) tracer.buildSpan(Constants.EXECUTE_OPERATION_NAME).start()
    else tracer.scopeManager.activeSpan()

    override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)

    override def execute(command: Runnable): Unit = {
      ec.execute(new Runnable {
        override def run(): Unit = {
          // Only deactivate the active Span if we created/own it.
          val scope = tracer.scopeManager.activate(activeSpan)
          try {
            command.run()
          } finally {
            scope.close()
            if (createSpans) {
              activeSpan.finish()
            }
          }
        }
      })
    }
  }

}
