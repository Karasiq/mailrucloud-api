package com.karasiq.mailrucloud.api

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

import akka.http.scaladsl.model.HttpRequest

import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, Session}

trait MailCloudApi {
  type ResponseParser[T]

  def executeApiRequest[T: ResponseParser](request: HttpRequest): Future[T]
  def get[T: ResponseParser](method: String, data: (String, String)*)(implicit session: Session, csrfToken: CsrfToken): Future[T]
  def post[T: ResponseParser](method: String, data: (String, String)*)(implicit session: Session, csrfToken: CsrfToken): Future[T]
}

trait MailCloudApiProvider { self: MailCloudRequestsProvider ⇒
  val api: MailCloudApi

  trait BaseMailCloudApi extends MailCloudApi {
    def get[T: ResponseParser](method: String, data: (String, String)*)(implicit session: Session, csrfToken: CsrfToken): Future[T] = {
      executeApiRequest[T](requests.getRequest(method, data:_*))
    }

    def post[T: ResponseParser](method: String, data: (String, String)*)(implicit session: Session, csrfToken: CsrfToken): Future[T] = {
      executeApiRequest[T](requests.postRequest(method, data:_*))
    }
  }
}