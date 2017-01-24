package com.karasiq.mailrucloud.api

import akka.actor.ActorSystem
import akka.http.scaladsl.HttpExt
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

trait MailCloudContext {
  implicit val actorSystem: ActorSystem
  implicit val actorMaterializer: ActorMaterializer
  implicit val executionContext: ExecutionContext
  implicit val http: HttpExt
}
