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

trait MailCloudRequestsProvider {
  val requests: MailCloudRequests
}
