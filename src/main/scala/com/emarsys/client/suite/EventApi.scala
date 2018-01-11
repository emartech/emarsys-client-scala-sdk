package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.emarsys.client.suite.EventApi.ExternalEventTrigger
import com.emarsys.escher.akka.http.config.EscherConfig
import spray.json.JsValue

import scala.concurrent.{ExecutionContextExecutor, Future}

trait EventApi extends SuiteClient {

  import fommil.sjs.FamilyFormats._

  def trigger(customerId: Int, eventId: String, entity: ExternalEventTrigger): Future[Unit] = {
    val path = s"event/$eventId/trigger"
    val request = RequestBuilding.Post(Uri(baseUrl(customerId) + path), entity.toJsonWithPureSpray)

    run[Option[String]](request).map(_ => ())
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
