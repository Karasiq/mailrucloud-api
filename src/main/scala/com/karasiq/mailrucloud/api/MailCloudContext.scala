package com.karasiq.mailrucloud.api

import akka.actor.ActorSystem
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
