package com.karasiq.mailrucloud.api.impl

import com.karasiq.mailrucloud.api.{MailCloudConstants, MailCloudConstantsProvider}
import com.karasiq.mailrucloud.api.MailCloudTypes.EntityPath

object DefaultMailCloudConstants extends MailCloudConstants {
  val AdvertisedBuild = sys.props.getOrElse("mailru.cloud.build", "cloudweb-11600-72-4-0.202011271755")
  val RootFolder = EntityPath.root
}

trait DefaultMailCloudConstantsProvider extends MailCloudConstantsProvider {
  val constants = DefaultMailCloudConstants
}
