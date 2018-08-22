package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Flow
import akka.stream.{ActorMaterializer, Materializer}
import com.emarsys.client.RestClientException
import com.emarsys.client.suite.SegmentRunApi.{ContactListDetails, SegmentRunResult}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncWordSpec, Matchers}

import scala.concurrent.ExecutionContextExecutor

class SegmentRunApiSpec extends AsyncWordSpec with Matchers with ScalaFutures {

  implicit val system       = ActorSystem("segment-run-api-test-system")
  implicit val materializer = ActorMaterializer()
  implicit val executor     = system.dispatcher

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

  object TestSegmentRunApi {

    def apply(eConfig: EscherConfig, response: HttpResponse)(implicit
                                                             sys: ActorSystem,
                                                             mat: Materializer,
                                                             ex: ExecutionContextExecutor) =
      new SuiteClient with SegmentRunApi {
        override implicit val system       = sys
        override implicit val materializer = mat
        override implicit val executor     = ex
        override val escherConfig          = eConfig

        override lazy val connectionFlow = Flow[HttpRequest].map(_ => response)
      }
  }

  val customerId         = 215526938
  val segmentId          = 1000500238
  val runId              = "100024015"
  val contactListId      = 113214
  val userCount          = 448
  val optInCount         = 2
  val duration           = 8
  val validStartResponse = s"""{"replyCode":0,"replyText":"OK","data":{"run_id":"$runId","status":"waiting"}}"""
  val finishedResponse =
    s"""{"replyCode":0,"replyText":"OK","data":{
       |"run_id":"$runId",
       |"status":"done",
       |"result":{
       |"contact_list_id": $contactListId,
       |"user_count": $userCount,
       |"opt_in_count": $optInCount,
       |"duration": $duration
       |}
       |}}""".stripMargin
  val failedResponse     = s"""{"replyCode":0,"replyText":"OK","data":{"run_id":"$runId","status":"error"}}"""
  val errorStartResponse = """{"replyCode":1008,"replyText":"error","data":""}"""

  "start" should {

    "return with run id and waiting status" when {
      "valid payload provided" in {
        segmentApi(StatusCodes.OK, validStartResponse)
          .start(customerId, segmentId)
          .map(_ shouldEqual SegmentRunResult(runId, "waiting", None))
      }
    }

    "return with failed future" when {
      "request failed" in {
        recoverToSucceededIf[RestClientException] {
          segmentApi(StatusCodes.BadRequest, errorStartResponse).start(customerId, segmentId)
        }
      }
    }
  }

  "poll" should {

    "return with run id and waiting status" when {
      "segment is running" in {
        segmentApi(StatusCodes.OK, validStartResponse)
          .poll(customerId, segmentId, runId)
          .map(_ shouldEqual SegmentRunResult(runId, "waiting", None))
      }
    }

    "return with count and status done" when {
      "segment run finished" in {
        val expectedDetails = ContactListDetails(contactListId, userCount, optInCount, duration)
        segmentApi(StatusCodes.OK, finishedResponse)
          .poll(customerId, segmentId, runId)
          .map(_ shouldEqual SegmentRunResult(runId, "done", Some(expectedDetails)))
      }
    }

    "return with status error" when {
      "segment run failed" in {
        segmentApi(StatusCodes.OK, failedResponse)
          .poll(customerId, segmentId, runId)
          .map(_ shouldEqual SegmentRunResult(runId, "error", None))
      }
    }
  }

  def segmentApi(httpStatus: StatusCode, response: String) =
    TestSegmentRunApi(escherConfig,
                      HttpResponse(httpStatus, Nil, HttpEntity(ContentTypes.`application/json`, response)))
}
