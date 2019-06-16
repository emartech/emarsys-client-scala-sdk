package com.emarsys.client.relationaldata

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.emarsys.client.RestClient
import com.emarsys.escher.akka.http.config.EscherConfig
import com.emarsys.client.Config.emsApi.relationalData
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.http.scaladsl.model.headers.RawHeader
import spray.json.JsValue

import scala.concurrent.ExecutionContextExecutor

trait RelationalDataApi extends RestClient {

  val serviceName   = relationalData.serviceName
  val baseUrl       = Uri(scheme = s"${relationalData.protocol}", authority = Authority(host = Host(relationalData.host))) + relationalData.basePath
  val maxRetryCount = 0

  final val customerIdHeader = "x-suite-customerid"

  lazy val connectionFlow: Flow[HttpRequest, HttpResponse, _] = {
    if (relationalData.port == 443) {
      Http().outgoingConnectionHttps(relationalData.host)
    } else {
      Http().outgoingConnection(relationalData.host, relationalData.port)
    }
  }

  def insertIgnore(customerId: Int, tableName: String, payload: Seq[Map[String, JsValue]]) = {
    val path: String = s"/tables/$tableName/records"

    val request = RequestBuilding
      .Post(baseUrl + path, payload)
      .addHeader(RawHeader(customerIdHeader, customerId.toString))

    runSigned[String](request, serviceName, List(customerIdHeader), maxRetryCount)
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
