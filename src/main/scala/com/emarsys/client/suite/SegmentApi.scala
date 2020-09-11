package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.emarsys.client.suite.SuiteClient.SuiteRawResponse
import com.emarsys.formats.SuiteSdkFormats._
import com.emarsys.escher.akka.http.config.EscherConfig

import scala.concurrent.{ExecutionContextExecutor, Future}

trait SegmentApi extends SuiteClient {
  import SegmentApi._
  val retryConfig = defaultRetryConfig.copy(maxRetries = 0)

  def create(customerId: Int, payload: CreateRequest): Future[CreateResponse] = {
    val path    = "filter"
    val request = RequestBuilding.Put(Uri(baseUrl(customerId) + path), payload)

    runSuiteRequest[CreateRawResponseData](request, retryConfig) map createTransformer
  }
}

object SegmentApi {
  final case class CreateRequest(
      name: String,
      contactCriteria: Option[ContactCriteria],
      behaviorCriteria: Option[BehaviorCriteria],
      description: String,
      baseContactListId: Option[Int]
  )

  sealed trait ContactCriteria
  final case class ContactCriteriaBranch(`type`: String, children: List[ContactCriteria]) extends ContactCriteria
  final case class ContactCriteriaLeaf(`type`: String, field: Either[Int, String], operator: String, value: String)
      extends ContactCriteria

  sealed trait BehaviorCriteria
  final case class BehaviorCriteriaBranch(`type`: String, children: List[BehaviorCriteria]) extends BehaviorCriteria
  final case class BehaviorCriteriaLeaf(
      `type`: String,
      criteria: Option[String] = None,
      time_restriction: Option[String] = None,
      from_day: Option[String] = None,
      to_day: Option[String] = None,
      from_date: Option[String] = None,
      to_date: Option[String] = None,
      campaign_filter: Option[String] = None,
      campaign_ids: Option[List[Int]] = None,
      category_ids: Option[List[Int]] = None,
      platform_types: Option[List[String]] = None,
      mobile_platforms: Option[List[String]] = None
  ) extends BehaviorCriteria

  final case class CreateRawResponseData(id: String)
  final case class CreateResponse(id: Int)

  val createTransformer: SuiteRawResponse[CreateRawResponseData] => CreateResponse =
    r => CreateResponse(r.data.id.toInt)

  def apply(eConfig: EscherConfig)(
      implicit
      sys: ActorSystem,
      ex: ExecutionContextExecutor
  ): SegmentApi = {
    new SuiteClient with SegmentApi {
      implicit override val system       = sys
      implicit override val executor     = ex
      override val escherConfig          = eConfig
    }
  }
}
