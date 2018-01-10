package com.emarsys.client.suite

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import com.emarsys.client.suite.EventApi.ExternalEventTrigger
import spray.json.JsValue

import scala.concurrent.Future

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

}
