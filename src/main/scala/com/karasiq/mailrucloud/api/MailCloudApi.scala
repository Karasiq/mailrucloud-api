package com.karasiq.mailrucloud.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

trait MailCloudApi { self: MailCloudConstants with MailCloudForms with MailCloudUrls with MailCloudRequests with MailCloudJson with MailCloudContext ⇒
  import MailCloudTypes._

  def get[T: Reader](method: String, data: (String, String)*)(implicit session: Session, csrfToken: CsrfToken): Future[T] = {
    executeApiRequest[T](getRequest(method, data:_*))
  }

  def post[T: Reader](method: String, data: (String, String)*)(implicit session: Session, csrfToken: CsrfToken): Future[T] = {
    executeApiRequest[T](postRequest(method, data:_*))
  }

  private def extractError(response: ApiResponse[upickle.Js.Obj]): Option[String] = {
    Try(response.body("home")("error").str).toOption
  }

  def executeApiRequest[T: Reader](request: HttpRequest): Future[T] = {
    doHttpRequest(request)
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { bs ⇒
        val responseStr = bs.utf8String
        val response = read[ApiResponse[upickle.Js.Obj]](responseStr)
        if (response.status != 200)
          throw ApiException(request, response, extractError(response))
        readJs[T](response.body)
      }
  }
}

object MailCloudApi {
  trait DefaultMailCloudApiTrait extends MailCloudApi with MailCloudConstants with MailCloudForms with MailCloudUrls with MailCloudRequests with MailCloudJson with MailCloudContext
  class DefaultMailCloudApi(as: ActorSystem) extends DefaultMailCloudApiTrait {
    final implicit val actorSystem = as
    final implicit val actorMaterializer = ActorMaterializer()
    final implicit val executionContext = actorSystem.dispatcher
    final val akkaHttp = Http()

    def doHttpRequest(request: HttpRequest) = {
      akkaHttp.singleRequest(request)
    }
  }

  def apply()(implicit as: ActorSystem): DefaultMailCloudApi = new DefaultMailCloudApi(as)
}