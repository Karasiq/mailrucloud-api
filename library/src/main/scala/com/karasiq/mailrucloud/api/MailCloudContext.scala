package com.karasiq.mailrucloud.api

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer

import com.karasiq.mailrucloud.api.MailCloudTypes.ApiException

trait MailCloudContext {
  implicit val actorSystem: ActorSystem
  implicit val actorMaterializer: ActorMaterializer
  implicit val executionContext: ExecutionContext
  def doHttpRequest(request: HttpRequest): Future[HttpResponse]
}

object MailCloudContext {
  private final class DefaultMailCloudContext(_actorSystem: ActorSystem) extends MailCloudContext {
    implicit val actorSystem = _actorSystem
    implicit val actorMaterializer = ActorMaterializer()
    implicit val executionContext = actorSystem.dispatcher
    val akkaHttp = Http()

    def doHttpRequest(request: HttpRequest) = {
      akkaHttp.singleRequest(request).flatMap { response ⇒
        if (!response.status.isSuccess()) toFailedFuture(request, response)
        else Future.successful(response)
      }
    }

    private[this] def toFailedFuture(request: HttpRequest, response: HttpResponse) = {
      response.entity.toStrict(5 seconds).flatMap { entity ⇒
        Future.failed(ApiException(request, entity.data, None, new IllegalArgumentException("HTTP failure: " + response.status)))
      }
    }
  }

  def apply()(implicit as: ActorSystem): MailCloudContext = new DefaultMailCloudContext(as)
}