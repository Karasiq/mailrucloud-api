package com.karasiq.mailrucloud.api

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

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

    def extractError(bs: ByteString): Option[ApiException] = {
      val response = Json.parse(bs.toArray)
      (response \ "status").asOpt[Int].filterNot(_ == 200).map { _ ⇒
        val errorStr = (response \ "body" \ "home" \ "error").asOpt[String]
        ApiException(request, bs, errorStr)
      }
    }

    doHttpRequest(request)
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .recoverWith { case e: ApiException ⇒ Future.failed(extractError(e.response).map(_.copy(cause = e)).getOrElse(e)) }
      .map { bs ⇒
        extractError(bs).foreach(throw _)
        val json = Json.parse(bs.toArray)
        // json.as[ApiResponse[T]].body
        (json \ "body").as[T]
      }
  }
}

object MailCloudApi {
  trait MailCloudApiLike extends MailCloudApi with MailCloudConstants with MailCloudForms with MailCloudUrls with MailCloudRequests
  trait DefaultMailCloudApiLike extends MailCloudApiLike with MailCloudJsonApi with DefaultMailCloudConstants with DefaultMailCloudUrls with DefaultMailCloudForms with DefaultMailCloudRequests
  final class DefaultMailCloudApi extends DefaultMailCloudApiLike

  def apply()(implicit as: ActorSystem): DefaultMailCloudApi = new DefaultMailCloudApi
}