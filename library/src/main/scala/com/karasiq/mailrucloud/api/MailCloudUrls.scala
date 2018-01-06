package com.karasiq.mailrucloud.api

import scala.language.postfixOps

trait MailCloudUrls {
  val BaseDomain: String
  val AuthURL: String
  val AuthSDCURL: String

  def apiMethodUrl(method: String): String
}

trait MailCloudUrlsProvider {
  val urls: MailCloudUrls
}