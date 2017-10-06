package com.karasiq.mailrucloud.api

import scala.language.postfixOps

import akka.http.scaladsl.model._

import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, EntityPath, Nodes, Session}

trait MailCloudRequests {
  def getRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest
  def postRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest
  def downloadRequest(path: EntityPath)(implicit nodes: Nodes, session: Session): HttpRequest
  def uploadRequest(path: EntityPath, data: RequestEntity)(implicit nodes: Nodes, session: Session): HttpRequest
  def loginRequest(email: String, password: String): HttpRequest
  def sdcRequest(implicit session: Session): HttpRequest
  def cloudHomeRequest(implicit session: Session): HttpRequest
  def csrfTokenRequest(pageId: String)(implicit session: Session): HttpRequest
}

trait DefaultMailCloudRequests extends MailCloudRequests { self: MailCloudConstants with MailCloudForms with MailCloudUrls ⇒
  import MailCloudTypes._

  override def getRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.GET, Uri(getApiMethodUrl(method)).withQuery(apiRequestQuery(session, token, data:_*).fields), Vector(session.header))
  }

  override def postRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.POST, getApiMethodUrl(method), Vector(session.header), apiRequestQuery(session, token, data:_*).toEntity)
  }

  override def downloadRequest(path: EntityPath)(implicit nodes: Nodes, session: Session): HttpRequest = {
    val (dlNode, _) = nodes.random
    HttpRequest(HttpMethods.GET, dlNode + path.toURLPath, Vector(session.header))
  }

  private def emptyUploadRequest(path: EntityPath)(implicit nodes: Nodes, session: Session): HttpRequest = {
    val (_, ulNode) = nodes.random
    HttpRequest(HttpMethods.PUT, ulNode, Vector(session.header))
  }

  override def uploadRequest(path: EntityPath, data: RequestEntity)(implicit nodes: Nodes, session: Session) = {
    emptyUploadRequest(path).withEntity(data)
  }

  override def loginRequest(email: String, password: String): HttpRequest = {
    HttpRequest(HttpMethods.POST, AuthURL, entity = loginForm(email, password).toEntity)
  }

  override def sdcRequest(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.GET, AuthSDCURL, Vector(session.header))
  }

  override def cloudHomeRequest(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.GET, BaseDomain, Vector(session.header))
  }

  override def csrfTokenRequest(pageId: String)(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.POST, getApiMethodUrl("tokens/csrf"), Vector(session.header), apiRequestQuery(session, "x-page-id" → pageId).toEntity)
  }
}
