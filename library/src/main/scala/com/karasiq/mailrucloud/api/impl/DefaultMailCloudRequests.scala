package com.karasiq.mailrucloud.api.impl

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity, Uri}

import com.karasiq.mailrucloud.api._

class DefaultMailCloudRequests(constants: MailCloudConstants, forms: MailCloudForms, urls: MailCloudUrls) extends MailCloudRequests {
  import MailCloudTypes._

  override def getRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.GET, Uri(urls.apiMethodUrl(method)).withQuery(forms.apiRequestQuery(session, token, data:_*).fields), Vector(session.header))
  }

  override def postRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.POST, urls.apiMethodUrl(method), Vector(session.header), forms.apiRequestQuery(session, token, data:_*).toEntity)
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
