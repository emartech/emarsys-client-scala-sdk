package com.emarsys.client.relationaldata

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import com.emarsys.client.Config.emsApi.relationalData
import com.emarsys.client.EscherRestClient
import com.emarsys.escher.akka.http.config.EscherConfig
import spray.json.DefaultJsonProtocol._
import spray.json.JsValue

import scala.concurrent.ExecutionContextExecutor

trait RelationalDataApi extends EscherRestClient {

  val serviceName = relationalData.serviceName
  val baseUrl     = Uri(scheme = s"${relationalData.protocol}", authority = Authority(host = Host(relationalData.host))).toString + relationalData.basePath
  val retryConfig = defaultRetryConfig.copy(maxRetries = 0)

  final val customerIdHeader = "x-suite-customerid"

  def insertIgnore(customerId: Int, tableName: String, payload: Seq[Map[String, JsValue]]) = {
    val path: String = s"/tables/$tableName/records"

    val request = RequestBuilding
      .Post(baseUrl + path, payload)
      .addHeader(RawHeader(customerIdHeader, customerId.toString))

    runSigned[String](request, serviceName, List(customerIdHeader), retryConfig)
  }

}

object RelationalDataApi {

  def apply(eConfig: EscherConfig)(
      implicit
      sys: ActorSystem,
      mat: Materializer,
      ex: ExecutionContextExecutor
  ): RelationalDataApi = {

    new RelationalDataApi {
      implicit override val system       = sys
      implicit override val materializer = mat
      implicit override val executor     = ex
      override val escherConfig          = eConfig
    }
  }

}
