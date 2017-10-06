lazy val commonSettings = Seq(
  organization := "com.github.karasiq",
  version := "1.0.0-SNAPSHOT",
  isSnapshot := version.value.endsWith("SNAPSHOT"),
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.11.11", "2.12.3"),
  resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))
)

lazy val librarySettings = Seq(
  name := "mailrucloud-api",
  libraryDependencies ++= {
    val akkaV = "2.5.4"
    val akkaHttpV = "10.0.10"
    Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-http" % akkaHttpV,
      "com.lihaoyi" %% "upickle" % "0.4.4"
    )
  }
)

lazy val testAppSettings = Seq(
  name := "mailrucloud-api-test",
  mainClass in Compile := Some("com.karasiq.mailrucloud.test.Main")
)

lazy val library = project
  .settings(commonSettings, librarySettings)
  
lazy val testApp = (project in file("test-app"))
  .settings(commonSettings, testAppSettings)
  .dependsOn(library)
  .enablePlugins(JavaAppPackaging)

lazy val `mailrucloud-api` = (project in file("."))
  .settings(commonSettings)
  .aggregate(library)