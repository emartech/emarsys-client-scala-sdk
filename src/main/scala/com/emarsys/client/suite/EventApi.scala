package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.emarsys.escher.akka.http.config.EscherConfig
import spray.json.JsValue

import scala.concurrent.{ExecutionContextExecutor, Future}

trait EventApi extends SuiteClient {

  import fommil.sjs.FamilyFormats._
  import EventApi._

  def trigger(customerId: Int, eventId: String, entity: ExternalEventTrigger): Future[Unit] = {
    val path = s"event/$eventId/trigger"
    val request = RequestBuilding.Post(Uri(baseUrl(customerId) + path), entity.toJsonWithPureSpray)

    run[Map[String, String]](request).map(_ => ())
  }

  def triggerBatch(customerId: Int, eventId: String, entity: ExternalEventTriggerBatch): Future[Unit] = {
    val path = s"event/$eventId/trigger"
    val request = RequestBuilding.Post(Uri(baseUrl(customerId) + path), entity.toJsonWithPureSpray)

    run[Map[String, String]](request).map(_ => ())
  }
}

object EventApi {

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
      implicit val formatForContact = jsonFormat(ExternalEventTriggerContact,"external_id", "data")
      implicit val format = jsonFormat(ExternalEventTriggerBatch, "key_id", "contacts")
      this.toJson
    }
  }

  case class ExternalEventTriggerContact(externalId: String, data: Option[JsValue])

  def apply(eConfig: EscherConfig)(
    implicit
    sys: ActorSystem,
    mat: Materializer,
    ex: ExecutionContextExecutor): EventApi = {

    new EventApi {
      override implicit val system       = sys
      override implicit val materializer = mat
      override implicit val executor     = ex
      override val escherConfig          = eConfig
    }
  }
}
