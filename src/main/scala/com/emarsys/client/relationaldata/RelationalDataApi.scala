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

import scala.concurrent.ExecutionContextExecutor


trait RelationalDataApi extends RestClient {


  val serviceName = relationalData.serviceName
  val baseUrl = s"${relationalData.protocol}://${relationalData.host}:${relationalData.port}"

  lazy val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Http().outgoingConnectionHttps(relationalData.host)

  def insertIgnore(customerId: Int, tableName: String, payload: Seq[Map[String, String]]) = {
    val path: String = s"/customers/$customerId/tables/$tableName/records"

    val request = RequestBuilding.Post(Uri(baseUrl + path), payload)

    runRaw[String](request)
  }


}

object RelationalDataApi {

  def apply(eConfig: EscherConfig)(
    implicit
    sys: ActorSystem,
    mat: Materializer,
    ex: ExecutionContextExecutor): RelationalDataApi = {

    new RelationalDataApi {
      override implicit val system       = sys
      override implicit val materializer = mat
      override implicit val executor     = ex
      override val escherConfig          = eConfig
    }
  }

}
