package com.emarsys.client.segmentregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.emarsys.client.RestClientErrors.{InvalidResponseFormatException, RestClientException}
import com.emarsys.client.segmentregistry.SegmentRegistryApi.{SegmentCreatePayload, SegmentData, SegmentRegistryRecord}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.emarsys.formats.SegmentRegistryFormats._
import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, Matchers}
import spray.json._

import scala.concurrent.Future

class SegmentRegistryApiSpec
    extends AsyncWordSpec
    with Matchers
    with ScalaFutures
    with SegmentRegistryApi
    with BeforeAndAfterAll {

  implicit val system          = ActorSystem("segment-registry-api-test-system")
  implicit val materializer    = ActorMaterializer()
  implicit val executor        = system.dispatcher
  implicit val defaultPatience = PatienceConfig(timeout = Span(2, Seconds), interval = Span(100, Millis))

  val escherConfig   = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))
  val segmentCreated = DateTime.now.toDateTime(DateTimeZone.UTC).withMillisOfSecond(0)
  val criteriaTypes  = Seq("contact", "behavior")

  val customerId                    = 101
  val invalidResponseCodeCustomerId = 102
  val invalidDateFormatCustomerId   = 103

  val mandatoryOnlySegmentId = 100
  val createSegmentId        = 200

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

  val segmentCreatePayload = SegmentCreatePayload(
    id = Some(1),
    name = "segment name",
    segmentType = "normal",
    criteriaTypes = Some(criteriaTypes),
    baseContactListId = Some(0),
    Some(true)
  )

  "SegmentRegistryApi" should {

    "update responds with segment record" when {

      "proper segment data is sent" in {
        update(customerId, segmentData).map {
          _ shouldEqual validResponse
        }
      }

      "only mandatory segment data is sent" in {
        update(customerId, SegmentData(mandatoryOnlySegmentId, "segment name", "normal")).map {
          _ shouldEqual validResponseWithMandatoryData
        }
      }
    }

    "update returns failed future" when {

      "response code is invalid" in {
        recoverToSucceededIf[RestClientException] {
          update(invalidResponseCodeCustomerId, segmentData)
        }
      }

      "date format is invalid in response" in {
        recoverToSucceededIf[InvalidResponseFormatException] {
          val a = update(invalidDateFormatCustomerId, segmentData)
          a.failed.foreach(e => e.printStackTrace())
          a
        }
      }
    }

    "update by registry id responds with segment record" when {

      "proper segment data is sent" in {
        updateByRegistryId(customerId, segmentData).map {
          _ shouldEqual validResponse
        }
      }

      "only mandatory segment data is sent" in {
        updateByRegistryId(customerId, SegmentData(mandatoryOnlySegmentId, "segment name", "normal")).map {
          _ shouldEqual validResponseWithMandatoryData
        }
      }
    }

    "update by registry id returns failed future" when {

      "response code is invalid" in {
        recoverToSucceededIf[RestClientException] {
          updateByRegistryId(invalidResponseCodeCustomerId, segmentData)
        }
      }

      "date format is invalid in response" in {
        recoverToSucceededIf[InvalidResponseFormatException] {
          updateByRegistryId(invalidDateFormatCustomerId, segmentData)
        }
      }
    }

    "create responds with segment record" when {

      "proper segment data is sent" in {
        create(customerId, segmentCreatePayload.copy(id = Some(createSegmentId))).map {
          _ shouldEqual validResponse
        }
      }

      "segment id is not provided" in {
        create(customerId, segmentCreatePayload.copy(id = None)).map {
          _ shouldEqual validResponse
        }
      }
    }

    "create returns failed future" when {

      "response code is invalid" in {
        recoverToSucceededIf[RestClientException] {
          create(customerId, segmentCreatePayload)
        }
      }

      "date format is invalid in response" in {
        recoverToSucceededIf[InvalidResponseFormatException] {
          create(invalidDateFormatCustomerId, segmentCreatePayload)
        }
      }
    }

    "return successful future" when {
      "delete responded with no content" in {
        delete(customerId, createSegmentId).map(_ => 1).map(_ shouldBe 1)
      }
    }

    "return failed future" when {
      "deleted responded with failure" in {
        recoverToSucceededIf[RestClientException] {
          delete(customerId, createSegmentId + 1)
        }
      }
    }
  }

  private val validResponseWithMandatoryData = {
    SegmentRegistryRecord(
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

  override protected def afterAll(): Unit = {
    system terminate
  }

  override protected def sendRequest(request: HttpRequest): Future[HttpResponse] =
    Future.successful(
      request match {
        case HttpRequest(HttpMethods.PUT, uri, _, entity, _)
            if validPath(uri)(s"customers/$customerId/segments/${segmentData.id}") =>
          val segment = Unmarshal(entity).to[SegmentData].futureValue
          val response = SegmentRegistryRecord(
            segment.id,
            segment.id,
            customerId,
            segment.segmentType,
            segment.name,
            segmentCreated,
            segmentCreated,
            segment.criteriaTypes.get,
            segment.baseContactListId.get,
            predefined = true
          )
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, response.toJson.compactPrint))

        case HttpRequest(HttpMethods.PUT, uri, _, entity, _)
            if validPath(uri)(s"customers/$customerId/segments/$mandatoryOnlySegmentId") =>
          val segment = Unmarshal(entity).to[SegmentData].futureValue
          val response = SegmentRegistryRecord(
            segment.id,
            segment.id,
            customerId,
            segment.segmentType,
            segment.name,
            segmentCreated,
            segmentCreated,
            List(),
            1,
            predefined = false
          )
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, response.toJson.compactPrint))

        case HttpRequest(HttpMethods.PUT, uri, _, entity, _) if validPath(uri)(s"customers/$customerId/segments") =>
          val segment = Unmarshal(entity).to[SegmentData].futureValue
          val response = if (segment.id == mandatoryOnlySegmentId) {
            SegmentRegistryRecord(
              segment.id,
              segment.id,
              customerId,
              segment.segmentType,
              segment.name,
              segmentCreated,
              segmentCreated,
              List(),
              1,
              predefined = false
            )
          } else {
            SegmentRegistryRecord(
              segment.id,
              segment.id,
              customerId,
              segment.segmentType,
              segment.name,
              segmentCreated,
              segmentCreated,
              segment.criteriaTypes.get,
              segment.baseContactListId.get,
              predefined = true
            )
          }
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, response.toJson.compactPrint))

        case HttpRequest(HttpMethods.PUT, uri, _, _, _)
            if validPath(uri)(s"customers/$invalidDateFormatCustomerId/segments") =>
          respondWithInvalidDate

        case HttpRequest(HttpMethods.PUT, uri, _, _, _)
            if validPath(uri)(s"customers/$invalidResponseCodeCustomerId/segments") =>
          HttpResponse(
            StatusCodes.InternalServerError,
            Nil,
            HttpEntity(ContentTypes.`application/json`, validResponse.toJson.compactPrint)
          )

        case HttpRequest(HttpMethods.PUT, uri, _, _, _)
            if validPath(uri)(s"customers/$invalidDateFormatCustomerId/segments/${segmentData.id}") =>
          respondWithInvalidDate

        case HttpRequest(HttpMethods.PUT, uri, _, _, _)
            if validPath(uri)(s"customers/$invalidResponseCodeCustomerId/segments/${segmentData.id}") =>
          HttpResponse(
            StatusCodes.InternalServerError,
            Nil,
            HttpEntity(ContentTypes.`application/json`, validResponse.toJson.compactPrint)
          )

        case HttpRequest(HttpMethods.DELETE, uri, _, _, _)
            if validPath(uri)(s"customers/$customerId/segments/$createSegmentId") =>
          HttpResponse(StatusCodes.NoContent)

        case HttpRequest(HttpMethods.DELETE, _, _, _, _) =>
          HttpResponse(StatusCodes.InternalServerError)

        case HttpRequest(HttpMethods.POST, uri, _, entity, _) if validPath(uri)(s"customers/$customerId/segments") =>
          val segment = Unmarshal(entity).to[SegmentCreatePayload].futureValue
          if (segment.id.isEmpty || segment.id.contains(createSegmentId)) {
            HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, validResponse.toJson.compactPrint))
          } else {
            HttpResponse(StatusCodes.InternalServerError)
          }

        case HttpRequest(HttpMethods.POST, uri, _, _, _)
            if validPath(uri)(s"customers/$invalidDateFormatCustomerId/segments") =>
          respondWithInvalidDate
      }
    )

  private def respondWithInvalidDate = {
    val responseString = validResponse.toJson.compactPrint.replace(segmentCreated.toString(dateTimePattern), "invalid")
    HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, responseString))
  }

  val validPath = (u: Uri) => u.path.toString endsWith _

}
