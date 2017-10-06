package com.karasiq.mailrucloud.api

import java.io.IOException
import java.net.URLEncoder

import scala.language.{implicitConversions, postfixOps}
import scala.util.Random

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Cookie, HttpCookie}
import akka.util.ByteString
import derive.key

object MailCloudTypes {
  case class ApiResponse[T](email: String, body: T, time: Long, status: Int)
  case class ApiException(request: HttpRequest, response: ByteString, errorName: Option[String], cause: Throwable = null) extends IOException(s"Cloud.Mail.Ru API request failed: ${request.uri} (${errorName.getOrElse(response.utf8String)})", cause)

  case class Space(overquota: Boolean, used: Int, total: Int) {
    override def toString: String = {
      def asGb(i: Int) = f"${i.toDouble / 1024}%.2f GB"
      s"[${asGb(used)} of ${asGb(total)} used]"
    }
  }

  object EntityPath {
    implicit def apply(str: String): EntityPath = {
      EntityPath(str.split("/").filter(_.nonEmpty))
    }

    val root: EntityPath = "/"
  }

  case class EntityPath(path: Seq[String]) {
    def /(str: String) = {
      copy(path :+ str)
    }

    def parent = {
      copy(path.dropRight(1))
    }

    def name: String = {
      path.lastOption.getOrElse("")
    }

    override def toString: String = {
      path.mkString("/", "/", "")
    }

    def toURLPath: String = {
      path.map(URLEncoder.encode(_, "UTF-8").replaceAllLiterally("+", "%20")).mkString("/")
    }
  }

  trait Entity {
    def `type`: String
    def kind: String
    def name: String
    def path: String
    def size: Long
    def rev: Int
    def grev: Int
  }

  case class EntityCount(folders: Int = 0, files: Int = 0) {
    override def toString: String = {
      s"[$folders folders, $files files]"
    }
  }
  object EntityCount {
    val empty = EntityCount()
  }
  case class File(`type`: String, kind: String, name: String, @key("home") path: String, size: Long, hash: String, rev: Int = 0, grev: Int = 0) extends Entity {
    require(`type` == "file")
  }
  case class Folder(`type`: String, kind: String, name: String, @key("home") path: String, size: Long, tree: String, rev: Int = 0, grev: Int = 0, count: EntityCount = EntityCount.empty, list: Seq[Entity] = Nil) extends Entity {
    require(`type` == "folder")
  }

  case class Node(count: Int, url: String)
  case class Nodes(get: Seq[Node], upload: Seq[Node]) {
    def random: (String, String) = {
      require(get.nonEmpty && upload.nonEmpty)
      (get(Random.nextInt(get.length)).url, upload(Random.nextInt(upload.length)).url)
    }
  }

  case class ApiCsrfToken(token: String)
  case class CsrfToken(pageId: String, token: String)
  case class Session(email: String, cookies: Seq[HttpCookie]) {
    def header: Cookie = {
      Cookie(cookies.map(_.pair).toVector)
    }
  }
}
