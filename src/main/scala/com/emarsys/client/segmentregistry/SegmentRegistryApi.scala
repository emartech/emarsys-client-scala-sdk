package com.emarsys.client.segmentregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import com.emarsys.client.Config.emsApi.segmentRegistry
import com.emarsys.client.{EscherRestClient, RestClient}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.emarsys.formats.SegmentRegistryFormats._
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContextExecutor, Future}

trait SegmentRegistryApi extends EscherRestClient {

  import SegmentRegistryApi._

  val serviceName: String = segmentRegistry.serviceName

  val baseUrl = s"${segmentRegistry.protocol}://${segmentRegistry.host}:${segmentRegistry.port}"

  val retryConfig = defaultRetryConfig.copy(maxRetries = 0)

  def create(customerId: Int, segmentData: SegmentCreatePayload): Future[SegmentRegistryRecord] = {
    runSigned[SegmentRegistryRecord](
      RequestBuilding.Post(Uri(baseUrl + path(customerId)), segmentData),
      serviceName,
      Nil,
      retryConfig
    )
  }

  def update(customerId: Int, segmentData: SegmentData): Future[SegmentRegistryRecord] = {
    runSigned[SegmentRegistryRecord](
      RequestBuilding.Put(Uri(baseUrl + path(customerId)), segmentData),
      serviceName,
      Nil,
      retryConfig
    )
  }

  def updateByRegistryId(customerId: Int, segmentData: SegmentData): Future[SegmentRegistryRecord] = {
    runSigned[SegmentRegistryRecord](
      RequestBuilding.Put(Uri(baseUrl + path(customerId) + "/" + segmentData.id), segmentData),
      serviceName,
      Nil,
      retryConfig
    )
  }

  def delete(customerId: Int, segmentId: Int): Future[Unit] = {
    runSigned[String](
      RequestBuilding.Delete(Uri(baseUrl + path(customerId) + "/" + segmentId)),
      serviceName,
      Nil,
      retryConfig
    ).map(_ => ())
  }

  private def path(customerId: Int) = {
    s"/customers/$customerId/segments"
  }
}

object SegmentRegistryApi {

  case class SegmentData(
      id: Int,
      name: String,
      segmentType: String,
      criteriaTypes: Option[Seq[String]] = None,
      baseContactListId: Option[Int] = None,
      predefined: Option[Boolean] = None,
      predefinedSegmentId: Option[String] = None
  )
  case class SegmentCreatePayload(
      id: Option[Int],
      name: String,
      segmentType: String,
      criteriaTypes: Option[Seq[String]] = None,
      baseContactListId: Option[Int] = None,
      predefined: Option[Boolean] = None,
      predefinedSegmentId: Option[String] = None
  )

  case class SegmentRegistryRecord(
      id: Int,
      originalId: Int,
      customerId: Int,
      segmentType: String,
      name: String,
      created: DateTime,
      updated: DateTime,
      criteriaTypes: Seq[String],
      baseContactListId: Int,
      predefined: Boolean
  )

  def apply(eConfig: EscherConfig)(
      implicit
      sys: ActorSystem,
      mat: Materializer,
      ex: ExecutionContextExecutor
  ): SegmentRegistryApi =
    new RestClient with SegmentRegistryApi {
      implicit override val system: ActorSystem                = sys
      implicit override val materializer: Materializer         = mat
      implicit override val executor: ExecutionContextExecutor = ex
      override val escherConfig: EscherConfig                  = eConfig
    }
}
