package com.karasiq.mailrucloud.test

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import com.karasiq.mailrucloud.api.MailCloudClient
import com.karasiq.mailrucloud.api.MailCloudTypes.EntityPath

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.{implicitConversions, postfixOps}

// Test application
object Main extends App {
  implicit val actorSystem = ActorSystem("mailcloud-test")
  implicit val actorMaterializer = ActorMaterializer()
  import actorSystem.dispatcher
  val cloud = MailCloudClient()

  implicit val session = Await.result(cloud.login(sys.props("mailru.email"), sys.props("mailru.password")), Duration.Inf)
  println(session)

  implicit val token = Await.result(cloud.csrfToken, Duration.Inf)
  println(token)

  val space = Await.result(cloud.space, Duration.Inf)
  println(space)

  val nodes = Await.result(cloud.nodes, Duration.Inf)
  println(nodes)

  val listing = Await.result(cloud.folder(cloud.api.ROOT_FOLDER), Duration.Inf)
  println(listing)

  val folderResult = Await.result(for (_ ← cloud.delete("Testfolder"); r ← cloud.createFolder("Testfolder")) yield r, Duration.Inf)
  println(folderResult)

  val deleteResult = Await.result(cloud.delete("test.jpg"), Duration.Inf)
  println(deleteResult)

  if (Files.exists(Paths.get("test.jpg"))) {
    val uploadResult = Await.result(cloud.upload("test.jpg", FileIO.fromPath(Paths.get("test.jpg"))), Duration.Inf)
    println(uploadResult)
  }

  Iterator.continually(StdIn.readLine())
    .takeWhile(null ne)
    .foreach(file ⇒ cloud.download(file).runWith(FileIO.toPath(Paths.get(EntityPath(file).name))))
}
