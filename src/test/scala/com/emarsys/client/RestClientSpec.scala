package com.emarsys.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import com.emarsys.client.RestClientErrors.RestClientException
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.{Failure, Try}

class RestClientSpec extends WordSpecLike with Matchers with ScalaFutures {

  val escherConf   = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))
  implicit val sys = ActorSystem("predict-api-test-system")
  implicit val mat = ActorMaterializer()
  implicit val exe = sys.dispatcher
  val timeout      = 3.seconds
  val url          = "http://test.example.com/testEndpoint"

  trait Scope extends RestClient {
    override implicit val system: ActorSystem                = sys
    override implicit val materializer: Materializer         = mat
    override implicit val executor: ExecutionContextExecutor = exe
    override val serviceName: String                         = "test"
    override val escherConfig: EscherConfig                  = escherConf
  }

  "RestClient" should {

    "runRawWithHeader return ok if everything is ok" in new Scope {
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map(_ => HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))

      Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout) shouldBe "{}"
    }

    "runStreamWithHeader return ok if everything is ok" in new Scope {
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map(_ => HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))

      Await.result(runStreamWithHeader(HttpRequest(uri = url), Nil, 3).map(_.utf8String).runWith(Sink.seq), timeout) shouldBe Seq("{}")
    }

    "runRawWithHeader return fail instantly on non server error" in new Scope {
      var counter = 0
      val counterFn = () => {
        _: HttpRequest => {
          counter += 1
          List(HttpResponse(StatusCodes.NotFound, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
        }
      }
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].statefulMapConcat(counterFn)

      Try(Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout)) shouldBe Failure(RestClientException(s"Rest client request failed for $url", 404, "{}"))
      counter shouldBe 1
    }

    "runRawWithHeader return fail if all attempt is failed with server error" in new Scope {
      var counter = 0
      val counterFn = () => {
        _: HttpRequest => {
          counter += 1
          List(HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
        }
      }
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].statefulMapConcat(counterFn)

      Try(Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout)) shouldBe Failure(RestClientException(s"Rest client request failed for $url", 500, "{}"))
      counter shouldBe 4
    }


    "runRawWithHeader return ok if any attempt is ok in the retry range" in new Scope {
      var counter = 0
      val counterFn = () => {
        _: HttpRequest => {
          counter += 1
          if(counter < 2)
            List(HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
          else
            List(HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`application/json`, "{}")))
        }
      }
      override val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].statefulMapConcat(counterFn)

      Await.result(runRawWithHeader[String](HttpRequest(uri = url), Nil, 3), timeout) shouldBe "{}"
      counter shouldBe 2
    }


  }
}
