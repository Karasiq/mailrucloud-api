package com.karasiq.mailrucloud.api.impl

import com.karasiq.mailrucloud.api.{MailCloudUrls, MailCloudUrlsProvider}

class DefaultMailCloudUrls extends MailCloudUrls {
  val BaseDomain = "https://cloud.mail.ru/"
  val AuthURL = "https://auth.mail.ru/cgi-bin/auth"
  val AuthSDCURL = "https://auth.mail.ru/sdc?from=https%3A%2F%2Fcloud.mail.ru%2F"

  def apiMethodUrl(method: String) = {
    assert(!method.startsWith("/"))
    BaseDomain + "api/v2/" + method
  }
}

trait DefaultMailCloudUrlsProvider extends MailCloudUrlsProvider {
  val urls = new DefaultMailCloudUrls
}