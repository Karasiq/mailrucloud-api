package com.karasiq.mailrucloud.api.impl.json

import play.api.libs.functional.syntax._
import play.api.libs.json._
import Reads._

import com.karasiq.mailrucloud.api.{MailCloudFormats, MailCloudFormatsProvider}

class MailCloudJsonFormats extends MailCloudFormats  {
  import com.karasiq.mailrucloud.api.MailCloudTypes._

  override type FormatT[T] = Format[T]

  /* implicit val intFormat = Format[Int](
    Reads {
      case JsString(str) ⇒ JsSuccess(str.toInt)
      case JsNumber(int) ⇒ JsSuccess(int.intValue())
      case v ⇒ JsError(s"Not an integer: $v")
    },
    Writes(int ⇒ JsNumber(int))
  )

  implicit val longFormat = Format[Long](
    Reads {
      case JsString(str) ⇒ JsSuccess(str.toLong)
      case JsNumber(int) ⇒ JsSuccess(int.longValue())
      case v ⇒ JsError(s"Not an integer: $v")
    },
    Writes(int ⇒ JsNumber(int))
  ) */

  implicit val apiCsrfTokenFormat = Json.format[ApiCsrfToken]
  implicit val nodeFormat = Json.format[Node]
  implicit val nodesFormat = Json.format[Nodes]
  implicit val spaceFormat = Json.format[Space]

  implicit val entityCountFormat = Json.format[EntityCount]
  implicit val entityPathFormat = Format[EntityPath](
    Reads(value ⇒ JsSuccess(EntityPath(value.as[String]))),
    Writes(path ⇒ JsString(path.toString))
  )

  private[this] val entityFields =
    (JsPath \ "type").read[String] and
      (JsPath \ "kind").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "home").read[String] and
      (JsPath \ "size").read[Long]

  implicit val entityFormat: Format[Entity] = Format[Entity](
    Reads { value ⇒
      (value \ "type").as[String] match {
        case "file" ⇒ JsSuccess(value.as[File](fileFormat))
        case "folder" ⇒ JsSuccess(value.as[Folder](folderFormat))
        case t ⇒ JsError(s"Invalid entity type: $t")
      }
    },
    Writes {
      case f: File ⇒ Json.toJson(f)(fileFormat)
      case f: Folder ⇒ Json.toJson(f)(folderFormat)
      case e ⇒ throw new IllegalArgumentException(s"Invalid entity: $e")
    }
  )

  implicit lazy val fileFormat = Format[File](
    (entityFields and
      (JsPath \ "hash").read[String] and
      withDefault(JsPath \ "rev", 0) and
      withDefault(JsPath \ "grev", 0)
    )(File.apply _),
    Json.writes[File]
  )

  implicit lazy val folderFormat = Format[Folder](
    (entityFields and
      (JsPath \ "tree").read[String] and
      withDefault(JsPath \ "rev", 0) and
      withDefault(JsPath \ "grev", 0) and
      withDefault(JsPath \ "count", EntityCount.empty) and
      withDefault(JsPath \ "list", Nil: Seq[Entity])
    )(Folder.apply _),
    Json.writes[Folder]
  )

  /* implicit val entitySeqFormat = Format[Seq[Entity]](
    Reads { value ⇒
      JsSuccess(value.as[JsArray].value.map(_.as[Entity]))
    },
    Writes { entities ⇒
      JsArray(entities.map(Json.toJson(_)))
    }
  ) */

  implicit def apiResponseFormat[T: Format] = Json.format[ApiResponse[T]]
}

trait MailCloudJsonFormatsProvider extends MailCloudFormatsProvider {
  val formats = new MailCloudJsonFormats
}