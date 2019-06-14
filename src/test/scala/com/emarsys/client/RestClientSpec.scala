package com.emarsys.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, BufferOverflowException, Materializer, StreamTcpException}
import akka.stream.scaladsl.{Flow, Sink}
import akka.testkit.TestKit
import com.emarsys.client.RestClientErrors.RestClientException
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.util.{Failure, Try}

class RestClientSpec extends TestKit(ActorSystem("RestClientSpec")) with WordSpecLike with Matchers with ScalaFutures {
  self =>

  val escherConf   = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))
  implicit val mat = ActorMaterializer()
  val timeout      = 3.seconds
  val url          = "http://test.example.com/testEndpoint"

  trait Scope extends RestClient {
    implicit override val system: ActorSystem                = self.system
    implicit override val materializer: Materializer         = mat
    implicit override val executor: ExecutionContextExecutor = self.system.dispatcher
    override val serviceName: String                         = "test"
    override val escherConfig: EscherConfig                  = escherConf
    override val initialDelay: FiniteDuration                = 10.millis
  }

  "#runRawWithHeader" should {

    "return ok if everything is ok" in new Scope {
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] =
        Flow[HttpRequest].map(_ => HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))

      Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout) shouldBe "{}"
    }

    "return fail instantly on non server error" in new Scope {
      var counter = 0
      val counterFn = () => {
        _: HttpRequest => {
          counter += 1
          List(HttpResponse(StatusCodes.NotFound, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
        }
      }
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].statefulMapConcat(counterFn)

      Try(Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 404, "{}")
      )
      counter shouldBe 1
    }

    "return 429 instantly on buffer overflow" in new Scope {
      var counter = 0
      val counterFn = () => {
        _: HttpRequest => {
          counter += 1
          throw BufferOverflowException("overflow")
        }
      }
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].statefulMapConcat(counterFn)

      Try(Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 429, "overflow")
      )
      counter shouldBe 1
    }

    "return fail if all attempt is failed with server error" in new Scope {
      var counter = 0
      val counterFn = () => {
        _: HttpRequest => {
          counter += 1
          List(HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
        }
      }
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].statefulMapConcat(counterFn)

      Try(Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 500, "{}")
      )
      counter shouldBe 4
    }

    "return 504 if all attempt is failed with stream tcp exception" in new Scope {
      var counter = 0
      val counterFn = () => {
        _: HttpRequest => {
          counter += 1
          throw new StreamTcpException("timeout")
        }
      }
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].statefulMapConcat(counterFn)

      Try(Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 504, "timeout")
      )
      counter shouldBe 4
    }

    "return ok if any attempt is ok in the retry range" in new Scope {
      var counter = 0
      val counterFn = () => {
        _: HttpRequest => {
          counter += 1
          if (counter < 2)
            List(HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
          else
            List(HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
        }
      }
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].statefulMapConcat(counterFn)

      Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout) shouldBe "{}"
      counter shouldBe 2
    }

    "use at least exponential backoff when retrying failed request" in new Scope {
      val retries = 4
      var counter = 0
      val counterFn = () => {
        _: HttpRequest => {
          counter += 1
          List(HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
        }
      }
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].statefulMapConcat(counterFn)

      val start         = System.currentTimeMillis()
      val result        = Try(Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, retries), timeout))
      val end           = System.currentTimeMillis()
      val expectedDelay = initialDelay * (1 << (retries + 1) - 1)

      val elapsed = (end - start).millis
      counter shouldBe retries + 1
      result shouldBe Failure(RestClientException(s"Rest client request failed for $url", 500, "{}"))
      elapsed should be > expectedDelay
    }
  }

  "#runStreamWithHeader" should {
    "runStreamWithHeader return ok if everything is ok" in new Scope {
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] =
        Flow[HttpRequest].map(_ => HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))

      Await
        .result(runStreamWithHeader(HttpRequest(uri = url), Nil, 3).map(_.utf8String).runWith(Sink.seq), timeout) shouldBe Seq(
        "{}"
      )
    }
  }
}
