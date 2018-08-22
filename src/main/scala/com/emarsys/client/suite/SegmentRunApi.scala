package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.emarsys.escher.akka.http.config.EscherConfig
import com.emarsys.formats.SuiteSdkFormats._

import scala.concurrent.{ExecutionContextExecutor, Future}

trait SegmentRunApi extends SuiteClient {

  import SegmentRunApi._

  def start(customerId: Int, segmentId: Int): Future[SegmentRunResult] = {
    val path    = s"filter/$segmentId/runs"
    val request = RequestBuilding.Post(Uri(baseUrl(customerId) + path))

    run[SegmentRunResultRaw](request).map(_.data).map(toInternalFormat)
  }

  def poll(customerId: Int, segmentId: Int, runId: String): Future[SegmentRunResult] = {
    val path    = s"filter/$segmentId/runs/$runId"
    val request = RequestBuilding.Get(Uri(baseUrl(customerId) + path))

    run[SegmentRunResultRaw](request).map(_.data).map(toInternalFormat)
  }

  private def toInternalFormat(segmentRunResult: SegmentRunResultRaw): SegmentRunResult =
    SegmentRunResult(
      segmentRunResult.run_id,
      segmentRunResult.status,
      segmentRunResult.result.map(toInternalFormat)
    )

  private def toInternalFormat(contactListDetails: ContactListDetailsRaw): ContactListDetails =
    ContactListDetailsRaw.unapply(contactListDetails).map(ContactListDetails.tupled).get
}

object SegmentRunApi {

  final case class SegmentRunResultRaw(run_id: String, status: String, result: Option[ContactListDetailsRaw])
  final case class ContactListDetailsRaw(contact_list_id: Int, user_count: Int, opt_in_count: Int, duration: Int)

  final case class SegmentRunResult(runId: String, status: String, result: Option[ContactListDetails])
  final case class ContactListDetails(contactListId: Int, userCount: Int, optInCount: Int, duration: Int)

  def apply(eConfig: EscherConfig)(implicit
                                   sys: ActorSystem,
                                   mat: Materializer,
                                   ex: ExecutionContextExecutor): SegmentRunApi = {

    new SuiteClient with SegmentRunApi {
      override implicit val system       = sys
      override implicit val materializer = mat
      override implicit val executor     = ex
      override val escherConfig          = eConfig
    }
  }
}