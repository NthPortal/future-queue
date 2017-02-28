organization := "com.nthportal"
name := "future-queue"
description := "A queue for Scala which returns Futures for elements which may not have been enqueued yet."

val rawVersion = "1.1.0"
isSnapshot := false
version := rawVersion + {if (isSnapshot.value) "-SNAPSHOT" else ""}

scalaVersion := "2.12.1"
crossScalaVersions := Seq(
  "2.11.8",
  "2.12.0",
  "2.12.1"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.+" % Test,
  "com.nthportal" %% "testing-utils" % "1.+" % Test
)

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true
licenses := Seq("The Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
homepage := Some(url("https://github.com/NthPortal/future-queue"))

pomExtra :=
  <scm>
    <url>https://github.com/NthPortal/future-queue</url>
    <connection>scm:git:git@github.com:NthPortal/future-queue.git</connection>
    <developerConnection>scm:git:git@github.com:NthPortal/future-queue.git</developerConnection>
  </scm>
  <developers>
    <developer>
      <id>NthPortal</id>
      <name>NthPortal</name>
      <url>https://github.com/NthPortal</url>
    </developer>
  </developers>
