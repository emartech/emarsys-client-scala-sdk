package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import com.emarsys.client.RestClientException
import com.emarsys.client.suite.EventApi.ExternalEventTrigger
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncWordSpec, Matchers}
import spray.json.{JsBoolean, JsObject}

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
        }
      }

    private def plainTextParse(entity: RequestEntity): String = {
      val postDataF = entity.dataBytes.map(_.utf8String).reduce((i, z) => i.concat(z)).runWith(Sink.head)
      Await.result(postDataF, 50.millis)
    }
  }

  val validResponse =
    """{
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
  }

  def eventApi(requestPath: String, requestData: String, httpStatus: StatusCode, response: String) = {
    TestEventApi(escherConfig, requestPath, requestData, HttpResponse(httpStatus, Nil, HttpEntity(ContentTypes.`application/json`, response)))
  }
}
