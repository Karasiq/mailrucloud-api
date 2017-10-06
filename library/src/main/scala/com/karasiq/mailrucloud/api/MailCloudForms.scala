package com.karasiq.mailrucloud.api

import scala.language.postfixOps

import akka.http.scaladsl.model._

import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, Session}
import com.karasiq.mailrucloud.utils.MailCloudUtils

trait MailCloudForms {
  def loginForm(email: String, password: String): FormData
  def apiRequestQuery(session: Session, data: (String, String)*): FormData
  def apiRequestQuery(session: Session, token: CsrfToken, data: (String, String)*): FormData
  def uploadRequestEntity(fileName: String, entity: HttpEntity.Default): RequestEntity
}

trait DefaultMailCloudForms extends MailCloudForms { self: MailCloudConstants with MailCloudUrls ⇒
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
      "page" → BaseDomain,
      "Domain" → domain,
      "Login" → login,
      "Password" → password
    )
  }

  override def apiRequestQuery(session: Session, data: (String, String)*): FormData = {
    FormData(Seq(
      "api" → "2",
      "build" → ADVERTISED_BUILD,
      "email" → session.email,
      "x-email" → session.email
    ) ++ data:_*)
  }

  override def apiRequestQuery(session: Session, token: CsrfToken, data: (String, String)*): FormData = {
    apiRequestQuery(session, Seq("x-page-id" → token.pageId, "token" → token.token) ++ data:_*)
  }

  override def uploadRequestEntity(fileName: String, entity: HttpEntity.Default): RequestEntity = {
    MailCloudUtils.instantSizedMultipartUpload(fileName, entity)
  }
}
