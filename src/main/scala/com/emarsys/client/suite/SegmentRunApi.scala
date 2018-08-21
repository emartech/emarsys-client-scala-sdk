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

    run[SegmentRunResult](request).map(_.data)
  }

  def poll(customerId: Int, segmentId: Int, runId: String): Future[SegmentRunResult] = {
    val path    = s"filter/$segmentId/runs/$runId"
    val request = RequestBuilding.Get(Uri(baseUrl(customerId) + path))

    run[SegmentRunResult](request).map(_.data)
  }
}

object SegmentRunApi {

  final case class SegmentRunResult(run_id: String, status: String, count: Option[Int])

  def apply(eConfig: EscherConfig)(
    implicit
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


