package com.karasiq.mailrucloud.api

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}
import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.util.ByteString
import play.api.libs.json.{Json, Reads}

import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, Session}

trait MailCloudApi { self: MailCloudRequests ⇒
  type ResponseParser[T]
  def executeApiRequest[T: ResponseParser](request: HttpRequest)(implicit ctx: MailCloudContext): Future[T]

  def get[T: ResponseParser](method: String, data: (String, String)*)(implicit ctx: MailCloudContext, session: Session, csrfToken: CsrfToken): Future[T] = {
    executeApiRequest[T](getRequest(method, data:_*))
  }

  def post[T: ResponseParser](method: String, data: (String, String)*)(implicit ctx: MailCloudContext, session: Session, csrfToken: CsrfToken): Future[T] = {
    executeApiRequest[T](postRequest(method, data:_*))
  }
}

trait MailCloudJsonApi extends MailCloudApi with MailCloudJson { self: MailCloudConstants with MailCloudForms with MailCloudUrls with MailCloudRequests ⇒
  import MailCloudTypes._

  override type ResponseParser[T] = Reads[T]

  def executeApiRequest[T: ResponseParser](request: HttpRequest)(implicit ctx: MailCloudContext): Future[T] = {
    import ctx._
    doHttpRequest(request)
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { bs ⇒
        val responseStr = bs.utf8String
        val response = Json.parse(responseStr)
        if (!(response \ "status").asOpt[Int].contains(200)) {
          val errorStr = (response \ "body" \ "home" \ "error").asOpt[String]
          throw ApiException(request, bs, errorStr)
        }
        response.as[ApiResponse[T]].body
      }
  }
}

object MailCloudApi {
  trait MailCloudApiLike extends MailCloudApi with MailCloudConstants with MailCloudForms with MailCloudUrls with MailCloudRequests
  trait DefaultMailCloudApiLike extends MailCloudApiLike with MailCloudJsonApi with DefaultMailCloudConstants with DefaultMailCloudUrls with DefaultMailCloudForms with DefaultMailCloudRequests
  final class DefaultMailCloudApi extends DefaultMailCloudApiLike

  def apply()(implicit as: ActorSystem): DefaultMailCloudApi = new DefaultMailCloudApi
}