package com.karasiq.mailrucloud.api

import akka.http.scaladsl.model._
import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, EntityPath, Nodes, Session}

import scala.language.postfixOps

trait MailCloudRequests {
  def getRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest
  def postRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest
  def downloadRequest(path: EntityPath)(implicit nodes: Nodes, session: Session): HttpRequest
  def uploadRequest(path: EntityPath, data: HttpEntity.Default)(implicit nodes: Nodes, session: Session): HttpRequest
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

  override def downloadRequest(path: EntityPath)(implicit nodes: Nodes, session: Session): HttpRequest = {
    val (dlNode, _) = nodes.random
    HttpRequest(HttpMethods.GET, dlNode + path.toURLPath, Vector(session.header))
  }

  private def emptyUploadRequest(path: EntityPath)(implicit nodes: Nodes, session: Session): HttpRequest = {
    val (_, ulNode) = nodes.random
    HttpRequest(HttpMethods.POST, ulNode + path.parent.toURLPath, Vector(session.header))
  }

  override def uploadRequest(path: EntityPath, data: HttpEntity.Default)(implicit nodes: Nodes, session: Session): HttpRequest = {
    emptyUploadRequest(path)
      .withEntity(uploadRequestEntity(path.name, data))
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
