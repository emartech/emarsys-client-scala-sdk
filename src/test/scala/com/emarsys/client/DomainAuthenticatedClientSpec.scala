package com.emarsys.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class DomainAuthenticatedClientSpec extends AnyWordSpecLike with Matchers {
  implicit val sys = ActorSystem("domain-based-rest-client-spec")
  implicit val mat = ActorMaterializer()
  implicit val exe = sys.dispatcher

  trait Scope extends DomainAuthenticatedClient {
    override val system       = sys
    override val materializer = mat
    override val executor     = exe

    override val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

    val service1EscherKey = "service1-key"

    def callSendRequest(uri: Uri) =
      Await.result(send(HttpRequest(uri = uri)), 3.seconds)

    def getXEmsAuthHeader(request: HttpRequest) =
      request.headers find (_.name() == "X-Ems-Auth") map (_.value())
  }

  val defaultResponse =
    Future.successful(HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))

  "#sendRequest" when {
    "no trusted service found in config with the given domain" should {
      "not sign the request" in new Scope {
        override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
          getXEmsAuthHeader(request) shouldBe None
          defaultResponse
        }

        private val defaultUri = Uri("https://undefined.com")

        callSendRequest(defaultUri)
      }
    }

    "trusted service found in config by the given domain" should {
      "sign the request with service1 credentials" in new Scope {
        override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
          getXEmsAuthHeader(request).getOrElse("") should include(service1EscherKey)
          defaultResponse
        }

        private val serviceUri = Uri("https://service1.com/any/path")

        callSendRequest(serviceUri)
      }
    }

    "trusted service found in config by the secondary domain" should {
      "sign the request with service1 credentials" in new Scope {
        override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
          getXEmsAuthHeader(request).getOrElse("") should include(service1EscherKey)
          defaultResponse
        }

        private val serviceUri = Uri("https://service1-alias.com/any/path")

        callSendRequest(serviceUri)
      }
    }
  }
}
