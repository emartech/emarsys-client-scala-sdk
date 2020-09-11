package com.emarsys.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Sink
import akka.testkit.TestKit
import com.emarsys.client.Config.RetryConfig
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.OptionValues

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class EscherRestClientSpec
    extends TestKit(ActorSystem("RestClientSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with OptionValues {
  self =>

  val escherConf = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))
  val timeout    = 3.seconds
  val url        = "http://test.example.com/testEndpoint"

  val serviceName = "service1"

  trait Scope extends EscherRestClient {
    implicit override val system: ActorSystem                = self.system
    implicit override val executor: ExecutionContextExecutor = self.system.dispatcher
    override val defaultRetryConfig: RetryConfig =
      RetryConfig(maxRetries = 3, dontRetryAfter = 1.second, initialRetryDelay = 10.millis)
    override val escherConfig: EscherConfig = escherConf

    var requests: List[HttpRequest] = List.empty
    override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
      requests :+= request
      Future.successful(HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
    }
  }

  "#runSigned" should {
    "add the X-Ems-Auth header" in new Scope {
      Await.result(runSigned[String](HttpRequest(uri = url), serviceName, List("my-header")), timeout) shouldBe "{}"

      requests should have size 1
      requests.head.headers.map(_.name()) should contain("X-Ems-Auth")
      val authHeader = requests.head.headers.find(_.name() == "X-Ems-Auth").value
      authHeader.value() should include("my-header")
      authHeader.value() should include regex "service1-key/\\d{8}/service1-scope"
    }
  }

  "#runRawSigned" should {
    "add the X-Ems-Auth header" in new Scope {
      Await.result(runRawSigned(HttpRequest(uri = url), serviceName, List("my-header")), timeout)

      requests should have size 1
      requests.head.headers.map(_.name()) should contain("X-Ems-Auth")
      val authHeader = requests.head.headers.find(_.name() == "X-Ems-Auth").value
      authHeader.value() should include("my-header")
      authHeader.value() should include regex "service1-key/\\d{8}/service1-scope"
    }
  }

  "#runStreamedSigned" should {
    "add the X-Ems-Auth header" in new Scope {
      runStreamSigned(HttpRequest(uri = url), serviceName, List("my-header")).runWith(Sink.ignore)

      requests should have size 1
      requests.head.headers.map(_.name()) should contain("X-Ems-Auth")
      val authHeader = requests.head.headers.find(_.name() == "X-Ems-Auth").value
      authHeader.value() should include("my-header")
      authHeader.value() should include regex "service1-key/\\d{8}/service1-scope"
    }
  }
}
