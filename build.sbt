organization := "com.nthportal"
name := "future-queue"
description := "A queue for Scala which returns Futures for elements which may not have been enqueued yet."

val rawVersion = "1.2.0"
isSnapshot := false
version := rawVersion + { if (isSnapshot.value) "-SNAPSHOT" else "" }

scalaVersion := "2.12.1"
crossScalaVersions := Seq(
  "2.11.8",
  "2.11.11",
  "2.12.0",
  "2.12.1"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.+" % Test,
  "com.nthportal" %% "testing-utils" % "1.+" % Test
)

scalacOptions ++= {
  if (isSnapshot.value) Seq()
  else scalaVersion.value split '.' map { _.toInt } match {
    case Array(2, 11, _) => Seq("-optimize")
    case Array(2, 12, patch) if patch <= 2 => Seq("-opt:l:project")
    case Array(2, 12, patch) if patch > 2 => Seq("-opt:l:inline")
    case _ => Seq()
  }
}

publishTo := {
  if (isSnapshot.value) Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
  else None
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
