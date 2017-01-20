package com.emarsys.client

import com.typesafe.config.ConfigFactory

object Config {

  private val config = ConfigFactory.load()

  object suiteConfig {
    val suiteConfig  = config.getConfig("ems-api.suite")
    val host         = suiteConfig.getString("host")
    val port         = 443
    val suiteApiPath = suiteConfig.getString("apiPath")
    val serviceName  = "suiteApi"
  }

}
