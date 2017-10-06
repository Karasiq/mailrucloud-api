package com.karasiq.mailrucloud.api

import scala.language.postfixOps

trait MailCloudUrls {
  val BASE_DOMAIN: String
  val AUTH_URL: String
  val AUTH_SDC_URL: String

  def apiMethodUrl(method: String): String
}

trait DefaultMailCloudUrls extends MailCloudUrls {
  val BASE_DOMAIN = "https://cloud.mail.ru/"
  val AUTH_URL = "https://auth.mail.ru/cgi-bin/auth"
  val AUTH_SDC_URL = "https://auth.mail.ru/sdc?from=https%3A%2F%2Fcloud.mail.ru%2F"

  assert(BASE_DOMAIN.endsWith("/"))

  def apiMethodUrl(method: String) = {
    assert(!method.startsWith("/"))
    BASE_DOMAIN + "api/v2/" + method
  }
}