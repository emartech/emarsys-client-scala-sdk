package com.emarsys.client.segmentregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.emarsys.client.segmentregistry.SegmentRegistryApi.{SegmentData, SegmentRegistryRecord}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{AsyncWordSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.emarsys.formats.JodaDateTimeFormat._


class SegmentRegistryApiSpec extends AsyncWordSpec with Matchers with ScalaFutures with SegmentRegistryApi {

  implicit val system          = ActorSystem("segment-registry-api-test-system")
  implicit val materializer    = ActorMaterializer()
  implicit val executor        = system.dispatcher
  implicit val defaultPatience = PatienceConfig(timeout = Span(2, Seconds), interval = Span(100, Millis))

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))
  val segmentCreated = DateTime.now.toDateTime(DateTimeZone.UTC)
  val criteriaTypes = Seq("contact", "behavior")

  val customerId = 101
  val invalidResponseCodeCustomerId = 102
  val invalidDateFormatCustomerId = 103

  val mandatoryOnlySegmentId = 100

  val validResponse = SegmentRegistryRecord(
    id = 1,
    originalId = 1,
    customerId = customerId,
    segmentType = "normal",
    name = "segment name",
    created = segmentCreated,
    updated = segmentCreated,
    criteriaTypes = criteriaTypes,
    baseContactListId = 0,
    predefined = true
  )

  val segmentData = SegmentData(
    id = 1,
    name = "segment name",
    segmentType = "normal",
    criteriaTypes = Some(criteriaTypes),
    baseContactListId = Some(0),
    Some(true)
  )

  "SegmentRegistryApi" should {

    "respond with segment record" when {

      "proper segment data is sent" in {
        update(customerId, segmentData).map {
          _ shouldEqual validResponse
        }
      }

      "only mandatory segment data is sent" in {
        update(customerId, SegmentData(mandatoryOnlySegmentId, "segment name", "normal")).map {
          _ shouldEqual SegmentRegistryRecord(
            id = mandatoryOnlySegmentId,
            originalId = mandatoryOnlySegmentId,
            customerId = customerId,
            segmentType = "normal",
            name = "segment name",
            created = segmentCreated,
            updated = segmentCreated,
            criteriaTypes = List(),
            baseContactListId = 1,
            predefined = false
          )
        }
      }

    }

    "returned failed future" when {

      "response code is invalid" in {
        recoverToSucceededIf[Exception] {
          update(invalidResponseCodeCustomerId, segmentData)
        }
      }

      "date format is invalid in response" in {
        recoverToSucceededIf[IllegalArgumentException] {
          update(invalidDateFormatCustomerId, segmentData)
        }
      }

    }

  }

  override lazy val connectionFlow = Flow[HttpRequest].map {

    case HttpRequest(HttpMethods.PUT, uri, _, entity, _) if validPath(uri)(s"customers/$customerId/segments") =>
      val segment = Unmarshal(entity).to[SegmentData].futureValue
      val response = if (segment.id == mandatoryOnlySegmentId) {
        SegmentRegistryRecord(segment.id, segment.id, customerId, segment.segmentType, segment.name,
          segmentCreated, segmentCreated, List(), 1, predefined = false)
      } else {
        SegmentRegistryRecord(segment.id, segment.id, customerId, segment.segmentType, segment.name,
          segmentCreated, segmentCreated, segment.criteriaTypes.get, segment.baseContactListId.get, predefined = true)
      }
      HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, response.toJson.compactPrint))

    case HttpRequest(HttpMethods.PUT, uri, _, _, _) if validPath(uri)(s"customers/$invalidDateFormatCustomerId/segments") =>
      val responseString = validResponse.toJson.compactPrint.replace(segmentCreated.toString, "invalid")
      HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, responseString))

    case HttpRequest(HttpMethods.PUT, uri, _, _, _) if validPath(uri)(s"customers/$invalidResponseCodeCustomerId/segments") =>
      HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, validResponse.toJson.compactPrint))

  }

  val validPath = (u: Uri) => u.path.toString endsWith _

}
