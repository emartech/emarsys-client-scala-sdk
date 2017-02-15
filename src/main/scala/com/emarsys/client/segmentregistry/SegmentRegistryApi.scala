package com.emarsys.client.segmentregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.stream.Materializer
import com.emarsys.client.RestClient
import com.emarsys.escher.akka.http.config.EscherConfig
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.emarsys.formats.JodaDateTimeFormat._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.Flow
import com.emarsys.client.Config.segmentRegistryConfig
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContextExecutor, Future}

trait SegmentRegistryApi extends RestClient {

  import SegmentRegistryApi._

  val serviceName = segmentRegistryConfig.serviceName

  val baseUrl = s"https://${segmentRegistryConfig.host}:${segmentRegistryConfig.port}"

  lazy val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Http().outgoingConnectionHttps(segmentRegistryConfig.host)

  def update(customerId: Int, segmentData: SegmentData): Future[SegmentRegistryRecord] = {
    val path = s"/customers/$customerId/segments"
    runRaw[SegmentRegistryRecord](RequestBuilding.Put(Uri(baseUrl + path), segmentData))
  }

}

object SegmentRegistryApi {

  case class SegmentData(id: Int, name: String, segmentType: String, criteriaTypes: Option[Seq[String]] = None, baseContactListId: Option[Int] = None, predefined: Option[Boolean] = None)

  case class SegmentRegistryRecord(id: Int, originalId: Int, customerId: Int, segmentType: String, name: String, created: DateTime, updated: DateTime, criteriaTypes: Seq[String], baseContactListId: Int, predefined: Boolean)

  def apply(eConfig: EscherConfig)(
    implicit
    sys: ActorSystem,
    mat: Materializer,
    ex: ExecutionContextExecutor): SegmentRegistryApi =
    new RestClient with SegmentRegistryApi {
      override implicit val system: ActorSystem                = sys
      override implicit val materializer: Materializer         = mat
      override implicit val executor: ExecutionContextExecutor = ex
      override val escherConfig: EscherConfig                  = eConfig
    }

}