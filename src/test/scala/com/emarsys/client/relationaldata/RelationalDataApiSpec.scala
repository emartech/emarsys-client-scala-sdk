package com.emarsys.client.relationaldata

import java.util.Optional

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import com.emarsys.client.Config.RetryConfig
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import spray.json.JsString

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class RelationalDataApiSpec extends AsyncWordSpec with Matchers {
  implicit val system   = ActorSystem("relational-data-api-test-system")
  implicit val executor = system.dispatcher

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

  var calledRequest: Option[HttpRequest] = None

  object TestRelationalDataApi {
    def apply(eConfig: EscherConfig)(implicit
        sys: ActorSystem,
        ex: ExecutionContextExecutor
    ): RelationalDataApi = {
      new RelationalDataApi {
        implicit override val system   = sys
        implicit override val executor = ex
        override val escherConfig      = eConfig

        override def runSigned[S](
            request: HttpRequest,
            serviceName: String,
            headers: List[String],
            retryConfig: RetryConfig
        )(implicit
            um: Unmarshaller[ResponseEntity, S]
        ): Future[S] = {
          calledRequest = Some(request)
          super.runSigned(request, serviceName, headers, retryConfig)
        }
      }
    }
  }

  "RelationalDataApi " should {
    "send valid request to proper Uri" in {
      TestRelationalDataApi(escherConfig).insertIgnore(1, "animal", List.empty, Some("pubsub"))
      calledRequest.get.uri.toString() should endWith("/tables/animal/records")
      calledRequest.get.getHeader("x-suite-customerid") should equal(Optional.of(RawHeader("x-suite-customerid", "1")))
      calledRequest.get.getHeader("X-Forwarded-Service") should equal(
        Optional.of(RawHeader("X-Forwarded-Service", "pubsub"))
      )
    }

    "send the payload with the request" in {
      val payload = Seq(
        Map(
          "cica"  -> JsString("cirmos"),
          "kutya" -> JsString("aaaa")
        )
      )
      val expectedPayload = """[{"cica":"cirmos","kutya":"aaaa"}]"""

      TestRelationalDataApi(escherConfig).insertIgnore(1, "animal", payload, None)

      val body = Await.result(Unmarshal(calledRequest.get.entity).to[String], Duration.Inf)
      body should equal(expectedPayload)
    }
  }
}
