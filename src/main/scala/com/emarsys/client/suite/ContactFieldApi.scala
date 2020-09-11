package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import com.emarsys.formats.SuiteSdkFormats._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.emarsys.client.suite.SuiteClient.SuiteRawResponse
import com.emarsys.escher.akka.http.config.EscherConfig

import scala.concurrent.{ExecutionContextExecutor, Future}

trait ContactFieldApi extends SuiteClient {
  import ContactFieldApi._
  val retryConfig = defaultRetryConfig.copy(maxRetries = 0)

  def list(customerId: Int): Future[ListResponse] = {
    val path = "field"
    callList(customerId, path)
  }

  def list(customerId: Int, languageCode: String): Future[ListResponse] = {
    val path = s"field/translate/$languageCode"
    callList(customerId, path)
  }

  def listPredictFields(customerId: Int): Future[ListResponse] = {
    list(customerId) map predictFilter
  }

  def listPredictFields(customerId: Int, languageCode: String): Future[ListResponse] = {
    list(customerId, languageCode) map predictFilter
  }

  private def callList(customerId: Int, path: String) = {
    val request = RequestBuilding.Get(Uri(baseUrl(customerId) + path))

    runSuiteRequest[ListRawResponseData](request, retryConfig) map listResponseTransformer
  }

  private def predictFilter(fields: ListResponse) =
    fields.copy(data = fields.data.filter(_.name.getOrElse("").toLowerCase contains "predict"))

  val listResponseTransformer: (SuiteRawResponse[ListRawResponseData]) => ListResponse = r =>
    r.data match {
      case Right(d) => ListResponse(d)
      case Left(_)  => ListResponse(Nil)
    }

  def createField(customerId: Int, payload: CreateFieldRequest): Future[CreateFieldResponse] = {
    val request = RequestBuilding.Post(Uri(baseUrl(customerId) + "field"), payload)
    runSuiteRequest[CreateFieldResponse](request, retryConfig) map (_.data)
  }
}

object ContactFieldApi {
  final case class FieldItem(id: Int, name: Option[String], application_type: String, string_id: String)
  final case class ListResponse(data: List[FieldItem])
  type ListRawResponseData = Either[String, List[FieldItem]]

  final case class CreateFieldRequest(name: String, application_type: String, string_id: Option[String])
  final case class CreateFieldResponse(id: Int)

  def apply(eConfig: EscherConfig)(implicit sys: ActorSystem, ex: ExecutionContextExecutor) =
    new SuiteClient with ContactFieldApi {
      implicit override val system       = sys
      implicit override val executor     = ex
      override val escherConfig          = eConfig
    }
}
