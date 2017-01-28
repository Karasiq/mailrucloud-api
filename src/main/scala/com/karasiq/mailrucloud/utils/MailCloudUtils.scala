package com.karasiq.mailrucloud.utils

import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart, RequestEntity}
import akka.util.ByteString

import scala.language.postfixOps

object MailCloudUtils {
  // Allows to create streaming upload request with known Content-Length
  // WARNING: Breaks if akka-http multipart code changed
  def instantSizedMultipartUpload(fName: String, entity: HttpEntity.Default): RequestEntity = {
    val fixedBoundary = "TVFamZeGky6lp+58hBM6zK-N"
    val fullEntitySize = {
      val CRLF = 2
      val boundary = 26 // --TVFamZeGky6lp+58hBM6zK-N
      val contentType = 14 + entity.contentType.toString().length // Content-Type: %CONTENT_TYPE%
      val fileName = fName.length // %FILENAME%
      val contentDisposition = 56 + fileName // Content-Disposition: form-data; filename="%FILENAME%"; name="file"
      val file = entity.contentLength
      val nameContentType = 39 // Content-Type: text/plain; charset=UTF-8
      val nameContentDisposition = 44 // Content-Disposition: form-data; name="_file"
      val endBoundary = boundary + 2 // --TVFamZeGky6lp+58hBM6zK-N--
      // -----------------------------------------------------------
      boundary + CRLF +
        contentType + CRLF +
        contentDisposition + CRLF + CRLF +
        file + CRLF +
        boundary + CRLF +
        nameContentType + CRLF +
        nameContentDisposition + CRLF + CRLF +
        fileName + CRLF +
        endBoundary
    }

    val formData = Multipart.FormData(
      Multipart.FormData.BodyPart("file", entity, Map("filename" → fName)),
      Multipart.FormData.BodyPart.Strict("_file", HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString(fName)))
    )
    val chunked = formData.toEntity(fixedBoundary).asInstanceOf[Chunked]
    val chunkedBytes = chunked.chunks.map(_.data()).takeWhile(_.nonEmpty)
    HttpEntity.Default(chunked.contentType, fullEntitySize, chunkedBytes)
  }
}
