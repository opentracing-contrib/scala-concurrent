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

import java.util.concurrent.atomic.AtomicInteger

import io.opentracing.{Scope, Span}

class AutoFinishScope(manager: AutoFinishScopeManager, refCount: AtomicInteger, wrapped: Span) extends Scope {
  private val toRestore: AutoFinishScope = manager.tlsScope.get
  manager.tlsScope.set(this)

  private[concurrent] def capture: AutoFinishScope#Continuation = new Continuation

  override def close(): Unit = {
    if (this.manager.tlsScope.get eq this) {
      if (this.refCount.decrementAndGet == 0) {
        this.wrapped.finish()
      }
      this.manager.tlsScope.set(toRestore)
    }
  }

  override def span: Span = wrapped

  private[concurrent] class Continuation() {
    refCount.incrementAndGet

    def activate() = new AutoFinishScope(manager, refCount, wrapped)
  }

}
