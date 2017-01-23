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

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.StdIn
import scala.language.{implicitConversions, postfixOps}
import scala.util.Random

object MailRuApiData {
  case class ApiResponse[T](email: String, body: T, time: Long, status: Int)

  case class Space(overquota: Boolean, used: Int, total: Int) {
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

  private val AUTH_URL = "https://auth.mail.ru/cgi-bin/auth"
  private val AUTH_SDC_URL = "https://auth.mail.ru/sdc?from=https%3A%2F%2Fcloud.mail.ru%2F"
  private val CLOUD_MAIL_RU = "https://cloud.mail.ru/"

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
    val request = HttpRequest(HttpMethods.POST, AUTH_URL, entity = FormData(
      "new_auth_form" → "1",
      "page" → CLOUD_MAIL_RU,
      "Domain" → domain,
      "Login" → login,
      "Password" → password
    ).toEntity)

    http.singleRequest(request)
      .filter(_.headers.exists(h ⇒ h.is("location") && h.value().startsWith(CLOUD_MAIL_RU)))
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
    val request = HttpRequest(HttpMethods.GET, AUTH_SDC_URL, Vector(session.header))
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
    get[Space]("user/space")
  }

  def file(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[File] = {
    get[File]("file", "home" → path.toString)
  }

  def folder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[Folder] = {
    get[Folder]("folder", "home" → path.toString)
  }

  def nodes(implicit session: Session, token: CsrfToken): Future[Nodes] = {
    get[Nodes]("dispatcher")
  }

  def delete(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[String] = {
    post[String]("file/remove", "home" → path.toString)
  }

  def download(path: EntityPath)(implicit session: Session, token: CsrfToken): Source[ByteString, NotUsed] = {
    Source.fromFuture(for (f ← file(path); n ← nodes) yield (f, n))
      .map { case (file, nodes) ⇒ (file, downloadRequest(nodes, path)) }
      .flatMapConcat { case (file, request) ⇒ Source.fromFuture(http.singleRequest(request)).map(r ⇒ (file, r)) }
      .flatMapConcat { case (file, response) ⇒ response.entity.withSizeLimit(file.size).dataBytes }
  }

  def upload(path: EntityPath, data: Source[ByteString, _])(implicit session: Session, token: CsrfToken): Future[String] = {
    nodes
      .flatMap(uploadRequest(_, path, data))
      .flatMap(http.singleRequest(_))
      .flatMap(_.entity.withSizeLimit(1000).dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { bs ⇒
        val regex = "(\\w+);(\\d+)".r.unanchored
        bs.utf8String match {
          case regex(hash, size) ⇒
            hash → size

          case _ ⇒
            throw new IllegalArgumentException("Invalid response")
        }
      }
      .flatMap { case (hash, size) ⇒ post[String]("file/add", "home" → path.toString, "hash" → hash, "size" → size) }
  }

  def createFolder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[String] = {
    post[String]("folder/add", "home" → path.toString)
  }

  private def downloadRequest(nodes: Nodes, path: EntityPath)(implicit session: Session): HttpRequest = {
    val (dlNode, _) = nodes.random
    HttpRequest(HttpMethods.GET, dlNode + path.path.map(URLEncoder.encode(_, "UTF-8")).mkString("/"), Vector(session.header))
  }

  private def uploadRequest(nodes: Nodes, path: EntityPath, data: Source[ByteString, _])(implicit session: Session): Future[HttpRequest] = {
    val (_, ulNode) = nodes.random
    val fileName = path.path.last
    val formData = Multipart.FormData(Multipart.FormData.BodyPart("file", HttpEntity.IndefiniteLength(ContentTypes.`application/octet-stream`, data), Map("filename" → fileName)))
    val url = ulNode + path.path.dropRight(1).map(URLEncoder.encode(_, "UTF-8")).mkString("/")
    formData.toStrict(10 seconds)
      .map(data ⇒ HttpRequest(HttpMethods.POST, url, Vector(session.header), data.toEntity()))
  }

  private def apiMethod(method: String) = {
    Uri(CLOUD_MAIL_RU).withPath(Uri.Path("/api/v2/" + method))
  }

  def get[T: Reader](method: String, data: (String, String)*)(implicit session: Session, csrfToken: CsrfToken): Future[T] = {
    execute[T](getRequest(method, data:_*))
  }

  def post[T: Reader](method: String, data: (String, String)*)(implicit session: Session, csrfToken: CsrfToken): Future[T] = {
    execute[T](postRequest(method, data:_*))
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

  private val testJpg = Paths.get("temp.jpg")

  val folderResult = Await.result(MailRuCloud.createFolder("Testfolder"), Duration.Inf)
  println(folderResult)

  val deleteResult = Await.result(MailRuCloud.delete("test.jpg"), Duration.Inf)
  println(deleteResult)

  val uploadResult = Await.result(MailRuCloud.upload("test.jpg", FileIO.fromPath(testJpg)), Duration.Inf)
  println(uploadResult)

  Iterator.continually(StdIn.readLine())
    .takeWhile(null ne)
    .map(file ⇒ MailRuCloud.download(file))
    .foreach(_.runWith(FileIO.toPath(testJpg))(MailRuCloud.actorMaterializer))
}
