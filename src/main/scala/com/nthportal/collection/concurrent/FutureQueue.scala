package com.nthportal.collection.concurrent

import java.util.concurrent.atomic.AtomicReference

import scala.collection.GenSeq
import scala.collection.immutable.Queue
import scala.concurrent.{Future, Promise}
import scala.language.implicitConversions

final class FutureQueue[A] private(in: Queue[A]) {

  import FutureQueue._

  private val atomic = new AtomicReference(Contents(elems = in, promises = Queue.empty))

  @inline
  private def contents = atomic.get()

  def size: Int = {
    val c = contents
    c.elems.length - c.promises.length
  }

  def promiseCount: Int = contents.promises.length

  def queued: Queue[A] = contents.elems

  def enqueue(a: A): Unit = {
    val cs = atomic.getAndUpdate(c => {
      if (c.promises.nonEmpty) c.copy(promises = c.promises.tail)
      else c.copy(elems = c.elems :+ a)
    })

    if (cs.promises.nonEmpty) cs.promises.head.success(a)
  }

  def enqueue[B <: A](xs: TraversableOnce[B]): Unit = enqueueImpl(xs.to[GenSeq])

  private def enqueueImpl[B <: A](xs: GenSeq[B]): Unit = {
    val cs = atomic.getAndUpdate(c => {
      c.copy(elems = c.elems ++ xs.drop(c.promises.length), promises = c.promises.drop(xs.length))
    })

    cs.promises.zip(xs)
      .map { case (p, e) => p.success(e) }
  }

  def +=(a: A): FutureQueue[A] = {enqueue(a); this}

  def ++=[B <: A](xs: TraversableOnce[B]): FutureQueue[A] = {enqueue(xs); this}

  def dequeue: Future[A] = {
    val p = Promise[A]()

    val cs = atomic.getAndUpdate(c => {
      if (c.elems.nonEmpty) c.copy(elems = c.elems.tail)
      else c.copy(promises = c.promises :+ p)
    })

    if (cs.elems.nonEmpty) p.success(cs.elems.head)

    p.future
  }

  override def hashCode(): Int = contents.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: FutureQueue[_] => this.contents == that.contents
    case _ => false
  }

  override def toString: String = s"FutureQueue($contentsToString)"

  private def contentsToString: String = {
    val c = contents
    if (c.promises.nonEmpty) s"promised: ${c.promises.length}"
    else if (c.elems.nonEmpty) s"queued: ${c.elems.mkString("(", ", ", ")")}"
    else "empty"
  }
}

object FutureQueue {

  object Implicits {
    implicit def FutureQueueToQueue[A](fq: FutureQueue[A]): Queue[A] = fq.queued
  }

  def apply[A](elems: Queue[A]): FutureQueue[A] = new FutureQueue(elems)

  def apply[A](): FutureQueue[A] = empty

  def apply[A](elems: A*): FutureQueue[A] = apply(Queue(elems: _*))

  def empty[A]: FutureQueue[A] = apply(Queue.empty)

  private case class Contents[A](elems: Queue[A], promises: Queue[Promise[A]])

}
