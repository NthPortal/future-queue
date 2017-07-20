package com.nthportal.collection.concurrent

import java.util.concurrent.atomic.AtomicReference

import com.nthportal.collection.concurrent.FutureQueue._
import com.nthportal.collection.concurrent._future_queue._

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions

/**
  * A queue which returns Futures for elements which may not have been enqueued yet.
  *
  * @param initialContents the initial contents of this queue
  * @tparam A the type of the contents of this queue
  */
final class FutureQueue[A] private(initialContents: Contents[A]) {

  private val atomic = new AtomicReference(initialContents)

  @inline
  private def contents = atomic.get()

  /**
    * Returns the size of this queue. Returns a negative number if there are
    * no elements queued and there are [[dequeue promised elements]] which
    * are not completed.
    *
    * @return the size of this queue
    */
  def size: Int = {
    val c = contents
    c.elems.length - c.promises.length
  }

  /**
    * Returns the number of elements [[dequeue promised by dequeue]] which have
    * not yet been completed.
    *
    * @return the number of promised elements in this queue
    */
  def promiseCount: Int = contents.promises.length

  /**
    * Returns the elements currently in this queue. Returns an empty [[Queue]]
    * if there are no elements or [[dequeue promised elements]] in this queue.
    *
    * @return the elements currently in the queue
    */
  def queued: Queue[A] = contents.elems

  /**
    * Append an element to this queue.
    *
    * @param a the element to append
    * @return this queue
    */
  def +=(a: A): FutureQueue.this.type = {
    val cs = atomic.getAndUpdate(unaryOp(c => {
      if (c.promises.nonEmpty) c.copy(promises = c.promises.tail)
      else c.copy(elems = c.elems :+ a)
    }))

    if (cs.promises.nonEmpty) cs.promises.head.success(a)

    this
  }

  /**
    * Append elements to this queue.
    *
    * @param xs the elements to append
    * @return this queue
    */
  def ++=(xs: TraversableOnce[A]): FutureQueue.this.type = {enqueue(xs.toSeq: _*); this}

  /**
    * Append an element to this queue.
    *
    * @param a the element to append
    */
  def enqueue(a: A): Unit = this += a

  /**
    * Append elements to this queue.
    *
    * @param xs the elements to append
    */
  def enqueue(xs: A*): Unit = {
    val len = xs.length
    if (len == 1) this += xs.head
    else if (len > 1) {
      val cs = atomic.getAndUpdate(unaryOp(c => {
        c.copy(
          elems = c.elems ++ xs.view.drop(c.promises.length),
          promises = c.promises.drop(len))
      }))

      cs.promises.zip(xs)
        .map { case (p, e) => p.success(e) }
    }
  }

  /**
    * Append elements to this queue.
    *
    * @param xs the elements to append
    */
  @deprecated("use `enqueue(A*)` or `++=` instead", since = "1.2.0")
  def enqueue(xs: TraversableOnce[A]): Unit = enqueue(xs.toSeq: _*)

  /**
    * Returns a [[Future]] containing the next element, and removes that element
    * from this queue. If this queue contains no elements, the Future will be
    * completed when more elements are added to the queue; an element is said
    * to be "promised" by that Future. Futures returned by this method are
    * completed by enqueued elements in the order that the elements were promised.
    *
    * @return Returns a Future (eventually) containing the next element in this queue
    */
  def dequeue(): Future[A] = {
    val p = Promise[A]()

    val cs = atomic.getAndUpdate(unaryOp(c => {
      if (c.elems.nonEmpty) c.copy(elems = c.elems.tail)
      else c.copy(promises = c.promises :+ p)
    }))

    if (cs.elems.nonEmpty) p.success(cs.elems.head)

    p.future
  }

  /**
    * Dequeues elements from this queue as they are added, and applies them to the
    * specified function.
    *
    * One SHOULD NOT dequeue elements from this queue after calling this method or
    * [[drainContinuallyTo]]; doing so will result in only some elements being
    * applied to the specified function, in an inconsistent fashion. For the same
    * reason, neither this method nor [[drainContinuallyTo]] (nor
    * [[FutureQueue.aggregate]] with `this` as an argument) should be invoked after
    * calling this method.
    *
    * @param f        a function executed with each element added to this queue
    *                 (forever); its return value is ignored
    * @param executor the [[ExecutionContext]] used to execute the callbacks
    * @tparam B only used to accept any return type from the callback function
    */
  def drainContinually[B](f: A => B)(implicit executor: ExecutionContext): Unit = {
    @inline
    def loop(future: Future[A]): Unit = future.onComplete { t =>
      f(t.get)
      loop(dequeue())
    }

    loop(dequeue())
  }

  /**
    * Dequeues elements from this queue as they are added, and enqueues them to the
    * specified `FutureQueue`.
    *
    * One SHOULD NOT dequeue elements from this queue after calling this method or
    * [[drainContinually]]; doing so will result in only some elements being
    * enqueued to the specified queue, in an inconsistent fashion. For the same
    * reason, neither this method nor [[drainContinually]] (nor
    * [[FutureQueue.aggregate]] with `this` as an argument) should be invoked after
    * calling this method.
    *
    * @param other    the queue to which to enqueue elements added to this queue
    * @param executor the [[ExecutionContext]] used to enqueue elements to the other
    *                 queue
    * @tparam B the type of the elements in the other queue
    * @throws IllegalArgumentException if the specified `FutureQueue` is `this`
    */
  @throws[IllegalArgumentException]
  def drainContinuallyTo[B >: A](other: FutureQueue[B])(implicit executor: ExecutionContext): Unit = {
    require(this ne other, "Cannot drain a queue to itself")
    drainContinually(other.enqueue)
  }

  /**
    * @see [[drainContinuallyTo]]
    */
  @deprecated("use `drainContinuallyTo` instead", since = "1.2.0")
  def drainToContinually[B >: A](other: FutureQueue[B])(implicit executor: ExecutionContext): Unit = drainContinuallyTo(other)

  override def hashCode(): Int = contents.hashCode()

  /**
    * Returns `true` if `this` and `other` are equal.
    *
    * `this` and `other` are equal only if `other` is a `FutureQueue`,
    * and the `FutureQueue`s are equal. Two `FutureQueue`s are equal
    * if they have the same queued elements, and no [[dequeue promised elements]].
    *
    * @param other the other object
    * @return `true` if `this` and `other` are equal; `false` otherwise
    */
  override def equals(other: Any): Boolean = other match {
    case that: FutureQueue[_] => this.contents == that.contents
    case _ => false
  }

  override def toString: String = s"FutureQueue($contentsToString)"

  @inline
  private def contentsToString: String = {
    val c = contents
    if (c.promises.nonEmpty) s"promised: ${c.promises.length}"
    else if (c.elems.nonEmpty) s"queued: ${c.elems.mkString("(", ", ", ")")}"
    else "empty"
  }
}

/**
  * Companion object for [[FutureQueue]].
  */
object FutureQueue {

  /**
    * An object containing an implicit conversion from [[FutureQueue]] to [[Queue]]
    * (deprecated).
    *
    * The implicit conversion is deprecated because it hides the mutability of the
    * underlying `FutureQueue`. It could lead to invoking multiple methods from
    * `Queue` on a `FutureQueue` and expecting them to be invoked on the same
    * collection, which is not guaranteed.
    */
  @deprecated("convert to Queue explicitly instead", since = "1.1.0")
  object Implicits {
    /**
      * An implicit conversion from [[FutureQueue]] to [[Queue]].
      * @param fq the FutureQueue to convert
      * @return a Queue
      */
    implicit def FutureQueueToQueue[A](fq: FutureQueue[A]): Queue[A] = fq.queued
  }

  private case class Contents[A](elems: Queue[A], promises: Queue[Promise[A]])

  private val emptyContents = Contents[Nothing](Queue.empty, Queue.empty)

  /**
    * Creates an empty FutureQueue.
    *
    * @return an empty FutureQueue
    */
  def empty[A]: FutureQueue[A] = new FutureQueue(emptyContents.asInstanceOf[Contents[A]])

  /**
    * Creates an empty FutureQueue.
    *
    * @return an empty FutureQueue
    */
  def apply[A](): FutureQueue[A] = empty

  /**
    * Creates a FutureQueue with the specified elements.
    *
    * @param elems the elements of the created FutureQueue
    * @return a FutureQueue containing the specified elements
    */
  def apply[A](elems: Queue[A]): FutureQueue[A] = new FutureQueue(Contents(elems = elems, promises = Queue.empty))

  /**
    * Creates a FutureQueue with the specified elements.
    *
    * @param elems the elements of the created FutureQueue
    * @return a FutureQueue containing the specified elements
    */
  def apply[A](elems: A*): FutureQueue[A] = apply(Queue(elems: _*))

  /**
    * Creates a new `FutureQueue` ('aggregate queue') to which all of the
    * specified `FutureQueue`s ('input queues') are
    * [[FutureQueue.drainContinuallyTo drained]].
    *
    * Elements added to the input queues are dequeued from them and enqueued
    * to the aggregate queue. Consequently, one SHOULD NOT invoke
    * [[FutureQueue.dequeue dequeue]],
    * [[FutureQueue.drainContinually drainContinually]] or
    * [[FutureQueue.drainContinuallyTo drainContinuallyTo]] on the input queues,
    * or this method with any of the input queues as an argument, after calling
    * this method; doing so will result in only some elements being enqueued
    * to the aggregate queue, in an inconsistent fashion.
    *
    * Ordering is guaranteed to be maintained for elements added to a single
    * input queue relative to each other. However, ordering of elements added
    * to different input queues is not guaranteed relative to each other; they
    * are enqueued into the aggregate queue only roughly in the order that they
    * are enqueued into the input queues.
    *
    * @param queues   the `FutureQueue`s to aggregate into a single `FutureQueue`
    * @param executor the [[ExecutionContext]] used to aggregate the queues
    * @tparam A the type of the resulting `FutureQueue`
    * @return a `FutureQueue` which aggregates elements added to the specified
    *         `FutureQueue`s
    */
  def aggregate[A](queues: FutureQueue[_ <: A]*)(implicit executor: ExecutionContext): FutureQueue[A] = {
    val res = empty[A]
    queues.foreach(_.drainContinuallyTo(res))
    res
  }
}
