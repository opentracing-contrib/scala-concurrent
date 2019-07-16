package io.opentracing.contrib.concurrent

import java.util.concurrent.atomic.AtomicInteger

import io.opentracing.{ScopeManager, Span}

private[concurrent] class AutoFinishScopeManager extends ScopeManager {
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

  def captureScope: AutoFinishScope#Continuation = {
    Option(tlsScope.get).map(_.capture).orNull
  }
}
