package com.karasiq.mailrucloud.api

import akka.http.scaladsl.model.Uri

import scala.language.postfixOps

trait MailCloudUrls {
  val BASE_DOMAIN = "https://cloud.mail.ru/"
  val AUTH_URL = "https://auth.mail.ru/cgi-bin/auth"
  val AUTH_SDC_URL = "https://auth.mail.ru/sdc?from=https%3A%2F%2Fcloud.mail.ru%2F"

  def apiMethodUrl(method: String) = {
    Uri(BASE_DOMAIN).withPath(Uri.Path("/api/v2/" + method))
  }
}
