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

import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TracedAutoFinishExecutionContext(ec: ExecutionContext, tracer: Tracer) extends ExecutionContextExecutor {
  def this(ec: ExecutionContext) = this(ec, GlobalTracer.get())

  if (ec == null) throw new IllegalArgumentException("ec")
  if (tracer == null) throw new IllegalArgumentException("tracer")

  override def prepare(): ExecutionContext = {
    if (tracer.scopeManager.activeSpan() == null) ec else new TracedAutoFinishExecutionContextImpl
  }

  override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)

  override def execute(command: Runnable): Unit = ec.execute(command)


  class TracedAutoFinishExecutionContextImpl extends ExecutionContextExecutor {
    val continuation: AutoFinishScope#Continuation = {
      Option(tracer.scopeManager()) match {
        case Some(manager: AutoFinishScopeManager) => manager.tlsScope.get().capture
        case _ => throw new IllegalStateException("Usage of AutoFinishScopeManager required.")
      }
    }

    override def execute(command: Runnable): Unit = {
      ec.execute(new Runnable {
        override def run(): Unit = {
          val scope = continuation.activate()
          try {
            command.run()
          } finally {
            scope.close()
          }
        }
      })
    }

    override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
  }

}

