package com.karasiq.mailrucloud.api.impl

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import com.karasiq.mailrucloud.api.MailCloudContext
import com.karasiq.mailrucloud.api.MailCloudTypes.ApiException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class DefaultMailCloudContext(implicit val actorSystem: ActorSystem) extends MailCloudContext {
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  val akkaHttp = Http()

  def doHttpRequest(request: HttpRequest, handleRedirects: Boolean): Future[HttpResponse] = {
    akkaHttp.singleRequest(request).flatMap { response ⇒
      if (!response.status.isSuccess()) toFailedFuture(request, response)
      else if (handleRedirects && response.status.isRedirection()) {
        val newUri = response.header[Location].get.uri
        response.discardEntityBytes()
        doHttpRequest(request.copy(uri = newUri))
      } else Future.successful(response)
    }
  }

  private[this] def toFailedFuture(request: HttpRequest, response: HttpResponse) = {
    response.entity.toStrict(5 seconds).flatMap { entity ⇒
      Future.failed(ApiException(request, entity.data, None, new IllegalArgumentException("HTTP failure: " + response.status)))
    }
  }
}
