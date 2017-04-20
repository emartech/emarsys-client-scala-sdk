package com.emarsys.client

import com.typesafe.config.ConfigFactory

trait Config {

  private val config = ConfigFactory.load()

  object suiteConfig {
    val suiteConfig  = config.getConfig("ems-api.suite")
    val host         = suiteConfig.getString("host")
    val port         = 443
    val suiteApiPath = suiteConfig.getString("apiPath")
    val serviceName  = "suiteApi"
  }

  object predictConfig {
    val predictConfig = config.getConfig("ems-api.predict")
    val host          = predictConfig.getString("host")
    val port          = predictConfig.getInt("port")
    val serviceName   = "predict"
  }

  object segmentRegistryConfig {
    val registryConfig = config.getConfig("ems-api.segment-registry")
    val host           = registryConfig.getString("host")
    val port           = registryConfig.getInt("port")
    val serviceName    = "segment-registry"
  }

  val restClientRetryCount = config.getInt("ems-api.clientRetryCount")
}

object Config extends Config