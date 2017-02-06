package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Flow
import akka.stream.{ActorMaterializer, Materializer}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncWordSpec, Matchers}
import DataTransformers._

import scala.concurrent.ExecutionContextExecutor

class ContactApiSpec extends AsyncWordSpec with Matchers with ScalaFutures {

  implicit val system       = ActorSystem("contact-api-test-system")
  implicit val materializer = ActorMaterializer()
  implicit val executor     = system.dispatcher

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

  object TestContactApi {

    def apply(eConfig: EscherConfig,
              response: HttpResponse)(implicit sys: ActorSystem, mat: Materializer, ex: ExecutionContextExecutor) =
      new SuiteClient with ContactApi {
        override implicit val system       = sys
        override implicit val materializer = mat
        override implicit val executor     = ex
        override val escherConfig          = eConfig

        override lazy val connectionFlow = Flow[HttpRequest].map(_ => response)
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
          response.data shouldEqual GetDataResult(List(
                                                    Map("id"        -> Left(Some("123")),
                                                        "uid"       -> Left(Some("abc")),
                                                        "0"         -> Left(None),
                                                        "1"         -> Left(Some("Peter")),
                                                        "100007887" -> Left(None),
                                                        "multi"     -> Right(List(1))
                                                    )
                                                  ),
                                                  Nil)
        }
      }

      "return false response and list of errors if response data contains false result and errors" in {
        contactApi(OK, falseResultResponse).getData(customerId, GetDataRequest("id", Nil, None)) map { response =>
          response.data shouldEqual GetDataResult(
            Nil,
            List(GetDataError("ironman@example.com", 2008, "No contact found with the external id: 3"),
                 GetDataError("hulk@example.com", 2008, "No contact found with the external id: 3")))
        }
      }

      "return false response and empty list of errors if data is empty string - thanks suite API" in {
        contactApi(OK, emptyDataResponse).getData(customerId, GetDataRequest("id", Nil, None)) map { response =>
          response.data shouldEqual GetDataResult(Nil, Nil)
        }
      }

      "return failure in case of non-success status code" in {
        recoverToSucceededIf[Exception] {
          contactApi(Unauthorized, emptyDataResponse).getData(customerId, GetDataRequest("id", Nil, None))
        }
      }

      "return failure in case of invalid but successful response" in {
        recoverToSucceededIf[Exception] {
          contactApi(OK, invalidResponse).getData(customerId, GetDataRequest("id", Nil, None))
        }
      }

    }
  }

  def contactApi(httpStatus: StatusCode, requestEntity: String) = {
    TestContactApi(escherConfig,
                   HttpResponse(httpStatus, Nil, HttpEntity(ContentTypes.`application/json`, requestEntity)))
  }
}
