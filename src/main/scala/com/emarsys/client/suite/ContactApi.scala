package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.emarsys.formats.SuiteSdkFormats._
import com.emarsys.escher.akka.http.config.EscherConfig
import com.emarsys.client.suite.DataTransformers._

import scala.concurrent.{ExecutionContextExecutor, Future}

trait ContactApi extends SuiteClient {
  val retryConfig = defaultRetryConfig.copy(maxRetries = 0)

  def getData(customerId: Int, entity: GetDataRequest): Future[GetDataResponse] = {
    val path    = "contact/getdata"
    val request = RequestBuilding.Post(Uri(baseUrl(customerId) + path), entity)

    runSuiteRequest[GetDataRawResponseData](request, retryConfig) map getDataResponseTransformer
  }
}

object ContactApi {
  def apply(eConfig: EscherConfig)(
      implicit
      sys: ActorSystem,
      ex: ExecutionContextExecutor
  ): ContactApi = {
    new SuiteClient with ContactApi {
      implicit override val system   = sys
      implicit override val executor = ex
      override val escherConfig      = eConfig
    }
  }
}
