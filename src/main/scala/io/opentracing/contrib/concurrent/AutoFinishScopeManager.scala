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

import io.opentracing.{ScopeManager, Span}

class AutoFinishScopeManager extends ScopeManager {
  private[concurrent] val tlsScope = new ThreadLocal[AutoFinishScope]

  override def activate(span: Span, finishOnClose: Boolean): AutoFinishScope = {
    new AutoFinishScope(this, new AtomicInteger(1), span)
  }

  override def activate(span: Span): AutoFinishScope = {
    activate(span, false)
  }

  override def active: AutoFinishScope = {
    tlsScope.get
  }

  override def activeSpan: Span = {
    Option(tlsScope.get).map(_.span).orNull
  }

  private[concurrent] def captureScope: AutoFinishScope#Continuation = {
    Option(tlsScope.get).map(_.capture).orNull
  }
}
