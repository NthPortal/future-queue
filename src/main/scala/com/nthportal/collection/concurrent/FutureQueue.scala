package com.nthportal.collection.concurrent

import java.util.concurrent.atomic.AtomicReference

import com.nthportal.collection.concurrent.FutureQueue._

import scala.collection.GenSeq
import scala.collection.immutable.Queue
import scala.concurrent.{Future, Promise}
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
    * Appends an element to this queue.
    *
    * @param a the element to append
    */
  def enqueue(a: A): Unit = {
    val cs = atomic.getAndUpdate(c => {
      if (c.promises.nonEmpty) c.copy(promises = c.promises.tail)
      else c.copy(elems = c.elems :+ a)
    })

    if (cs.promises.nonEmpty) cs.promises.head.success(a)
  }

  /**
    * Appends elements to this queue.
    *
    * @param xs the elements to append
    */
  def enqueue[B <: A](xs: TraversableOnce[B]): Unit = enqueueImpl(xs.to[GenSeq])

  private def enqueueImpl[B <: A](xs: GenSeq[B]): Unit = {
    val cs = atomic.getAndUpdate(c => {
      c.copy(elems = c.elems ++ xs.drop(c.promises.length), promises = c.promises.drop(xs.length))
    })

    cs.promises.zip(xs)
      .map { case (p, e) => p.success(e) }
  }

  /**
    * Appends an element to this queue.
    *
    * @param a the element to append
    * @return this queue
    */
  def +=(a: A): FutureQueue[A] = {enqueue(a); this}

  /**
    * Appends elements to this queue.
    *
    * @param xs the elements to append
    * @return this queue
    */
  def ++=[B <: A](xs: TraversableOnce[B]): FutureQueue[A] = {enqueue(xs); this}

  /**
    * Returns a [[Future]] containing the next element, and removes that element
    * from this queue. If this queue contains no elements, the Future will be
    * completed when more elements are added to the queue; an element is said
    * to be "promised" by that Future. Futures returned by this method are
    * completed by enqueued elements in the order that the elements were promised.
    *
    * @return Returns a Future (eventually) containing the next element in this queue
    */
  def dequeue: Future[A] = {
    val p = Promise[A]()

    val cs = atomic.getAndUpdate(c => {
      if (c.elems.nonEmpty) c.copy(elems = c.elems.tail)
      else c.copy(promises = c.promises :+ p)
    })

    if (cs.elems.nonEmpty) p.success(cs.elems.head)

    p.future
  }

  override def hashCode(): Int = contentsHashCode(contents)

  override def equals(other: Any): Boolean = other match {
    case that: FutureQueue[_] => contentsEquals(this.contents, that.contents)
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

/**
  * Companion object for [[FutureQueue]].
  */
object FutureQueue {

  /**
    * An object containing an implicit conversion from [[FutureQueue]] to [[Queue]].
    */
  object Implicits {
    /**
      * An implicit conversion from [[FutureQueue]] to [[Queue]].
      * @param fq the FutureQueue to convert
      * @return a Queue
      */
    implicit def FutureQueueToQueue[A](fq: FutureQueue[A]): Queue[A] = fq.queued
  }

  private case class Contents[A](elems: Queue[A], promises: Queue[Promise[A]])

  private def contentsEquals(a: Contents[_], b: Contents[_]): Boolean = {
    a.elems == b.elems && a.promises.length == b.promises.length
  }

  private def contentsHashCode(c: Contents[_]): Int = (c.elems, c.promises.length).hashCode()

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


}
