package com.karasiq.mailrucloud.api

import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait MailCloudRequests { self: MailCloudConstants with MailCloudForms with MailCloudUrls with MailCloudContext ⇒
  import MailCloudTypes._

  def getRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.GET, apiMethodUrl(method).withQuery(apiRequestQuery(session, token, data:_*).fields), Vector(session.header))
  }

  def postRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.POST, apiMethodUrl(method), Vector(session.header), apiRequestQuery(session, token, data:_*).toEntity)
  }

  def downloadRequest(nodes: Nodes, path: EntityPath)(implicit session: Session): HttpRequest = {
    val (dlNode, _) = nodes.random
    HttpRequest(HttpMethods.GET, dlNode + path.toURLPath, Vector(session.header))
  }

  def emptyUploadRequest(nodes: Nodes, path: EntityPath)(implicit session: Session): HttpRequest = {
    val (_, ulNode) = nodes.random
    HttpRequest(HttpMethods.POST, ulNode + path.parent.toURLPath, Vector(session.header))
  }

  def uploadRequest(nodes: Nodes, path: EntityPath, data: Source[ByteString, _])(implicit session: Session): Future[HttpRequest] = {
    val formData = uploadRequestEntity(path.name, HttpEntity.IndefiniteLength(ContentTypes.`application/octet-stream`, data))
    formData.toStrict(10 seconds)
      .map(data ⇒ emptyUploadRequest(nodes, path).withEntity(data.toEntity()))
  }

  def loginRequest(email: String, password: String): HttpRequest = {
    HttpRequest(HttpMethods.POST, AUTH_URL, entity = loginForm(email, password).toEntity)
  }

  def sdcRequest(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.GET, AUTH_SDC_URL, Vector(session.header))
  }

  def cloudHomeRequest(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.GET, BASE_DOMAIN, Vector(session.header))
  }

  def csrfTokenRequest(pageId: String)(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.POST, apiMethodUrl("tokens/csrf"), Vector(session.header), apiRequestQuery(session, "x-page-id" → pageId).toEntity)
  }
}
