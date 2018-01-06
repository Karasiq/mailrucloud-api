package com.karasiq.mailrucloud.api.impl

import com.karasiq.mailrucloud.api.{MailCloudConstants, MailCloudConstantsProvider}
import com.karasiq.mailrucloud.api.MailCloudTypes.EntityPath

object DefaultMailCloudConstants extends MailCloudConstants {
  val AdvertisedBuild = sys.props.getOrElse("mailru.cloud.build", "hotfix_CLOUDWEB-7710_50-0-1.201710021758")
  val RootFolder = EntityPath.root
}

trait DefaultMailCloudConstantsProvider extends MailCloudConstantsProvider {
  val constants = DefaultMailCloudConstants
}
