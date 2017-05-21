# future-queue
A queue for Scala which returns Futures for elements which may not have been enqueued yet.

[![Build Status](https://travis-ci.org/NthPortal/future-queue.svg?branch=master)](https://travis-ci.org/NthPortal/future-queue)
[![Coverage Status](https://coveralls.io/repos/github/NthPortal/future-queue/badge.svg?branch=master)](https://coveralls.io/github/NthPortal/future-queue?branch=master)
[![Maven Central](https://img.shields.io/maven-central/v/com.nthportal/future-queue_2.12.svg)](https://mvnrepository.com/artifact/com.nthportal/future-queue_2.12)
[![Versioning](https://img.shields.io/badge/versioning-semver%202.0.0-blue.svg)](http://semver.org/spec/v2.0.0.html)
[![Docs](https://www.javadoc.io/badge/com.nthportal/future-queue_2.12.svg?color=blue&label=docs)](https://www.javadoc.io/doc/com.nthportal/future-queue_2.12)

## Add as a Dependency

### SBT (Scala 2.11 and 2.12)

```sbt
"com.nthportal" %% "future-queue" % "1.1.0"
```

### Maven

**Scala 2.12**

```xml
<dependency>
  <groupId>com.nthportal</groupId>
  <artifactId>future-queue_2.12</artifactId>
  <version>1.1.0</version>
</dependency>
```

**Scala 2.11**

```xml
<dependency>
  <groupId>com.nthportal</groupId>
  <artifactId>future-queue_2.11</artifactId>
  <version>1.1.0</version>
</dependency>
```

## Examples

#### Creating a `FutureQueue`

```scala
import com.nthportal.collection.concurrent.FutureQueue

// Create an empty FutureQueue of Strings
val queue = FutureQueue.empty[String]
// or
val q2 = FutureQueue[String]()

// Create a FutureQueue with elements
val q3 = FutureQueue("some", "strings")

// Create a FutureQueue from an existing (immutable) Queue
import scala.collection.immutable.Queue
val scalaQueue = Queue("some", "strings")
val q4 = FutureQueue(scalaQueue)
```

#### Adding to and Removing from a `FutureQueue`

```scala
import com.nthportal.collection.concurrent.FutureQueue

val queue = FutureQueue.empty[String]

// Add an element
queue.enqueue("a string")
// or
queue += "a string"

// Add multiple individual elements
queue += "string 1" += "string 2" += "another string"

// Add a collection of elements (TraversableOnce)
val elements = Seq("some", "strings", "and", "then", "more", "strings")
queue.enqueue(elements)
// or
queue ++= elements

// Remove a Future which will contain an element once one is added to the queue
val future = queue.dequeue()
future.onComplete(println) // print it eventually
```

#### Automatically Removing or Aggregating Queued Elements

```scala
import com.nthportal.collection.concurrent.FutureQueue
import scala.concurrent.ExecutionContext.Implicits.global

// Automatically remove and print elements when they are enqueued
val q1 = FutureQueue.empty[String]
q1.drainContinually(println)
q1 += "this string will be removed and printed by the global ExecutionContext"

// Automatically add elements to another FutureQueue
val q2 = FutureQueue("a string")
val sink = FutureQueue.empty[Any]
q2.drainToContinually(sink)
q2 += "this string will automatically be removed and added to `sink`"

// Aggregate multiple FutureQueues into a single one
val q3 = FutureQueue.empty[String]
val q4 = FutureQueue.empty[Array[Int]]
val q5 = FutureQueue("")
val aggregate = FutureQueue.aggregate(q3, q4, q5)
q5 +=
  """this string and the empty string already in `q5` will
    |be automatically removed from `q5` and added to `aggregate`
    |""".stripMargin
```
