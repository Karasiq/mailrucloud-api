package com.karasiq.mailrucloud.api

import scala.language.higherKinds

import com.karasiq.mailrucloud.api.MailCloudTypes._

trait MailCloudFormats {
  type FormatT[T]

  implicit val apiCsrfTokenFormat: FormatT[ApiCsrfToken]
  implicit val nodesFormat: FormatT[Nodes]
  implicit val spaceFormat: FormatT[Space]
  implicit val entityPathFormat: FormatT[EntityPath]
  implicit val entityFormat: FormatT[Entity]
  implicit def apiResponseFormat[T: FormatT]: FormatT[ApiResponse[T]]
}

trait MailCloudFormatsProvider {
  val formats: MailCloudFormats
}
