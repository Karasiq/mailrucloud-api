package com.karasiq.mailrucloud.api

import scala.language.postfixOps

import play.api.libs.json._

trait MailCloudJson {
  import MailCloudTypes._

  implicit val fileFormat = Json.format[File]
  implicit val folderFormat = Json.format[Folder]

  implicit val entityWrites = Json.writes[Entity]
  implicit val entityReads = Reads[Entity] { value ⇒
    (value \ "type").as[String] match {
      case "file" ⇒ JsSuccess(value.as[File])
      case "folder" ⇒ JsSuccess(value.as[Folder])
      case t ⇒ JsError(s"Invalid entity type: $t")
    }
  }

  implicit val entityPathFormat = Format[EntityPath](
    Reads(value ⇒ JsSuccess(EntityPath(value.as[String]))),
    Writes(path ⇒ JsString(path.toString))
  )

  implicit def apiResponseFormat[T: Format] = Json.format[ApiResponse[T]]
}
