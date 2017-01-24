package com.karasiq.mailrucloud.api

import upickle.Js
import upickle.Js.Value

import scala.language.postfixOps

trait MailCloudJson extends upickle.AttributeTagged {
  import MailCloudTypes._

  override implicit val LongRW: ReadWriter[Long] = ReadWriter[Long](
    value ⇒ Js.Num(value),
    { case Js.Num(value) ⇒ value.toLong }
  )

  implicit val FolderRW: ReadWriter[Folder] = macroRW[Folder]

  implicit val EntityRW: Writer[Entity] with Reader[Entity] = new Writer[Entity] with Reader[Entity] {
    def write0: (Entity) => Value = {
      case file: File ⇒
        writeJs(file)

      case folder: Folder ⇒
        writeJs(folder)
    }

    def read0: PartialFunction[Value, Entity] = {
      case obj: Js.Obj if obj("type") == Js.Str("file") ⇒
        readJs[File](obj)

      case obj: Js.Obj if obj("type") == Js.Str("folder") ⇒
        readJs[Folder](obj)
    }
  }

  implicit val EntityPathRW = ReadWriter[EntityPath](
    path ⇒ Js.Str(path.toString),
    { case Js.Str(str) ⇒ EntityPath(str) }
  )
}
