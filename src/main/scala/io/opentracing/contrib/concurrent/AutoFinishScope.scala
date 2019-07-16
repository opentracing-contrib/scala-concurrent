package io.opentracing.contrib.concurrent

import java.util.concurrent.atomic.AtomicInteger

import io.opentracing.{Scope, Span}

private[concurrent] class AutoFinishScope(manager: AutoFinishScopeManager, refCount: AtomicInteger, wrapped: Span) extends Scope {
  private val toRestore: AutoFinishScope = manager.tlsScope.get

  def capture: AutoFinishScope#Continuation = new AutoFinishScope#Continuation

  override def close(): Unit = {
    if (this.manager.tlsScope.get eq this) {
      if (this.refCount.decrementAndGet == 0) {
        this.wrapped.finish()
      }
      this.manager.tlsScope.set(toRestore)
    }
  }

  override def span: Span = wrapped

  class Continuation() {
    refCount.incrementAndGet

    def activate() = new AutoFinishScope(manager, refCount, wrapped)
  }

}
