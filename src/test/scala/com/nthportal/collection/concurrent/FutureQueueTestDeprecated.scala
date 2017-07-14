package com.nthportal.collection.concurrent

import com.nthportal.testing.concurrent.ManualExecutor
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.Queue
import scala.concurrent.Await
import scala.concurrent.duration.Duration

@deprecated("testing deprecated methods", since = "now")
class FutureQueueTestDeprecated extends FlatSpec with Matchers {

  "implicit conversion from FutureQueue to Queue (deprecated)" should "convert properly" in {
    import FutureQueue.Implicits._

    val q = Queue("some", "elements")
    val fq = FutureQueue(q)

    fq.seq should be theSameInstanceAs q
  }

  behavior of "FutureQueue (deprecated)"

  it should "enqueue multiple elements" in {
    val fq = FutureQueue.empty[String]
    val list = List("some", "test", "strings")

    fq enqueue list
    fq.queued shouldEqual list
    fq should have size list.size
  }

  it should "drain to another `FutureQueue`" in {
    val executor = ManualExecutor()

    import executor.Implicits._

    val q1 = FutureQueue.empty[String]
    val q2 = FutureQueue.empty[String]

    q1 drainToContinually q2
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

    an [IllegalArgumentException] should be thrownBy { q2 drainToContinually q2 }
  }
}
