package com.karasiq.mailrucloud.api

import scala.language.postfixOps

import com.karasiq.mailrucloud.api.MailCloudTypes.EntityPath

trait MailCloudConstants { 
  val ADVERTISED_BUILD: String
  val ROOT_FOLDER: EntityPath
}

trait DefaultMailCloudConstants extends MailCloudConstants {
  val ADVERTISED_BUILD = sys.props.getOrElse("mailru.cloud.build", "release_CLOUDWEB-7279_39-4.201701101645")
  val ROOT_FOLDER = EntityPath.root
}
