package com.emarsys.client.relationaldata

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.{ActorMaterializer, Materializer}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncWordSpec, Matchers}
import spray.json.JsString

import scala.concurrent.{ExecutionContextExecutor, Future}

class RelationalDataApiSpec extends AsyncWordSpec with Matchers {
  implicit val system       = ActorSystem("relational-data-api-test-system")
  implicit val materializer = ActorMaterializer()
  implicit val executor     = system.dispatcher

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

  var calledRequest: Option[HttpRequest] = None

  object TestRelationalDataApi {


    def apply(eConfig: EscherConfig)(
      implicit
      sys: ActorSystem,
      mat: Materializer,
      ex: ExecutionContextExecutor): RelationalDataApi = {

      new RelationalDataApi {
        override implicit val system       = sys
        override implicit val materializer = mat
        override implicit val executor     = ex
        override val escherConfig          = eConfig

        override def runRaw[S](request: HttpRequest, retry: Int)(implicit um: Unmarshaller[ResponseEntity, S]): Future[S] = {
          calledRequest = Some(request)
          super.runRaw(request, retry)
        }
      }

    }

  }

  "RelationalDataApi " should {

    "send valid request to proper Uri" in {
      TestRelationalDataApi(escherConfig).insertIgnore(1,"animal", List.empty)
      calledRequest.get.uri.toString() should endWith("/customers/1/tables/animal/records")
    }

    "send the payload with the request" in {

      val payload = Seq(
        Map(
          "cica" -> JsString("cirmos"),
          "kutya" -> JsString("aaaa")
        )
      )
      val expectedPayload = """[{"cica":"cirmos","kutya":"aaaa"}])"""

      TestRelationalDataApi(escherConfig).insertIgnore(1,"animal", payload)

      calledRequest.get.entity.toString should endWith(expectedPayload)
    }

  }

}
