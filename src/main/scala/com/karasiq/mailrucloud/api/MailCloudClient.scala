package com.karasiq.mailrucloud.api

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.{Location, `Set-Cookie`}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.mailrucloud.api.MailCloudTypes._

import scala.concurrent.Future
import scala.language.postfixOps

trait MailCloudClient {
  val api: MailCloudApi.MailCloudApiLike
  implicit val context: MailCloudContext

  def login(email: String, password: String): Future[Session]
  def csrfToken(implicit session: Session): Future[CsrfToken]
  def space(implicit session: Session, token: CsrfToken): Future[Space]
  def file(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[File]
  def folder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[Folder]
  def nodes(implicit session: Session, token: CsrfToken): Future[Nodes]
  def delete(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[EntityPath]
  def download(path: EntityPath)(implicit session: Session, token: CsrfToken): Source[ByteString, NotUsed]
  def upload(path: EntityPath, data: Source[ByteString, _])(implicit session: Session, token: CsrfToken): Future[EntityPath]
  def createFolder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[EntityPath]
}

trait MailCloudJsonClient extends MailCloudClient {
  val api: MailCloudApi.MailCloudApiLike with MailCloudJsonApi
  implicit val context: MailCloudContext
  import api._
  import context._

  def login(email: String, password: String): Future[Session] = {
    doHttpRequest(loginRequest(email, password))
      .filter(_.headers.exists(h ⇒ h.is("location") && h.value().startsWith(BASE_DOMAIN)))
      .map(response ⇒ Session(email, response.headers.collect { case `Set-Cookie`(cookie) ⇒ cookie }))
      .flatMap(addSdcToken)
  }

  private def addSdcToken(session: Session): Future[Session] = {
    def extractSdc(response: HttpResponse) = {
      response.headers.collectFirst { case `Set-Cookie`(cookie) if cookie.name == "sdcs" ⇒ cookie }
    }
    def extractLocation(response: HttpResponse) = {
      response.headers.collectFirst { case Location(location) ⇒ location }
    }
    def newSession(response: HttpResponse) = {
      extractSdc(response).fold(session)(cookie ⇒ session.copy(cookies = session.cookies :+ cookie))
    }
    val request = sdcRequest(session)
    doHttpRequest(request).flatMap { response ⇒
      val location = extractLocation(response)
      extractSdc(response) match {
        case None if location.nonEmpty ⇒ // Redirect
          doHttpRequest(request.copy(uri = location.get)).map(newSession)

        case _ ⇒
          Future.successful(newSession(response))
      }
    }
  }

  def csrfToken(implicit session: Session): Future[CsrfToken] = {
    def extractHtmlPageId(response: HttpResponse): Future[String] = {
      val regex = "pageId: '(\\w+)'".r
      response.entity
        .withSizeLimit(1048576)
        .dataBytes.fold(ByteString.empty)(_ ++ _)
        .mapConcat(bs ⇒ regex.findFirstMatchIn(bs.utf8String).map(_.group(1)).toList)
        .runWith(Sink.head)
    }
    doHttpRequest(cloudHomeRequest)
      .flatMap(extractHtmlPageId)
      .flatMap { pageId ⇒
        executeApiRequest[ApiCsrfToken](csrfTokenRequest(pageId))
          .map(r ⇒ CsrfToken(pageId, r.token))
      }
  }

  def space(implicit session: Session, token: CsrfToken): Future[Space] = {
    get[Space]("user/space")
  }

  def file(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[File] = {
    get[File]("file", "home" → path.toString)
  }

  def folder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[Folder] = {
    get[Folder]("folder", "home" → path.toString)
  }

  def nodes(implicit session: Session, token: CsrfToken): Future[Nodes] = {
    get[Nodes]("dispatcher")
  }

  def delete(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[EntityPath] = {
    post[EntityPath]("file/remove", "home" → path.toString)
  }

  def download(path: EntityPath)(implicit session: Session, token: CsrfToken): Source[ByteString, NotUsed] = {
    Source.fromFuture(for (f ← file(path); n ← nodes) yield (f, n))
      .mapAsync(1) { case (file, nodes) ⇒ downloadRequest(nodes, path).map((file, _)) }
      .mapAsync(1) { case (file, request) ⇒ doHttpRequest(request).map((file, _)) }
      .flatMapConcat { case (file, response) ⇒ response.entity.withSizeLimit(file.size).dataBytes }
  }

  def upload(path: EntityPath, data: Source[ByteString, _])(implicit session: Session, token: CsrfToken): Future[EntityPath] = {
    nodes
      .flatMap(uploadRequest(_, path, data))
      .flatMap(doHttpRequest)
      .flatMap(_.entity.withSizeLimit(1000).dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { bs ⇒
        val regex = "(\\w+);(\\d+)".r.unanchored
        bs.utf8String match {
          case regex(hash, size) ⇒
            hash → size

          case _ ⇒
            throw new IllegalArgumentException("Invalid response")
        }
      }
      .flatMap { case (hash, size) ⇒ post[EntityPath]("file/add", "home" → path.toString, "hash" → hash, "size" → size) }
  }

  def createFolder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[EntityPath] = {
    post[EntityPath]("folder/add", "home" → path.toString)
  }

  // TODO: Missing methods
}

object MailCloudClient {
  final class DefaultMailCloudClient(implicit as: ActorSystem) extends MailCloudJsonClient {
    val api = MailCloudApi()
    val context = MailCloudContext()
  }

  def apply()(implicit as: ActorSystem): DefaultMailCloudClient = new DefaultMailCloudClient
}