package com.karasiq.mailrucloud.api.impl.json

import scala.concurrent.Future

import akka.http.scaladsl.model.HttpRequest
import akka.util.ByteString
import play.api.libs.json.Json

import com.karasiq.mailrucloud.api._

abstract class MailCloudJsonApi(context: MailCloudContext, constants: MailCloudConstants, forms: MailCloudForms,
                                urls: MailCloudUrls, requests: MailCloudRequests) extends MailCloudApi with MailCloudJsonFormatsProvider {

  import context._

  import MailCloudTypes._

  override type ResponseParser[T] = formats.FormatT[T]

  def executeApiRequest[T: ResponseParser](request: HttpRequest): Future[T] = {
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

trait MailCloudJsonApiProvider extends MailCloudApiProvider { self: MailCloudContextProvider with MailCloudConstantsProvider with MailCloudFormsProvider with MailCloudUrlsProvider with MailCloudRequestsProvider ⇒
  lazy val api = new MailCloudJsonApi(context, constants, forms, urls, requests) with BaseMailCloudApi
}
