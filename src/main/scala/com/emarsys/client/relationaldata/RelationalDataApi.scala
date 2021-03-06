package com.emarsys.client.relationaldata

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.http.scaladsl.model.headers.RawHeader
import com.emarsys.client.Config.emsApi.relationalData
import com.emarsys.client.EscherRestClient
import com.emarsys.escher.akka.http.config.EscherConfig
import spray.json.DefaultJsonProtocol._
import spray.json.JsValue

import scala.concurrent.ExecutionContextExecutor

trait RelationalDataApi extends EscherRestClient {
  val serviceName = relationalData.serviceName
  val baseUrl = Uri(
    scheme = s"${relationalData.protocol}",
    authority = Authority(host = Host(relationalData.host))
  ).toString + relationalData.basePath
  val retryConfig = defaultRetryConfig.copy(maxRetries = 0)

  final val customerIdHeader       = "x-suite-customerid"
  final val forwardedServiceHeader = "X-Forwarded-Service"

  def insertIgnore(customerId: Int, tableName: String, payload: Seq[Map[String, JsValue]], source: Option[String]) = {
    val path: String = s"/tables/$tableName/records"

    var request = RequestBuilding
      .Post(baseUrl + path, payload)
      .addHeader(RawHeader(customerIdHeader, customerId.toString))
    source.foreach(serv => request = request.addHeader(RawHeader(forwardedServiceHeader, serv)))

    runSigned[String](request, serviceName, List(customerIdHeader), retryConfig)
  }
}

object RelationalDataApi {
  def apply(eConfig: EscherConfig)(implicit
      sys: ActorSystem,
      ex: ExecutionContextExecutor
  ): RelationalDataApi = {
    new RelationalDataApi {
      implicit override val system   = sys
      implicit override val executor = ex
      override val escherConfig      = eConfig
    }
  }
}
