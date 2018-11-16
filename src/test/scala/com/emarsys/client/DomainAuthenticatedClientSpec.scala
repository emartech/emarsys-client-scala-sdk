package com.emarsys.client
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.{Assertion, Matchers, WordSpecLike}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class DomainAuthenticatedClientSpec extends WordSpecLike with Matchers {

  implicit val sys = ActorSystem("domain-based-rest-client-spec")
  implicit val mat = ActorMaterializer()
  implicit val exe = sys.dispatcher

  trait Scope extends DomainAuthenticatedClient {
    override val system       = sys
    override val materializer = mat
    override val executor     = exe

    override val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))
    override val serviceName  = "default"

    val defaultEscherKey = "default-key"
    val service1EscherKey = "service1-key"

    def callSendRequest(uri: Uri) =
      Await.result(sendRequest(HttpRequest(uri = uri))(Future.successful), 3.seconds)

    def getXEmsAuthHeader(request: HttpRequest) =
      request.headers find (_.name()  == "X-Ems-Auth") map (_.value())
  }

  private def createConnectionFlow(assert: HttpRequest => Assertion): Flow[HttpRequest, HttpResponse, _] =
    Flow[HttpRequest] map { request =>
      assert(request)
      HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}"))
    }

  "#sendRequest" when {
    "no trusted service found in config with the given domain" should {
      "must sign the request with default credentials" in new Scope {
        override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = createConnectionFlow { request =>
          getXEmsAuthHeader(request).getOrElse("") should include (defaultEscherKey)
        }

        private val defaultUri = Uri("https://undefined.com")

        callSendRequest(defaultUri)
      }
    }

    "trusted service found in config by the given domain" should {
      "must sign the request with service1 credentials" in new Scope {
        override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = createConnectionFlow { request =>
          getXEmsAuthHeader(request).getOrElse("") should include(service1EscherKey)
        }

        private val serviceUri = Uri("https://service1.com/any/path")

        callSendRequest(serviceUri)
      }
    }

    "trusted service found in config by the secondary domain" should {
      "must sign the request with service1 credentials" in new Scope {
        override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = createConnectionFlow { request =>
          getXEmsAuthHeader(request).getOrElse("") should include (service1EscherKey)
        }

        private val serviceUri = Uri("https://service1-alias.com/any/path")

        callSendRequest(serviceUri)
      }
    }
  }
}
