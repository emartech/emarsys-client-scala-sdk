package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import com.emarsys.client.RestClientException
import com.emarsys.client.suite.EventApi.{ExternalEventTrigger, ExternalEventTriggerBatch, ExternalEventTriggerContact, TriggerError}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncWordSpec, Matchers}
import spray.json.{JsBoolean, JsObject, JsString}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.Try

class EventApiSpec extends AsyncWordSpec with Matchers with ScalaFutures {

  implicit val system = ActorSystem("event-api-test-system")
  implicit val materializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

  val customerId = 123

  object TestEventApi {

    def apply(eConfig: EscherConfig,
              path: String,
              data: String,
              response: HttpResponse
             )(implicit sys: ActorSystem, mat: Materializer, ex: ExecutionContextExecutor) =
      new SuiteClient with EventApi {
        override implicit val system = sys
        override implicit val materializer = mat
        override implicit val executor = ex
        override val escherConfig = eConfig

        override lazy val connectionFlow = Flow[HttpRequest].map {
          case HttpRequest(HttpMethods.POST, uri, _, entity, _) if uri.path.toString().endsWith(path) && plainTextParse(entity) == data => response
          case HttpRequest(HttpMethods.POST, _, _, entity, _) => HttpResponse(BadRequest, entity = plainTextParse(entity))
        }
      }

    private def plainTextParse(entity: RequestEntity): String = {
      val postDataF = entity.dataBytes.map(_.utf8String).reduce((i, z) => i.concat(z)).runWith(Sink.head)
      Await.result(postDataF, 50.millis)
    }
  }

  val validResponse =
    """{
      |  "data": {},
      |  "replyCode": 0,
      |  "replyText": "OK"
      |}""".stripMargin

  val errorResponse =
    """{
      |  "replyCode":5001,
      |  "replyText":"Invalid event ID for customer."
      |}""".stripMargin

  val eventId = "EVENT_ID"
  val keyId = "KEY_ID"
  val externalId = "EXTERNAL_ID"
  val path = s"event/$eventId/trigger"

  "Event Api" when {

    "trigger called" should {

      "without data return successful" in {
        val requestData = s"""{"key_id":"$keyId","external_id":"$externalId"}"""

        val result = Try(Await.result(eventApi(path, requestData, OK, validResponse).trigger(customerId, eventId, ExternalEventTrigger(keyId, externalId, None)), 1.second))

        result.isSuccess shouldEqual true
      }


      "with data return successful" in {
        val requestData = s"""{"key_id":"$keyId","external_id":"$externalId","data":{"hello":true}}"""
        val data = JsObject("hello" -> JsBoolean(true))

        val result = Try(Await.result(eventApi(path, requestData, OK, validResponse).trigger(customerId, eventId, ExternalEventTrigger(keyId, externalId, Some(data))), 1.second))

        result.isSuccess shouldEqual true
      }

      "return error" in {
        val requestData = s"""{"key_id":"$keyId","external_id":"$externalId","data":{"hello":true}}"""
        val data = JsObject("hello" -> JsBoolean(true))

        recoverToSucceededIf[RestClientException] {
          eventApi(path, requestData, BadRequest, errorResponse).trigger(customerId, eventId, ExternalEventTrigger(keyId, externalId, Some(data)))
        }
      }
    }


    "batch trigger called" should {

      val data = JsObject("hello" -> JsBoolean(true))

      "return successful response" in {
        val otherData = JsObject("data" -> JsString("asd"))
        val otherExternalId = "OTHER_EXTERNAL_ID"
        val requestData = s"""{"key_id":"$keyId","contacts":[{"external_id":"$externalId","data":{"hello":true}},{"external_id":"$otherExternalId","data":{"data":"asd"}}]}"""

        val eventClient = eventApi(path, requestData, OK, validResponse)
        val triggerData = ExternalEventTriggerBatch(keyId, List(
          ExternalEventTriggerContact(externalId, Some(data)),
          ExternalEventTriggerContact(otherExternalId, Some(otherData))
        ))
        val result = Try(Await.result(eventClient.triggerBatch(customerId, eventId, triggerData), 1.second))

        result.isSuccess shouldEqual true
      }

      "return errors of successful response if one of the items fails" in {
        val requestData = s"""{"key_id":"$keyId","contacts":[{"external_id":"$externalId","data":{"hello":true}}]}"""
        val triggerData = ExternalEventTriggerBatch(keyId, List(
          ExternalEventTriggerContact(externalId, Some(data))
        ))

        val validResponseContainingOneError =
          s"""{
            |  "replyCode": 0,
            |  "replyText": "OK",
            |  "data": {
            |    "errors": {
            |      "$externalId": {
            |        "2008": "No contact found with the external id: $keyId - $externalId"
            |      }
            |    }
            |  }
            |}""".stripMargin

        val eventClient = eventApi(path, requestData, OK, validResponseContainingOneError)
        val result = Await.result(eventClient.triggerBatch(customerId, eventId, triggerData), 1.second)

        result shouldEqual List(
          TriggerError(externalId, "2008", s"No contact found with the external id: $keyId - $externalId")
        )
      }

      "return error if the request fails" in {

        val requestData = s"""{"key_id":"$keyId","contacts":[{"external_id":"$externalId","data":{"hello":true}}]}"""
        val triggerData = ExternalEventTriggerBatch(keyId, List(
          ExternalEventTriggerContact(externalId, Some(data))
        ))

        recoverToSucceededIf[RestClientException] {
          eventApi(path, requestData, BadRequest, errorResponse).triggerBatch(customerId, eventId, triggerData)
        }
      }

    }

  }

  def eventApi(requestPath: String, requestData: String, httpStatus: StatusCode, response: String) = {
    TestEventApi(escherConfig, requestPath, requestData, HttpResponse(httpStatus, Nil, HttpEntity(ContentTypes.`application/json`, response)))
  }
}
