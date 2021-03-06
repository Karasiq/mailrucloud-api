package com.karasiq.mailrucloud.api

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer

import com.karasiq.mailrucloud.api.impl.DefaultMailCloudContext

trait MailCloudContext {
  implicit val actorSystem: ActorSystem
  implicit val materializer: Materializer
  implicit val executionContext: ExecutionContext
  def doHttpRequest(request: HttpRequest, handleRedirects: Boolean = false): Future[HttpResponse]
}

trait MailCloudContextProvider {
  val context: MailCloudContext
}

object MailCloudContext {
  def apply()(implicit as: ActorSystem): MailCloudContext = new DefaultMailCloudContext
}
