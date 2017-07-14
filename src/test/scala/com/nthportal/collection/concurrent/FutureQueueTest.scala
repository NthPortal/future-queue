package com.nthportal.collection.concurrent

import com.nthportal.testing.concurrent.ManualExecutor
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.Queue
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class FutureQueueTest extends FlatSpec with Matchers {

  import FutureQueueTest._

  behavior of "FutureQueue companion object"

  it should "create an empty `FutureQueue`" in {
    val fq = FutureQueue.empty[Any]
    fq should have size 0
    fq.queued shouldBe empty

    fq shouldEqual FutureQueue[Nothing]()
  }

  it should "create a `FutureQueue`" in {
    val q = Queue("some", "elements")
    FutureQueue(q).queued should be theSameInstanceAs q

    FutureQueue("some", "elements").queued shouldEqual q
  }

  it should "aggregate `FutureQueue`s" in {
    val executor = ManualExecutor()

    import executor.Implicits._

    val q1 = FutureQueue.empty[String]
    val q2 = FutureQueue.empty[String]
    val q3 = FutureQueue.empty[String]
    val aggregate = FutureQueue.aggregate(q1, q2, q3)

    q1 += "q1"
    executor.executeAll()
    aggregate should have size 1
    q1.promiseCount shouldBe 1
    Await.result(aggregate.dequeue(), Duration.Zero) shouldBe "q1"
    aggregate should have size 0

    q2 += "q2"
    q3 += "q3"
    executor.executeAll()
    aggregate should have size 2
    q2.promiseCount shouldBe 1
    q3.promiseCount shouldBe 1
    val res1 = Await.result(aggregate.dequeue(), Duration.Zero)
    val res2 = Await.result(aggregate.dequeue(), Duration.Zero)
    res1 should (be("q2") or be("q3"))
    res2 should (be("q2") or be("q3"))
    res1 should not equal res2
    aggregate should have size 0

    aggregate += "0"
    q1 ++= Seq("1", "2")
    aggregate should have size 1
    executor.executeAll()
    aggregate should have size 3
    q1.promiseCount shouldBe 1
    Await.result(aggregate.dequeue(), Duration.Zero) shouldBe "0"
    Await.result(aggregate.dequeue(), Duration.Zero) shouldBe "1"
    Await.result(aggregate.dequeue(), Duration.Zero) shouldBe "2"
    aggregate should have size 0
  }

  behavior of "FutureQueue"

  it should "enqueue a single element" in {
    val fq = FutureQueue.empty[String]

    val s1 = "test1"
    (fq += s1).queued should contain(s1)
    fq should have size 1

    val s2 = "test2"
    fq.enqueue(s2)
    fq.queued should contain(s2)
    fq should have size 2
  }

  it should "enqueue multiple elements" in {
    val list = List("some", "test", "strings")

    val fq1 = FutureQueue.empty[String]
    (fq1 ++= list).queued shouldEqual list
    fq1 should have size list.size

    val fq2 = FutureQueue.empty[String]
    fq2.enqueue(list: _*)
    fq2.queued shouldEqual list
    fq2 should have size list.size

    val fq3 = FutureQueue.empty[String]
    val singletonList = List("test")
    fq3.enqueue(singletonList: _*)
    fq3.queued shouldEqual singletonList
    fq3 should have size singletonList.size

    val fq4 = FutureQueue.empty
    fq4.enqueue(List(): _*)
    fq4 should have size 0
  }

  it should "keep multiple promises" in {
    val fq = FutureQueue.empty[String]
    val list = List("some", "test", "strings")

    val futures = List.fill(3) { fq.dequeue() }
    fq ++= list

    futures.foreach(_.isCompleted shouldBe true)
    futures.map { Await.result(_, Duration.Zero) } shouldEqual list
  }

  it should "dequeue elements" in {
    val s = "test"
    val fq = FutureQueue(s)

    val f1 = fq.dequeue()
    f1.isCompleted shouldBe true
    Await.result(f1, Duration.Zero) shouldEqual s

    val f2 = fq.dequeue()
    f2.isCompleted shouldBe false

    fq.enqueue(s)
    f2.isCompleted shouldBe true
    Await.result(f2, Duration.Zero) shouldEqual s
  }

  it should "have the correct promise count and size" in {
    val fq = FutureQueue.empty[String]
    fq should have size 0
    fq.promiseCount shouldBe 0

    fq += "test"
    fq should have size 1
    fq.promiseCount shouldBe 0

    fq.dequeue()
    fq should have size 0
    fq.promiseCount shouldBe 0

    fq.dequeue()
    fq should have size -1
    fq.promiseCount shouldBe 1

    fq.dequeue()
    fq should have size -2
    fq.promiseCount shouldBe 2
  }

  it should "drain continually" in {
    val executor = ManualExecutor()

    import executor.Implicits._

    val fq = FutureQueue.empty[String]
    val mq = mutable.Queue.empty[String]

    fq drainContinually { mq += _ }
    fq.promiseCount shouldBe 1

    fq += "1"
    executor.executeAll()
    mq should have length 1
    mq.dequeue() shouldBe "1"
    fq.promiseCount shouldBe 1

    fq ++= Seq("2", "3")
    executor.executeAll()
    mq should have length 2
    mq.dequeue() shouldBe "2"
    mq.dequeue() shouldBe "3"
    fq.promiseCount shouldBe 1
  }

  it should "drain to another `FutureQueue`" in {
    val executor = ManualExecutor()

    import executor.Implicits._

    val q1 = FutureQueue.empty[String]
    val q2 = FutureQueue.empty[String]

    q1 drainContinuallyTo q2
    q1.promiseCount shouldBe 1

    q1 += "1"
    executor.executeAll()
    q2 should have size 1
    Await.result(q2.dequeue(), Duration.Zero) shouldBe "1"
    q1.promiseCount shouldBe 1

    q1 ++= Seq("2", "3")
    executor.executeAll()
    q2 should have size 2
    Await.result(q2.dequeue(), Duration.Zero) shouldBe "2"
    Await.result(q2.dequeue(), Duration.Zero) shouldBe "3"
    q1.promiseCount shouldBe 1

    an [IllegalArgumentException] should be thrownBy { q2 drainContinuallyTo q2 }
  }

  it should "evaluate equality properly" in {
    FutureQueue.empty[String] shouldBeEquivalentTo FutureQueue.empty[Int]
    FutureQueue("") shouldBeEquivalentTo FutureQueue("")
    FutureQueue("") should not equal FutureQueue.empty[String]

    val fq = FutureQueue.empty[String]
    fq.dequeue()
    fq should not equal FutureQueue.empty[String]

    val other = FutureQueue.empty[Int]
    other.dequeue()

    fq should not equal other
    fq.queued shouldEqual other.queued

    FutureQueue("") should not equal ""
  }

  it should "be represented by a sensible string" in {
    FutureQueue.empty.toString should include ("empty")

    val list = List("some", "test", "strings")
    val s = FutureQueue(list).toString
    list.foreach(e => s should include (e))

    val fq = FutureQueue.empty
    fq.dequeue()
    fq.dequeue()
    fq.toString should include (fq.promiseCount.toString)
  }
}

private object FutureQueueTest extends Matchers {

  implicit final class RichFutureQueue[A](private val a: FutureQueue[A]) extends AnyVal {
    def shouldBeEquivalentTo(b: FutureQueue[_]): Unit = {
      a shouldEqual b
      a.hashCode() shouldEqual b.hashCode()
    }
  }

}
