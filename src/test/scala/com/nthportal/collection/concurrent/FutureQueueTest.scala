package com.nthportal.collection.concurrent

import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.Queue
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

class FutureQueueTest extends FlatSpec with Matchers {

  import FutureQueueTest._

  behavior of "FutureQueue companion object"

  it should "create an empty FutureQueue" in {
    val fq = FutureQueue.empty[Any]
    fq should have size 0
    fq.queued shouldBe empty

    fq should equal(FutureQueue[Nothing]())
  }

  it should "create a FutureQueue" in {
    val q = Queue("some", "elements")
    FutureQueue(q).queued should be theSameInstanceAs q

    FutureQueue("some", "elements").queued should equal(q)
  }

  behavior of "implicit conversion from FutureQueue to Queue"

  it should "convert properly" in {
    import FutureQueue.Implicits._

    val q = Queue("some", "elements")
    val fq = FutureQueue(q)

    fq.seq should be theSameInstanceAs q
  }

  behavior of "FutureQueue"

  it should "enqueue a single element" in {
    val fq = FutureQueue.empty[String]
    val s = "test"
    (fq += s).queued should contain(s)
    fq should have size 1
  }

  it should "enqueue multiple elements" in {
    val fq = FutureQueue.empty[String]
    val list = List("some", "test", "strings")

    (fq ++= list).queued should equal(list)
    fq should have size list.size
  }

  it should "keep multiple promises" in {
    val fq = FutureQueue.empty[String]
    val list = List("some", "test", "strings")

    val futures = List.fill(3) {fq.dequeue}
    fq ++= list

    futures.foreach(_.isCompleted should be(true))
    futures.map {Await.result(_, Duration.Zero)} should equal(list)
  }

  it should "dequeue elements" in {
    val s = "test"
    val fq = FutureQueue(s)

    val f1 = fq.dequeue
    f1.isCompleted should be(true)
    Await.result(f1, Duration.Zero) should equal(s)

    val f2 = fq.dequeue
    f2.isCompleted should be(false)

    fq.enqueue(s)
    f2.isCompleted should be(true)
    Await.result(f2, Duration.Zero) should equal(s)
  }

  it should "have the correct promise count and size" in {
    val fq = FutureQueue.empty[String]
    fq should have size 0
    fq.promiseCount should be(0)

    fq += "test"
    fq should have size 1
    fq.promiseCount should be(0)

    fq.dequeue
    fq should have size 0
    fq.promiseCount should be(0)

    fq.dequeue
    fq should have size -1
    fq.promiseCount should be(1)

    fq.dequeue
    fq should have size -2
    fq.promiseCount should be(2)
  }

  it should "evaluate equality properly" in {
    FutureQueue.empty[String] shouldBeEquivalentTo FutureQueue.empty[Int]
    FutureQueue("") shouldBeEquivalentTo FutureQueue("")
    FutureQueue("") should not equal FutureQueue.empty[String]

    val fq = FutureQueue.empty[String]
    fq.dequeue
    fq should not equal FutureQueue.empty[String]

    val other = FutureQueue.empty[Int]
    other.dequeue

    fq should not equal other
    fq.queued should equal (other.queued)

    FutureQueue("") should not equal ""
  }

  it should "be represented by a sensible string" in {
    FutureQueue.empty.toString should include("empty")

    val list = List("some", "test", "strings")
    val s = FutureQueue(list).toString
    list.foreach(e => s should include(e))

    val fq = FutureQueue.empty
    fq.dequeue
    fq.dequeue
    fq.toString should include(fq.promiseCount.toString)
  }
}

private object FutureQueueTest extends Matchers {

  implicit final class RichFutureQueue[A](private val a: FutureQueue[A]) extends AnyVal {
    def shouldBeEquivalentTo(b: FutureQueue[_]): Unit = {
      a should equal(b)
      a.hashCode() should equal(b.hashCode())
    }
  }

}
