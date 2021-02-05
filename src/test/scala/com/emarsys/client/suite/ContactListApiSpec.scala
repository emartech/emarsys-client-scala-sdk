package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.{OK, Unauthorized}
import akka.http.scaladsl.model._
import com.emarsys.client.RestClientErrors.{InvalidResponseFormatException, RestClientException}
import com.emarsys.client.suite.ContactListApi.ContactList
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.{ExecutionContextExecutor, Future}

class ContactListApiSpec extends AsyncWordSpec with Matchers with ScalaFutures {

  implicit val system   = ActorSystem("contactlist-api-test-system")
  implicit val executor = system.dispatcher

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

  object TestContactListApi {
    def apply(
        eConfig: EscherConfig,
        response: HttpResponse
    )(implicit sys: ActorSystem, ex: ExecutionContextExecutor) =
      new SuiteClient with ContactListApi {
        implicit override val system   = sys
        implicit override val executor = ex
        override val escherConfig      = eConfig

        override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = Future.successful(response)
      }
  }

  val customerId        = 123
  val invalidResponse   = "invalid"
  val emptyDataResponse = """{"replyCode":1,"replyText":"Unauthorized","data":""}"""
  val validResponse =
    """
      |{
      |  "replyCode": 0,
      |  "replyText": "OK",
      |  "data": [
      |    {
      |      "id": "482019135",
      |      "name": "123",
      |      "created": "2020-11-27 14:00:19",
      |      "type": 0
      |    },
      |    {
      |      "id": "479866336",
      |      "name": "A",
      |      "created": "2020-11-27 13:24:26",
      |      "type": 0
      |    }
      |  ]
      |}
      |""".stripMargin

  "ContactList Api" when {
    "contactLists called" should {
      "return existing fields in case of successful response" in {
        contactListApi(OK, validResponse).contactLists(customerId) map { response =>
          response shouldBe List(
            ContactList("482019135", "123", "2020-11-27 14:00:19", 0),
            ContactList("479866336", "A", "2020-11-27 13:24:26", 0)
          )
        }
      }

      "return failure in case of non-success status code" in {
        recoverToSucceededIf[RestClientException] {
          contactListApi(Unauthorized, emptyDataResponse).contactLists(customerId)
        }
      }

      "failed unmarshall" in {
        recoverToSucceededIf[InvalidResponseFormatException] {
          contactListApi(OK, "[]").contactLists(customerId)
        }
      }
    }
  }

  def contactListApi(httpStatus: StatusCode, requestEntity: String) = {
    TestContactListApi(
      escherConfig,
      HttpResponse(httpStatus, Nil, HttpEntity(ContentTypes.`application/json`, requestEntity))
    )
  }

}
