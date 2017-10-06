package com.karasiq.mailrucloud.api

import scala.language.postfixOps

import com.karasiq.mailrucloud.api.MailCloudTypes.EntityPath

trait MailCloudConstants { 
  val AdvertisedBuild: String
  val RootFolder: EntityPath
}

trait DefaultMailCloudConstants extends MailCloudConstants {
  val AdvertisedBuild = sys.props.getOrElse("mailru.cloud.build", "hotfix_CLOUDWEB-7710_50-0-1.201710021758")
  val RootFolder = EntityPath.root
}
