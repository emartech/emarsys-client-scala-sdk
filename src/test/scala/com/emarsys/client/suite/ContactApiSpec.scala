package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, Materializer}
import com.emarsys.client.RestClientErrors.{InvalidResponseFormatException, RestClientException}
import com.emarsys.client.suite.DataTransformers._
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{ExecutionContextExecutor, Future}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ContactApiSpec extends AsyncWordSpec with Matchers with ScalaFutures {
  implicit val system       = ActorSystem("contact-api-test-system")
  implicit val materializer = ActorMaterializer()
  implicit val executor     = system.dispatcher

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

  object TestContactApi {
    def apply(
        eConfig: EscherConfig,
        response: HttpResponse
    )(implicit sys: ActorSystem, mat: Materializer, ex: ExecutionContextExecutor) =
      new SuiteClient with ContactApi {
        implicit override val system       = sys
        implicit override val materializer = mat
        implicit override val executor     = ex
        override val escherConfig          = eConfig

        override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = Future.successful(response)
      }
  }

  val customerId          = 123
  val invalidResponse     = "invalid"
  val emptyDataResponse   = """{"replyCode":1,"replyText":"Unauthorized","data":""}"""
  val validResponse       = """{
                            |  "replyCode": 0,
                            |  "replyText": "OK",
                            |  "data": {
                            |    "errors": [],
                            |    "result": [
                            |      {
                            |        "id": "123",
                            |        "uid": "abc",
                            |        "0": null,
                            |        "1": "Peter",
                            |        "100007887": null,
                            |        "multi": [1]
                            |      }
                            |    ]
                            |  }
                            |}""".stripMargin
  val falseResultResponse = """{
                             |   "replyCode":0,
                             |   "replyText":"OK",
                             |   "data":{
                             |      "errors":[
                             |         {
                             |            "key":"ironman@example.com",
                             |            "errorCode":2008,
                             |            "errorMsg":"No contact found with the external id: 3"
                             |         },
                             |         {
                             |            "key":"hulk@example.com",
                             |            "errorCode":2008,
                             |            "errorMsg":"No contact found with the external id: 3"
                             |         }
                             |      ],
                             |    "result": false
                             |   }
                             |}""".stripMargin

  "Contact Api" when {
    "get data called" should {
      "return existing fields in case of successful response" in {
        contactApi(OK, validResponse).getData(customerId, GetDataRequest("id", Nil, None)) map { response =>
          response.data shouldEqual GetDataResult(
            List(
              Map(
                "id"        -> Right(Some("123")),
                "uid"       -> Right(Some("abc")),
                "0"         -> Right(None),
                "1"         -> Right(Some("Peter")),
                "100007887" -> Right(None),
                "multi"     -> Left(List(1))
              )
            ),
            Nil
          )
        }
      }

      "return false response and list of errors if response data contains false result and errors" in {
        contactApi(OK, falseResultResponse).getData(customerId, GetDataRequest("id", Nil, None)) map { response =>
          response.data shouldEqual GetDataResult(
            Nil,
            List(
              GetDataError("ironman@example.com", 2008, "No contact found with the external id: 3"),
              GetDataError("hulk@example.com", 2008, "No contact found with the external id: 3")
            )
          )
        }
      }

      "return false response and empty list of errors if data is empty string - thanks suite API" in {
        contactApi(OK, emptyDataResponse).getData(customerId, GetDataRequest("id", Nil, None)) map { response =>
          response.data shouldEqual GetDataResult(Nil, Nil)
        }
      }

      "return failure in case of non-success status code" in {
        recoverToSucceededIf[RestClientException] {
          contactApi(Unauthorized, emptyDataResponse).getData(customerId, GetDataRequest("id", Nil, None))
        }
      }

      "return failure in case of invalid but successful response" in {
        recoverToSucceededIf[Exception] {
          contactApi(OK, invalidResponse).getData(customerId, GetDataRequest("id", Nil, None))
        }
      }

      "failed unmarshall" in {
        recoverToSucceededIf[InvalidResponseFormatException] {
          contactApi(OK, "[]").getData(customerId, GetDataRequest("id", Nil, None))
        }
      }
    }
  }

  def contactApi(httpStatus: StatusCode, requestEntity: String) = {
    TestContactApi(
      escherConfig,
      HttpResponse(httpStatus, Nil, HttpEntity(ContentTypes.`application/json`, requestEntity))
    )
  }
}
