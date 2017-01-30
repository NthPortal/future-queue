package com.nthportal.collection.concurrent

import java.util.function.UnaryOperator

package object _future_queue {
  @inline
  private[concurrent] def unaryOp[A](f: A => A): UnaryOperator[A] = new UnaryOperator[A] {
    override def apply(a: A) = f(a)
  }
}
