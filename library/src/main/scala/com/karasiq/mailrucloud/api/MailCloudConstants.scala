package com.karasiq.mailrucloud.api

import scala.language.postfixOps

import com.karasiq.mailrucloud.api.MailCloudTypes.EntityPath

trait MailCloudConstants { 
  val AdvertisedBuild: String
  val RootFolder: EntityPath
}

trait MailCloudConstantsProvider {
  val constants: MailCloudConstants
}
