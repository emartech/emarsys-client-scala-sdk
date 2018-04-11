package com.emarsys.client


trait Config {

  import pureconfig.ProductHint
  import pureconfig.ConfigFieldMapping
  import pureconfig.CamelCase
  import pureconfig.loadConfigOrThrow

  implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

  case class EmsApiConfig(
                     suite: SuiteConfig,
                     segmentRegistry: SegmentRegistryConfig,
                     predict: PredictConfig,
                     clientRetryCount: Int,
                     relationalData: RelationalDataConfig
                   )

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
                                    protocol : String,
                                    host: String,
                                    port: Int,
                                    serviceName: String = "segment-registry"
                                  )
  case class RelationalDataConfig(
                                    protocol : String,
                                    host: String,
                                    port: Int,
                                    serviceName: String = "relational-data"
                                  )

  val emsApi : EmsApiConfig =  loadConfigOrThrow[EmsApiConfig]("ems-api")

}

object Config extends Config
