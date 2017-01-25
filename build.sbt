name := "mailrucloud-api"

organization := "com.github.karasiq"

version := "1.0.0-SNAPSHOT"

isSnapshot := version.value.endsWith("SNAPSHOT")

scalaVersion := "2.12.1"

resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= {
  val akkaV = "2.4.16"
  val akkaHttpV = "10.0.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.lihaoyi" %% "upickle" % "0.4.4"
  )                       
}

mainClass in Compile := Some("com.karasiq.mailrucloud.test.Main")

licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)