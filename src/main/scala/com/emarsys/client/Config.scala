package com.emarsys.client


trait Config {

  import pureconfig.{CamelCase, ConfigFieldMapping, ProductHint, loadConfigOrThrow}

  implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

  case class EmsApiConfig(
                     suite: SuiteConfig,
                     segmentRegistry: SegmentRegistryConfig,
                     predict: PredictConfig,
                     clientRetryCount: Int,
                     relationalData: RelationalDataConfig,
                     restClient: RestClientConfig
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
                                    basePath: String,
                                    serviceName: String = "relational-data"
                                  )

  case class RestClientConfig(
                             errorOnFail: Boolean = true
                             )

  val emsApi : EmsApiConfig =  loadConfigOrThrow[EmsApiConfig]("ems-api")

}

object Config extends Config
