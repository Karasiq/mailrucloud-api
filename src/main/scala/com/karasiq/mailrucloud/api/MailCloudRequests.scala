package com.karasiq.mailrucloud.api

import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, EntityPath, Nodes, Session}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait MailCloudRequests {
  def getRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest
  def postRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest
  def downloadRequest(nodes: Nodes, path: EntityPath)(implicit context: MailCloudContext, session: Session): Future[HttpRequest]
  def emptyUploadRequest(nodes: Nodes, path: EntityPath)(implicit session: Session): HttpRequest
  def uploadRequest(nodes: Nodes, path: EntityPath, data: Source[ByteString, _])(implicit context: MailCloudContext, session: Session): Future[HttpRequest]
  def loginRequest(email: String, password: String): HttpRequest
  def sdcRequest(implicit session: Session): HttpRequest
  def cloudHomeRequest(implicit session: Session): HttpRequest
  def csrfTokenRequest(pageId: String)(implicit session: Session): HttpRequest
}

trait DefaultMailCloudRequests extends MailCloudRequests { self: MailCloudConstants with MailCloudForms with MailCloudUrls ⇒
  import MailCloudTypes._

  override def getRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.GET, Uri(apiMethodUrl(method)).withQuery(apiRequestQuery(session, token, data:_*).fields), Vector(session.header))
  }

  override def postRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.POST, apiMethodUrl(method), Vector(session.header), apiRequestQuery(session, token, data:_*).toEntity)
  }

  override def downloadRequest(nodes: Nodes, path: EntityPath)(implicit context: MailCloudContext, session: Session): Future[HttpRequest] = {
    val (dlNode, _) = nodes.random
    Future.successful(HttpRequest(HttpMethods.GET, dlNode + path.toURLPath, Vector(session.header)))
  }

  override def emptyUploadRequest(nodes: Nodes, path: EntityPath)(implicit session: Session): HttpRequest = {
    val (_, ulNode) = nodes.random
    HttpRequest(HttpMethods.POST, ulNode + path.parent.toURLPath, Vector(session.header))
  }

  override def uploadRequest(nodes: Nodes, path: EntityPath, data: Source[ByteString, _])(implicit context: MailCloudContext, session: Session): Future[HttpRequest] = {
    import context._
    val formData = uploadRequestEntity(path.name, HttpEntity.IndefiniteLength(ContentTypes.`application/octet-stream`, data))
    formData.toStrict(10 seconds) // TODO: Streaming request (content-length required)
      .map(data ⇒ emptyUploadRequest(nodes, path).withEntity(data.toEntity()))
  }

  override def loginRequest(email: String, password: String): HttpRequest = {
    HttpRequest(HttpMethods.POST, AUTH_URL, entity = loginForm(email, password).toEntity)
  }

  override def sdcRequest(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.GET, AUTH_SDC_URL, Vector(session.header))
  }

  override def cloudHomeRequest(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.GET, BASE_DOMAIN, Vector(session.header))
  }

  override def csrfTokenRequest(pageId: String)(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.POST, apiMethodUrl("tokens/csrf"), Vector(session.header), apiRequestQuery(session, "x-page-id" → pageId).toEntity)
  }
}
