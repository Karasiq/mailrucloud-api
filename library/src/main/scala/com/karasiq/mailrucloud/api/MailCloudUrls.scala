package com.karasiq.mailrucloud.api

import scala.language.postfixOps

trait MailCloudUrls {
  val BaseDomain: String
  val AuthURL: String
  val AuthSDCURL: String

  def getApiMethodUrl(method: String): String
}

trait DefaultMailCloudUrls extends MailCloudUrls {
  val BaseDomain = "https://cloud.mail.ru/"
  val AuthURL = "https://auth.mail.ru/cgi-bin/auth"
  val AuthSDCURL = "https://auth.mail.ru/sdc?from=https%3A%2F%2Fcloud.mail.ru%2F"

  assert(BaseDomain.endsWith("/"))

  def getApiMethodUrl(method: String) = {
    assert(!method.startsWith("/"))
    BaseDomain + "api/v2/" + method
  }
}