package com.karasiq.mailrucloud.api

import scala.language.postfixOps

import akka.http.scaladsl.model._

import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, Session}

trait MailCloudForms {
  def loginForm(email: String, password: String): FormData
  def apiRequestQuery(session: Session, data: (String, String)*): FormData
  def apiRequestQuery(session: Session, token: CsrfToken, data: (String, String)*): FormData
}

trait MailCloudFormsProvider {
  val forms: MailCloudForms
}