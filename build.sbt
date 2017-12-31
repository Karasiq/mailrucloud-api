lazy val commonSettings = Seq(
  organization := "com.github.karasiq",
  version := "1.0.2-SNAPSHOT",
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
      "com.typesafe.play" %% "play-json" % "2.6.0"
    )
  }
)

lazy val testAppSettings = Seq(
  name := "mailrucloud-api-test",
  mainClass in Compile := Some("com.karasiq.mailrucloud.test.Main")
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ ⇒ false },
  licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0")),
  homepage := Some(url("https://github.com/Karasiq/mailrucloud-api")),
  pomExtra := <scm>
    <url>git@github.com:Karasiq/mailrucloud-api.git</url>
    <connection>scm:git:git@github.com:Karasiq/mailrucloud-api.git</connection>
  </scm>
    <developers>
      <developer>
        <id>karasiq</id>
        <name>Piston Karasiq</name>
        <url>https://github.com/Karasiq</url>
      </developer>
    </developers>
)

lazy val noPublishSettings = Seq(
  publishArtifact := false,
  publishArtifact in makePom := false,
  publishTo := Some(Resolver.file("Repo", file("target/repo")))
)

lazy val library = project
  .settings(commonSettings, librarySettings, publishSettings)
  
lazy val testApp = (project in file("test-app"))
  .settings(commonSettings, testAppSettings)
  .dependsOn(library)
  .enablePlugins(JavaAppPackaging)

lazy val `mailrucloud-api` = (project in file("."))
  .settings(commonSettings, noPublishSettings)
  .aggregate(library)