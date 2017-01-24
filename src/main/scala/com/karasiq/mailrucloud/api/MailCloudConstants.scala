package com.karasiq.mailrucloud.api

import com.karasiq.mailrucloud.api.MailCloudTypes.EntityPath

import scala.language.postfixOps

trait MailCloudConstants { 
  val ADVERTISED_BUILD = sys.props.getOrElse("mailru.cloud.build", "release_CLOUDWEB-7279_39-4.201701101645")
  val ROOT_FOLDER = EntityPath.root
}
