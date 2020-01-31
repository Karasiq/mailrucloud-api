package com.karasiq.mailrucloud.test

import java.io.File
import java.nio.file.{Files, Paths, StandardOpenOption}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.{implicitConversions, postfixOps}
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity, MediaTypes}
import akka.stream.scaladsl.{FileIO, Sink}
import com.karasiq.mailrucloud.api.MailCloudClient
import com.karasiq.mailrucloud.api.MailCloudTypes.EntityPath

import scala.util.{Failure, Success}

// Test application
object Main extends App {
  implicit val actorSystem = ActorSystem("mailcloud-test")
  val cloud = MailCloudClient()
  import cloud.context._

  implicit val session = Await.result(cloud.login(sys.props("mailru.email"), sys.props("mailru.password")), Duration.Inf)
  println(session)

  implicit val token = Await.result(cloud.csrfToken, Duration.Inf)
  println(token)

  val space = Await.result(cloud.space, Duration.Inf)
  println(space)

  implicit val nodes = Await.result(cloud.nodes, Duration.Inf)
  println(nodes)

  val listing = Await.result(cloud.folder(cloud.constants.RootFolder), Duration.Inf)
  println(listing)

  val folderResult = Await.result(for (_ ← cloud.delete("Testfolder"); r ← cloud.createFolder("Testfolder")) yield r, Duration.Inf)
  println(folderResult)

  val testJpg = "Testfolder/test.jpg"
  val deleteResult = Await.result(cloud.delete(testJpg), Duration.Inf)
  println(s"DELETED: $deleteResult")

  val localTestJpg = Paths.get("test.jpg")
  if (Files.exists(localTestJpg)) {
    val uploadResult = Await.result(cloud.upload(testJpg, HttpEntity.Default(ContentType(MediaTypes.`image/jpeg`), Files.size(localTestJpg), FileIO.fromPath(localTestJpg))), Duration.Inf)
    println(s"UPLOADED: $uploadResult")
  }

  Await.result(cloud.upload("TestFolder/test.txt", HttpEntity(ContentTypes.`application/octet-stream`, new File("test.txt"))), Duration.Inf)

  Iterator.continually(StdIn.readLine())
    .takeWhile(null ne)
    .foreach { file =>
      val path = Paths.get("Downloaded", EntityPath(file).name)
      Files.createDirectories(path.getParent)

      val sink = FileIO.toPath(path, Set(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
      cloud.download(file)
        .alsoTo(Sink.onComplete {
          case Success(_) =>
            println(s"DOWNLOADED: $file")

          case Failure(exception) =>
            println(s"Error downloading $file")
            exception.printStackTrace()
        })
        .runWith(sink)
    }
}
