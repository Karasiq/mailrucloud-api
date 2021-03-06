package com.karasiq.mailrucloud.api.impl.json

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.{higherKinds, postfixOps}

import akka.NotUsed
import akka.http.scaladsl.model.{HttpResponse, RequestEntity}
import akka.http.scaladsl.model.headers.{`Set-Cookie`, Location}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import com.karasiq.mailrucloud.api._
import com.karasiq.mailrucloud.api.MailCloudTypes._

trait MailCloudJsonClient extends MailCloudClient with MailCloudJsonApiProvider { self: MailCloudContextProvider with MailCloudRequestsProvider with MailCloudUrlsProvider with MailCloudConstantsProvider with MailCloudFormsProvider ⇒

  import api._
  import context._
  import formats._

  def login(email: String, password: String): Future[Session] = {
    doHttpRequest(requests.loginRequest(email, password))
      .filter(_.headers.exists(h ⇒ h.is("location") && h.value().startsWith(urls.BaseDomain)))
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
    val request = requests.sdcRequest(session)
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
      val regex = "pageId:\"(\\w+)\"".r
      response.entity
        .withSizeLimit(1048576)
        .dataBytes.fold(ByteString.empty)(_ ++ _)
        .mapConcat(bs ⇒ regex.findFirstMatchIn(bs.utf8String).map(_.group(1)).toList)
        .orElse(Source.single(""))
        .runWith(Sink.head)
    }
    doHttpRequest(requests.cloudHomeRequest)
      .flatMap(extractHtmlPageId)
      .flatMap { pageId ⇒
        executeApiRequest[ApiCsrfToken](requests.csrfTokenRequest(pageId))
          .map(r ⇒ CsrfToken(pageId, r.token))
      }
  }

  def space(implicit session: Session, token: CsrfToken): Future[Space] = {
    get[Space]("user/space")
  }

  def file(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[Entity] = {
    get[File]("file", "home" → path.toString)
  }

  def folder(path: EntityPath, offset: Long, limit: Long)(implicit session: Session, token: CsrfToken): Future[Folder] = {
    get[Folder]("folder", "home" → path.toString, "offset" → offset.toString, "limit" → limit.toString)
  }

  def nodes(implicit session: Session, token: CsrfToken): Future[Nodes] = {
    get[Nodes]("dispatcher")
  }

  def delete(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[EntityPath] = {
    post[EntityPath]("file/remove", "home" → path.toString)
  }

  def download(path: EntityPath)(implicit nodes: Nodes, session: Session, token: CsrfToken): Source[ByteString, NotUsed] = {
    Source.fromFuture(file(path))
      .map((_, requests.downloadRequest(path)))
      .mapAsync(1) { case (file, request) ⇒ doHttpRequest(request, handleRedirects = true).map((file, _)) }
      .flatMapConcat { case (file, response) ⇒ response.entity.withSizeLimit(file.size).dataBytes }
  }

  override def upload(path: EntityPath, data: RequestEntity)(implicit nodes: Nodes, session: Session, token: CsrfToken): Future[EntityPath] = {
    val dataSize = data.contentLengthOption.getOrElse(throw new IllegalArgumentException("Content length required"))
    if (dataSize <= 20) { // "too short body"
      data.toStrict(5 seconds).flatMap { strictEntity ⇒
        val hash = String.format("%x", BigInt(1, strictEntity.data.padTo(20, 0: Byte).toArray).underlying())
        post[EntityPath]("file/add", "home" → path.toString, "hash" → hash, "size" → dataSize.toString)
      }
    } else {
      def registerUploadedFile(path: EntityPath, size: Long, result: Future[HttpResponse])
                              (implicit nodes: Nodes, session: Session, token: CsrfToken) = {

        Source.fromFuture(result)
          .flatMapConcat(_.entity.withSizeLimit(1000).dataBytes.fold(ByteString.empty)(_ ++ _))
          .mapAsync(1)(hash ⇒ post[EntityPath]("file/add", "home" → path.toString, "hash" → hash.utf8String, "size" → size.toString))
          .named("registerUploadedFile")
          .runWith(Sink.head)
      }

      val hashFuture = doHttpRequest(requests.uploadRequest(path, data))
      registerUploadedFile(path, dataSize, hashFuture)
    }
  }

  def createFolder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[EntityPath] = {
    post[EntityPath]("folder/add", "home" → path.toString)
  }

  // TODO: Missing methods
}
