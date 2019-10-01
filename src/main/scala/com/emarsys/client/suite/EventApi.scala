package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.emarsys.client.suite.SuiteClient.SuiteRawResponse
import com.emarsys.escher.akka.http.config.EscherConfig
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

trait EventApi extends SuiteClient {

  import EventApi._

  val retryConfig = defaultRetryConfig.copy(maxRetries = 0)

  implicit val formatForMap     = jsonFormat3(SuiteRawResponse[Map[String, String]])
  implicit val formatForJsValue = jsonFormat3(SuiteRawResponse[JsValue])

  def trigger(customerId: Int, eventId: String, entity: ExternalEventTrigger): Future[Unit] = {
    val path    = s"event/$eventId/trigger"
    val request = RequestBuilding.Post(Uri(baseUrl(customerId) + path), entity.toJsonWithPureSpray)

    runSuiteRequest[Map[String, String]](request, retryConfig).map(_ => ())
  }

  def triggerBatch(customerId: Int, eventId: String, entity: ExternalEventTriggerBatch): Future[List[TriggerError]] = {
    val path    = s"event/$eventId/trigger"
    val request = RequestBuilding.Post(Uri(baseUrl(customerId) + path), entity.toJsonWithPureSpray)

    runSuiteRequest[JsValue](request, retryConfig).map(_.data match {
      case data: JsObject => parseBatchTriggerResponseData(data)
      case _              => List.empty[TriggerError]
    })
  }

  private def parseBatchTriggerResponseData(data: JsObject) = {
    implicit val emptyBatchTriggerResponseDataFormat = jsonFormat1(BatchTriggerResponseDataEmptyErrors)
    implicit val batchTriggerResponseDataFormat      = jsonFormat1(BatchTriggerResponseData)
    Try(data.toJson.convertTo[BatchTriggerResponseDataEmptyErrors]).toOption.fold(
      data.toJson.convertTo[BatchTriggerResponseData].errors.fold(List.empty[TriggerError])(convertToTriggerErrors)
    )(_ => List.empty[TriggerError])
  }

  private def convertToTriggerErrors(errorData: Map[String, Map[String, String]]) = {
    errorData
      .map {
        case (externalId, errors) =>
          errors.headOption.map {
            case (errorCode, errorMessage) => TriggerError(externalId, errorCode, errorMessage)
          }
      }
      .toList
      .flatten
  }

}

object EventApi {

  case class BatchTriggerResponseDataEmptyErrors(errors: List[String])
  case class BatchTriggerResponseData(errors: Option[Map[String, Map[String, String]]])
  implicit val batchTriggerResponseDataFormat = jsonFormat1(BatchTriggerResponseData)
  case class TriggerError(externalId: String, errorCode: String, errorMessage: String)

  case class ExternalEventTrigger(keyId: String, externalId: String, data: Option[JsValue]) {
    private[EventApi] def toJsonWithPureSpray: JsValue = {
      import spray.json._
      import spray.json.DefaultJsonProtocol._
      implicit val format = jsonFormat(ExternalEventTrigger, "key_id", "external_id", "data")
      this.toJson
    }
  }

  case class ExternalEventTriggerBatch(keyId: String, contacts: List[ExternalEventTriggerContact]) {
    private[EventApi] def toJsonWithPureSpray: JsValue = {
      import spray.json._
      import spray.json.DefaultJsonProtocol._
      implicit val formatForContact = jsonFormat(ExternalEventTriggerContact, "external_id", "data")
      implicit val format           = jsonFormat(ExternalEventTriggerBatch, "key_id", "contacts")
      this.toJson
    }
  }

  case class ExternalEventTriggerContact(externalId: String, data: Option[JsValue])

  def apply(eConfig: EscherConfig)(
      implicit
      sys: ActorSystem,
      mat: Materializer,
      ex: ExecutionContextExecutor
  ): EventApi = {

    new EventApi {
      implicit override val system       = sys
      implicit override val materializer = mat
      implicit override val executor     = ex
      override val escherConfig          = eConfig
    }
  }
}
