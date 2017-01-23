package com.karasiq.mailrucloud.test

import java.net.URLEncoder
import java.nio.file.Paths

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import com.karasiq.mailrucloud.test.MailRuApiData.EntityPath
import upickle.Js
import upickle.Js.Value
import upickle.default._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.StdIn
import scala.language.{implicitConversions, postfixOps}
import scala.util.Random

object MailRuApiData {
  case class ApiResponse[T](email: String, body: T, time: Long, status: Int)

  case class Space(@key("overquota") overused: Boolean, used: Int, total: Int) {
    override def toString: String = {
      def asGb(i: Int) = f"${i.toDouble / 1024}%.2f GB"
      s"[${asGb(used)} of ${asGb(total)} used]"
    }
  }

  object EntityPath {
    implicit def apply(str: String): EntityPath = {
      EntityPath(str.split("/"))
    }

    val root: EntityPath = "/"
  }

  case class EntityPath(path: Seq[String]) {
    override def toString: String = {
      path.mkString("/", "/", "")
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

object MailRuCloud {
  import MailRuApiData._

  private implicit val LongRW = ReadWriter[Long](
    value ⇒ Js.Num(value),
    { case Js.Num(value) ⇒ value.toLong }
  )

  private implicit val EntityRW = new Writer[Entity] with Reader[Entity] {
    private implicit val EntityRW = this

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

  private implicit val EntityPathRW = ReadWriter[EntityPath](
    path ⇒ Js.Str(path.toString),
    { case Js.Str(str) ⇒ EntityPath(str) }
  )

  private val CLOUD_MAIL_RU: Uri = "https://cloud.mail.ru/"

  implicit val actorSystem = ActorSystem("mailrucloud-test")
  import actorSystem.dispatcher
  implicit val actorMaterializer = ActorMaterializer()
  val http = Http()

  def login(email: String, password: String): Future[Session] = {
    val (login, domain) = email.split("@", 2) match {
      case Array(login, domain) ⇒
        login → domain

      case Array(login) ⇒
        login → "mail.ru"
    }
    val request = HttpRequest(HttpMethods.POST, "https://auth.mail.ru/cgi-bin/auth", entity = FormData(
      "new_auth_form" → "1",
      "page" → "https://cloud.mail.ru/",
      "Domain" → domain,
      "Login" → login,
      "Password" → password
    ).toEntity)

    http.singleRequest(request)
      .filter(_.headers.exists(h ⇒ h.is("location") && h.value().startsWith("https://cloud.mail.ru/")))
      .map(response ⇒ Session(email, response.headers.collect { case `Set-Cookie`(cookie) ⇒ cookie }))
      .flatMap(addSdcToken)
  }

  private def addSdcToken(session: Session): Future[Session] = {
    def extractSdc(response: HttpResponse) = {
      response.headers.collectFirst { case `Set-Cookie`(cookie) if cookie.name == "sdcs" ⇒ cookie }
    }
    def extractLocation(response: HttpResponse) = {
      response.headers.collectFirst { case Location(location) ⇒ location }
    }
    def newSession(response: HttpResponse) = {
      extractSdc(response).fold(session)(cookie ⇒ session.copy(cookies = session.cookies :+ cookie))
    }
    val request = HttpRequest(HttpMethods.GET, "https://auth.mail.ru/sdc?from=https%3A%2F%2Fcloud.mail.ru%2F", Vector(session.header))
    http.singleRequest(request).flatMap { response ⇒
      val location = extractLocation(response)
      extractSdc(response) match {
        case None if location.nonEmpty ⇒
          http.singleRequest(request.copy(uri = location.get)).map(newSession)

        case _ ⇒
          Future.successful(newSession(response))
      }
    }
  }

  def csrfToken(implicit session: Session): Future[CsrfToken] = {
    def extractHtmlPageId(response: HttpResponse): Future[String] = {
      val regex = "pageId: '(\\w+)'".r
      response.entity
        .withSizeLimit(1048576)
        .dataBytes.fold(ByteString.empty)(_ ++ _)
        .mapConcat(bs ⇒ regex.findFirstMatchIn(bs.utf8String).map(_.group(1)).toList)
        .runWith(Sink.head)
    }
    val homePageRequest = HttpRequest(HttpMethods.GET, CLOUD_MAIL_RU, Vector(session.header))

    http.singleRequest(homePageRequest)
      .flatMap(extractHtmlPageId)
      .flatMap { pageId ⇒
        val csrfTokenRequest = HttpRequest(HttpMethods.POST, apiMethod("tokens/csrf"), Vector(session.header), createApiFormData(session, "x-page-id" → pageId).toEntity)
        execute[ApiCsrfToken](csrfTokenRequest)
          .map(r ⇒ CsrfToken(pageId, r.token))
      }
  }

  def space(implicit session: Session, token: CsrfToken): Future[Space] = {
    execute[Space](getRequest("user/space"))
  }

  def file(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[File] = {
    execute[File](getRequest("file", "home" → path.toString))
  }

  def folder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[Folder] = {
    execute[Folder](getRequest("folder", "home" → path.toString))
  }

  def nodes(implicit session: Session, token: CsrfToken): Future[Nodes] = {
    execute[Nodes](getRequest("dispatcher"))
  }

  def download(path: EntityPath)(implicit session: Session, token: CsrfToken): Source[ByteString, NotUsed] = {
    Source.fromFuture(for (f ← file(path); n ← nodes) yield (f, n))
      .map{ case (file, nodes) ⇒ (file, downloadRequest(nodes, path)) }
      .flatMapConcat { case (file, request) ⇒ Source.fromFuture(http.singleRequest(request)).map(r ⇒ (file, r)) }
      .flatMapConcat { case (file, response) ⇒ response.entity.withSizeLimit(file.size).dataBytes }
  }

  private def downloadRequest(nodes: Nodes, path: EntityPath)(implicit session: Session): HttpRequest = {
    val (dlNode, _) = nodes.random
    HttpRequest(HttpMethods.GET, dlNode + path.path.map(URLEncoder.encode(_, "UTF-8")).mkString("/"), Vector(session.header))
  }

  private def apiMethod(method: String) = {
    CLOUD_MAIL_RU.withPath(Uri.Path("/api/v2/" + method))
  }

  private def execute[T: Reader](request: HttpRequest): Future[T] = {
    http.singleRequest(request)
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { bs ⇒
        val response = read[ApiResponse[T]](bs.utf8String)
        if (response.status != 200) throw new IllegalArgumentException(s"API request failed: $request ($response)")
        response.body
      }
  }

  private def getRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.GET, apiMethod(method).withQuery(createApiFormData(session, token, data:_*).fields), Vector(session.header))
  }

  private def postRequest(method: String, data: (String, String)*)(implicit session: Session, token: CsrfToken): HttpRequest = {
    HttpRequest(HttpMethods.POST, apiMethod(method), Vector(session.header), createApiFormData(session, token, data:_*).toEntity)
  }

  private def createApiFormData(session: Session, data: (String, String)*): FormData = {
    FormData(Seq(
      "api" → "2",
      "build" → "release_CLOUDWEB-7279_39-4.201701101645",
      "email" → session.email,
      "x-email" → session.email
    ) ++ data:_*)
  }

  private def createApiFormData(session: Session, token: CsrfToken, data: (String, String)*): FormData = {
    createApiFormData(session, Seq("x-page-id" → token.pageId, "token" → token.token) ++ data:_*)
  }
}

object Main extends App {
  implicit val session = Await.result(MailRuCloud.login(sys.props("mailru.email"), sys.props("mailru.password")), Duration.Inf)
  println(session)

  implicit val token = Await.result(MailRuCloud.csrfToken, Duration.Inf)
  println(token)

  val space = Await.result(MailRuCloud.space, Duration.Inf)
  println(space)

  val nodes = Await.result(MailRuCloud.nodes, Duration.Inf)
  println(nodes)

  val root = Await.result(MailRuCloud.folder(EntityPath.root), Duration.Inf)
  println(root)

  Iterator.continually(StdIn.readLine())
    .takeWhile(null ne)
    .map(file ⇒ MailRuCloud.download(file))
    .foreach(_.runWith(FileIO.toPath(Paths.get("temp.jpg")))(MailRuCloud.actorMaterializer))
}
