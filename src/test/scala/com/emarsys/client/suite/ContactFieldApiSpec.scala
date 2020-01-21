package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, Materializer}
import com.emarsys.client.RestClientErrors.RestClientException
import com.emarsys.client.suite.ContactFieldApi.{CreateFieldRequest, CreateFieldResponse, FieldItem}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncWordSpec, Matchers}

import scala.concurrent.{ExecutionContextExecutor, Future}

class ContactFieldApiSpec extends AsyncWordSpec with Matchers with ScalaFutures {
  implicit val system       = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()
  implicit val executor     = system.dispatcher

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

  object TestContactFieldApi {
    def apply(
        eConfig: EscherConfig,
        response: HttpResponse
    )(implicit sys: ActorSystem, mat: Materializer, ex: ExecutionContextExecutor) =
      new SuiteClient with ContactFieldApi {
        implicit override val system       = sys
        implicit override val materializer = mat
        implicit override val executor     = ex
        override val escherConfig          = eConfig

        override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = Future.successful(response)
      }
  }

  val customerId        = 123
  val invalidResponse   = "invalid"
  val emptyDataResponse = """{"replyCode":1,"replyText":"Unauthorized","data":""}"""
  val validResponse     = """{
                        |  "replyCode": 0,
                        |  "replyText": "OK",
                        |  "data": [
                        |    {
                        |      "id": 0,
                        |      "name": "Interests",
                        |      "application_type": "interests",
                        |      "string_id": "interests"
                        |    },
                        |    {
                        |      "id": 9,
                        |      "name": null,
                        |      "application_type": "single choice",
                        |      "string_id": ""
                        |    },
                        |    {
                        |      "id": 11,
                        |      "name": "Predict something",
                        |      "application_type": "simple",
                        |      "string_id": ""
                        |    },
                        |    {
                        |      "id": 13,
                        |      "name": "another predict",
                        |      "application_type": "multi choice",
                        |      "string_id": ""
                        |    }
                        |  ]
                        |}""".stripMargin

  "ContactField Api" when {
    "contact fields list called" should {
      "return existing fields in case of successful response" in {
        contactField(OK, validResponse).list(customerId) map { response =>
          response.data shouldEqual List(
            FieldItem(0, Some("Interests"), "interests", "interests"),
            FieldItem(9, None, "single choice", ""),
            FieldItem(11, Some("Predict something"), "simple", ""),
            FieldItem(13, Some("another predict"), "multi choice", "")
          )
        }
      }

      "return translated existing fields for in case of successful response" in {
        contactField(OK, validResponse).list(customerId, "en") map { response =>
          response.data shouldEqual List(
            FieldItem(0, Some("Interests"), "interests", "interests"),
            FieldItem(9, None, "single choice", ""),
            FieldItem(11, Some("Predict something"), "simple", ""),
            FieldItem(13, Some("another predict"), "multi choice", "")
          )
        }
      }

      "return predict fields for customer" in {
        contactField(OK, validResponse).listPredictFields(customerId) map { response =>
          response.data shouldEqual List(
            FieldItem(11, Some("Predict something"), "simple", ""),
            FieldItem(13, Some("another predict"), "multi choice", "")
          )
        }
      }

      "return translated predict fields for customer" in {
        contactField(OK, validResponse).listPredictFields(customerId, "en") map { response =>
          response.data shouldEqual List(
            FieldItem(11, Some("Predict something"), "simple", ""),
            FieldItem(13, Some("another predict"), "multi choice", "")
          )
        }
      }

      "return empty list for data in case of empty data response" in {
        contactField(OK, emptyDataResponse).list(customerId, "en") map { response =>
          response.data shouldEqual List()
        }
      }

      "return empty list for data in case of empty data response and unsuccessful http status" in {
        recoverToSucceededIf[RestClientException] {
          contactField(Unauthorized, emptyDataResponse).list(customerId, "en")
        }
      }

      "return failure in case of failed response" in {
        recoverToSucceededIf[RestClientException] {
          contactField(Unauthorized, invalidResponse).list(customerId)
        }
      }
    }
  }

  "create custom field called" when {
    "valid response" should {
      "field id returned" in {
        val validResult = """{
                                |  "replyCode": 0,
                                |  "replyText": "OK",
                                |  "data":
                                |  {
                                |    "id": 111112222
                                |  }
                                |}""".stripMargin
        contactField(OK, validResult).createField(123, CreateFieldRequest("field1", "app", None)) map { response =>
          response shouldEqual CreateFieldResponse(111112222)
        }
      }
    }

    "invalid response" should {
      "exception raised" in {
        recoverToSucceededIf[Exception] {
          contactField(OK, invalidResponse).createField(123, CreateFieldRequest("field1", "app", None))
        }
      }
    }
  }

  private def contactField(httpStatus: StatusCode, requestEntity: String) = {
    TestContactFieldApi(
      escherConfig,
      HttpResponse(httpStatus, Nil, HttpEntity(ContentTypes.`application/json`, requestEntity))
    )
  }
}
