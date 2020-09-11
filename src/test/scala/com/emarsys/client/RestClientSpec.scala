package com.emarsys.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Sink, TcpIdleTimeoutException}
import akka.stream.{BufferOverflowException, StreamTcpException}
import akka.testkit.TestKit
import com.emarsys.client
import com.emarsys.client.Config.RetryConfig
import com.emarsys.client.RestClientErrors.RestClientException
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Try}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class RestClientSpec extends TestKit(ActorSystem("RestClientSpec")) with AnyWordSpecLike with Matchers with ScalaFutures {
  self =>

  val timeout      = 3.seconds
  val url          = "http://test.example.com/testEndpoint"

  trait Scope extends RestClient {
    implicit override val system: ActorSystem                = self.system
    implicit override val executor: ExecutionContextExecutor = self.system.dispatcher
    override val defaultRetryConfig: RetryConfig =
      RetryConfig(maxRetries = 3, dontRetryAfter = 1.second, initialRetryDelay = 10.millis)
  }

  "#run" should {
    "return ok if everything is ok" in new Scope {
      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] =
        Future.successful(HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))

      Await.result(run[String](HttpRequest(uri = url)), timeout) shouldBe "{}"
    }

    "return fail instantly on non server error" in new Scope {
      var counter = 0
      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
        counter += 1
        Future.successful(HttpResponse(StatusCodes.NotFound, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
      }

      Try(Await.result(run[String](HttpRequest(uri = url)), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 404, "{}")
      )
      counter shouldBe 1
    }

    "return 429 instantly on buffer overflow" in new Scope {
      var counter = 0
      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
        counter += 1
        Future.failed(BufferOverflowException("overflow"))
      }

      Try(Await.result(run[String](HttpRequest(uri = url)), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 429, "overflow")
      )
      counter shouldBe 1
    }

    "return fail if all attempt is failed with server error" in new Scope {
      var counter = 0
      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
        counter += 1
        Future.successful(
          HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}"))
        )
      }

      Try(Await.result(run[String](HttpRequest(uri = url)), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 500, "{}")
      )
      counter shouldBe 4
    }

    "not retry if maxRetryCount is set to 0" in new Scope {
      var counter = 0
      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
        counter += 1
        Future.successful(
          HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}"))
        )
      }

      private val retryConfig: client.Config.RetryConfig = defaultRetryConfig.copy(maxRetries = 0)
      Try(Await.result(run[String](HttpRequest(uri = url), retryConfig), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 500, "{}")
      )
      counter shouldBe 1
    }

    "return 504 if all attempt is failed with stream tcp exception" in new Scope {
      var counter = 0
      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
        counter += 1
        Future.failed(new StreamTcpException("timeout"))
      }
      Try(Await.result(run[String](HttpRequest(uri = url)), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 504, "timeout")
      )
      counter shouldBe 4
    }

    "return 504 if all attempt is failed with tcp idle timeout exception" in new Scope {
      var counter = 0

      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
        counter += 1
        Future.failed(new TcpIdleTimeoutException("timeout", Duration.Zero))
      }

      Try(Await.result(run[String](HttpRequest(uri = url)), timeout)) shouldBe Failure(
        RestClientException(s"Rest client request failed for $url", 504, "timeout")
      )
      counter shouldBe 4
    }

    "return ok if any attempt is ok in the retry range" in new Scope {
      var counter = 0

      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
        counter += 1
        if (counter < 2)
          Future.successful(
            HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}"))
          )
        else
          Future.successful(HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
      }
      Await.result(run[String](HttpRequest(uri = url)), timeout) shouldBe "{}"
      counter shouldBe 2
    }

    "use at least exponential backoff when retrying failed request" in new Scope {
      val retries = 4
      var counter = 0
      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
        counter += 1
        Future.successful(
          HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}"))
        )
      }
      val retryConfig = defaultRetryConfig.copy(maxRetries = retries, initialRetryDelay = 10.millis)

      val start         = System.currentTimeMillis()
      val result        = Try(Await.result(run[String](HttpRequest(uri = url), retryConfig), timeout))
      val end           = System.currentTimeMillis()
      val expectedDelay = retryConfig.initialRetryDelay * (1 << (retries + 1) - 1)

      val elapsed = (end - start).millis
      counter shouldBe retries + 1
      result shouldBe Failure(RestClientException(s"Rest client request failed for $url", 500, "{}"))
      elapsed should be > expectedDelay
    }

    "not retry after dontRetryAfter is elapsed" in new Scope {
      val retries = 4
      var counter = 0

      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
        counter += 1
        Future.successful(
          HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}"))
        )
      }

      val retryConfig = RetryConfig(retries, 50.millis, 10.millis)

      val result = Try(Await.result(run[String](HttpRequest(uri = url), retryConfig), timeout))

      counter shouldBe 3
      result shouldBe Failure(RestClientException(s"Rest client request failed for $url", 500, "{}"))
    }
  }

  "#runStreamed" should {
    "return ok if everything is ok" in new Scope {
      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] =
        Future.successful(HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))

      Await.result(runStreamed(HttpRequest(uri = url)).map(_.utf8String).runWith(Sink.seq), timeout) shouldBe Seq("{}")
    }
  }

  "#runRaw" should {
    "return ok if everything is ok" in new Scope {
      override protected def sendRequest(request: HttpRequest): Future[HttpResponse] =
        Future.successful(HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))

      val result = Await.result(runRaw(HttpRequest(uri = url)), timeout)
      result.status should ===(StatusCodes.OK)
      val responseBody = Await.result(result.entity.toStrict(timeout), timeout)
      responseBody.data.utf8String should ===("{}")
    }
  }
}
