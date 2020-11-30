package com.karasiq.mailrucloud.api.impl

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity, Uri}
import akka.http.scaladsl.model.headers.{`User-Agent`, RawHeader, Referer}

import com.karasiq.mailrucloud.api._

class DefaultMailCloudRequests(constants: MailCloudConstants, forms: MailCloudForms, urls: MailCloudUrls) extends MailCloudRequests {
  import MailCloudTypes._

  private[this] def defaultHeaders(implicit token: CsrfToken) = Vector(
    Referer("https://cloud.mail.ru/home/"),
    RawHeader("Sec-Fetch-Dest", "empty"),
    RawHeader("Sec-Fetch-Mode", "cors"),
    RawHeader("Sec-Fetch-Site", "same-origin"),
    `User-Agent`("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Safari/537.36"),
    RawHeader("X-CSRF-Token", token.token),
    RawHeader("X-Requested-With", "XMLHttpRequest")
  )

  override def getRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.GET, Uri(urls.apiMethodUrl(method)).withQuery(forms.apiRequestQuery(session, token, data: _*).fields), defaultHeaders :+ session.header)
  }

  override def postRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.POST, urls.apiMethodUrl(method), defaultHeaders :+ session.header, forms.apiRequestQuery(session, token, data: _*).toEntity)
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
    HttpRequest(HttpMethods.POST, urls.AuthURL, entity = forms.loginForm(email, password).toEntity)
  }

  override def sdcRequest(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.GET, urls.AuthSDCURL, Vector(session.header))
  }

  override def cloudHomeRequest(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.GET, urls.BaseDomain, Vector(session.header))
  }

  override def csrfTokenRequest(pageId: String)(implicit session: Session): HttpRequest = {
    HttpRequest(HttpMethods.POST, urls.apiMethodUrl("tokens/csrf"), Vector(session.header), forms.apiRequestQuery(session, "x-page-id" → pageId).toEntity)
  }
}

trait DefaultMailCloudRequestsProvider extends MailCloudRequestsProvider { self: MailCloudConstantsProvider with MailCloudFormsProvider with MailCloudUrlsProvider ⇒
  lazy val requests = new DefaultMailCloudRequests(constants, forms, urls)
}
