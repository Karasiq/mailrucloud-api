package com.karasiq.mailrucloud.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

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
      akkaHttp.singleRequest(request)
    }
  }

  def apply()(implicit as: ActorSystem): MailCloudContext = new DefaultMailCloudContext(as)
}