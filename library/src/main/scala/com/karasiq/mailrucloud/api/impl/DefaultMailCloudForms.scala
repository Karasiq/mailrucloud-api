package com.karasiq.mailrucloud.api.impl

import akka.http.scaladsl.model.FormData

import com.karasiq.mailrucloud.api._

class DefaultMailCloudForms(constants: MailCloudConstants, urls: MailCloudUrls) extends MailCloudForms {
  import MailCloudTypes._

  override def loginForm(email: String, password: String): FormData = {
    val (login, domain) = email.split("@", 2) match {
      case Array(login, domain) ⇒
        login → domain

      case Array(login) ⇒
        login → "mail.ru"
    }

    FormData(
      "new_auth_form" → "1",
      "page" → urls.BaseDomain,
      "Domain" → domain,
      "Login" → login,
      "Password" → password
    )
  }

  override def apiRequestQuery(session: Session, data: (String, String)*): FormData = {
    FormData(Seq(
      "api" → "2",
      "build" → constants.AdvertisedBuild,
      "email" → session.email,
      "x-email" → session.email,
      "_" → System.currentTimeMillis().toString
    ) ++ data: _*)
  }

  override def apiRequestQuery(session: Session, token: CsrfToken, data: (String, String)*): FormData = {
    apiRequestQuery(session, Seq("x-page-id" → token.pageId /*, "token" → token.token */) ++ data: _*)
  }
}

trait DefaultMailCloudFormsProvider extends MailCloudFormsProvider { self: MailCloudConstantsProvider with MailCloudUrlsProvider ⇒
  lazy val forms = new DefaultMailCloudForms(constants, urls)
}