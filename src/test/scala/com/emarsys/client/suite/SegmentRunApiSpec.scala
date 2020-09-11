package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, OK}
import akka.http.scaladsl.model._
import com.emarsys.client.RestClientErrors.RestClientException
import com.emarsys.client.suite.SegmentRunApi.{ContactListDetails, SegmentRunResult}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{ExecutionContextExecutor, Future}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class SegmentRunApiSpec extends AsyncWordSpec with Matchers with ScalaFutures {
  implicit val system       = ActorSystem("segment-run-api-test-system")
  implicit val executor     = system.dispatcher

  val escherConfig       = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))
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

    "call api with renew parameter" when {
      "renew param is true" in {
        segmentApiForceRenew(StatusCodes.OK, validStartResponse)
          .start(customerId, segmentId, true)
          .map(_ shouldEqual SegmentRunResult(runId, "waiting", None))
      }
    }

    "no renew parameter in request" when {
      "renew param is false" in {
        segmentApiForceNoRenew(StatusCodes.OK, validStartResponse)
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
    TestSegmentRunApi(
      escherConfig,
      HttpResponse(httpStatus, Nil, HttpEntity(ContentTypes.`application/json`, response))
    )

  def segmentApiForceRenew(httpStatus: StatusCode, response: String) =
    TestSegmentRunApiForceRenew(escherConfig, response)

  def segmentApiForceNoRenew(httpStatus: StatusCode, response: String) =
    TestSegmentRunApiNoRenew(escherConfig, response)
}

object TestSegmentRunApi {
  def apply(eConfig: EscherConfig, response: HttpResponse)(
      implicit
      sys: ActorSystem,
      ex: ExecutionContextExecutor
  ) =
    new SuiteClient with SegmentRunApi {
      implicit override val system       = sys
      implicit override val executor     = ex
      override val escherConfig          = eConfig

      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = Future.successful(response)
    }
}

object TestSegmentRunApiForceRenew {
  def apply(eConfig: EscherConfig, response: String)(
      implicit
      sys: ActorSystem,
      ex: ExecutionContextExecutor
  ) =
    new SuiteClient with SegmentRunApi {
      implicit override val system       = sys
      implicit override val executor     = ex
      override val escherConfig          = eConfig

      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] =
        Future.successful(
          request match {
            case HttpRequest(HttpMethods.POST, uri, _, _, _) if uri.rawQueryString.get == "renew=true" =>
              HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, response))
            case _ =>
              HttpResponse(
                InternalServerError,
                Nil,
                HttpEntity(ContentTypes.`application/json`, "Query must contain renew=true")
              )
          }
        )
    }
}

object TestSegmentRunApiNoRenew {
  def apply(eConfig: EscherConfig, response: String)(
      implicit
      sys: ActorSystem,
      ex: ExecutionContextExecutor
  ) =
    new SuiteClient with SegmentRunApi {
      implicit override val system       = sys
      implicit override val executor     = ex
      override val escherConfig          = eConfig

      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] =
        Future.successful(
          request match {
            case HttpRequest(HttpMethods.POST, uri, _, _, _) if uri.rawQueryString.isEmpty =>
              HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, response))
            case _ =>
              HttpResponse(
                InternalServerError,
                Nil,
                HttpEntity(ContentTypes.`application/json`, "Query must contain renew=true")
              )
          }
        )
    }
}
