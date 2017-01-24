package com.karasiq.mailrucloud.api

import akka.http.scaladsl.model._
import akka.util.ByteString

import scala.language.postfixOps

trait MailCloudForms { self: MailCloudConstants with MailCloudUrls ⇒
  import MailCloudTypes._

  def loginForm(email: String, password: String): FormData = {
    val (login, domain) = email.split("@", 2) match {
      case Array(login, domain) ⇒
        login → domain

      case Array(login) ⇒
        login → "mail.ru"
    }

    FormData(
      "new_auth_form" → "1",
      "page" → BASE_DOMAIN,
      "Domain" → domain,
      "Login" → login,
      "Password" → password
    )
  }

  def apiRequestQuery(session: Session, data: (String, String)*): FormData = {
    FormData(Seq(
      "api" → "2",
      "build" → ADVERTISED_BUILD,
      "email" → session.email,
      "x-email" → session.email
    ) ++ data:_*)
  }

  def apiRequestQuery(session: Session, token: CsrfToken, data: (String, String)*): FormData = {
    apiRequestQuery(session, Seq("x-page-id" → token.pageId, "token" → token.token) ++ data:_*)
  }

  def uploadRequestEntity(fileName: String, entity: BodyPartEntity): Multipart.FormData = {
    Multipart.FormData(
      Multipart.FormData.BodyPart("file", entity, Map("filename" → fileName)),
      Multipart.FormData.BodyPart.Strict("_file", HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString(fileName)))
    )
  }
}
