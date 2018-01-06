package com.karasiq.mailrucloud.api

import scala.concurrent.Future
import scala.language.postfixOps

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.RequestEntity
import akka.stream.scaladsl.Source
import akka.util.ByteString

import com.karasiq.mailrucloud.api.MailCloudTypes._
import com.karasiq.mailrucloud.api.impl.MailCloudDefaults
import com.karasiq.mailrucloud.api.impl.json.MailCloudJsonClient

trait MailCloudClient {
  def login(email: String, password: String): Future[Session]
  def csrfToken(implicit session: Session): Future[CsrfToken]
  def space(implicit session: Session, token: CsrfToken): Future[Space]
  def file(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[File]
  def folder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[Folder]
  def nodes(implicit session: Session, token: CsrfToken): Future[Nodes]
  def delete(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[EntityPath]
  def download(path: EntityPath)(implicit nodes: Nodes, session: Session, token: CsrfToken): Source[ByteString, NotUsed]
  def upload(path: EntityPath, data: RequestEntity)(implicit nodes: Nodes, session: Session, token: CsrfToken): Future[EntityPath]
  def createFolder(path: EntityPath)(implicit session: Session, token: CsrfToken): Future[EntityPath]
}

object MailCloudClient {
  final class DefaultMailCloudClient(implicit as: ActorSystem) extends MailCloudJsonClient with MailCloudContextProvider with MailCloudDefaults {
    val context = MailCloudContext()
  }

  def apply()(implicit as: ActorSystem): DefaultMailCloudClient = new DefaultMailCloudClient
}