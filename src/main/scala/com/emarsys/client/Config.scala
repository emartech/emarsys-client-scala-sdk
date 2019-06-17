package com.emarsys.client

import scala.concurrent.duration.FiniteDuration
import pureconfig.{CamelCase, ConfigFieldMapping}
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._

trait Config {

  import pureconfig.loadConfigOrThrow

  implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

  case class EmsApiConfig(
      suite: SuiteConfig,
      segmentRegistry: SegmentRegistryConfig,
      predict: PredictConfig,
      retry: RetryConfig,
      relationalData: RelationalDataConfig,
      restClient: RestClientConfig
  )

  case class RetryConfig(maxRetries: Int, dontRetryAfter: FiniteDuration, initialRetryDelay: FiniteDuration)

  case class SuiteConfig(
      protocol: String = "https",
      host: String,
      port: Int = 443,
      apiPath: String,
      serviceName: String = "suiteApi"
  )

  case class PredictConfig(
      protocol: String = "https",
      host: String,
      port: Int,
      serviceName: String = "predict"
  )

  case class SegmentRegistryConfig(
      protocol: String,
      host: String,
      port: Int,
      serviceName: String = "segment-registry"
  )
  case class RelationalDataConfig(
      protocol: String,
      host: String,
      port: Int,
      basePath: String,
      serviceName: String = "relational-data"
  )

  case class RestClientConfig(
      errorOnFail: Boolean = true
  )

//  private implicit def

  val emsApi: EmsApiConfig = loadConfigOrThrow[EmsApiConfig]("ems-api")

}

object Config extends Config
