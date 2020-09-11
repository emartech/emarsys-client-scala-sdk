package com.emarsys.client.predict

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import com.emarsys.client.RestClientErrors.RestClientException
import com.emarsys.client.predict.PredictApi.{PredictIdentityAuth, PredictIdentityHash, Recommendation}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class PredictApiSpec extends AsyncWordSpec with Matchers with ScalaFutures with PredictApi {
  implicit val system          = ActorSystem("predict-api-test-system")
  implicit val executor        = system.dispatcher
  implicit val defaultPatience = PatienceConfig(timeout = Span(2, Seconds), interval = Span(100, Millis))

  val escherConfig = new EscherConfig(ConfigFactory.load().getConfig("ems-api.escher"))

  val emailHash              = "email hash"
  val secret                 = "secret"
  val auth                   = "auth"
  val unauthorizedResponse   = """{"replyCode":1,"replyText":"Unauthorized","data":""}"""
  val invalidJsonResponse    = """invalid json"""
  val emptyProductResponse   = """{"products": {}}"""
  val invalidProductResponse = """{
                                 |    "products": {
                                 |        "invalid": {
                                 |            "item": "FW14-520087030"
                                 |        }
                                 |    }
                                 |}""".stripMargin
  val validResponse =
    """{
                               |    "cohort": "AAAA",
                               |    "features": {},
                               |    "products": {
                               |        "withImage": {
                               |            "image": "http://demandware.edgesuite.net/aatt_prd/on/demandware.static/-/Sites-Bossini_Catalog/default/dw8923eeb4/images/Products/FW14-520087030/FW14-520087030_01_normal_49.jpg",
                               |            "title": "Long Sleeve Print Mock Neck T-Shirt"
                               |        },
                               |        "invalid": {
                               |            "item": "FW14-520087030"
                               |        },
                               |        "withoutImage": {
                               |            "title": "Long Sleeve Print Fleece Tunic Sweatshirt"
                               |        }
                               |    },
                               |    "session": "179C9E00A32028C",
                               |    "visitor": "4330136DDC76BD77"
                               |}""".stripMargin
  val validProduct   = """{
                              |  "2129": {
                              |    "title": "LSL Women Hat 60s Hat",
                              |    "image": "http://lifestylelabels.com/pub/media/catalog/product/w/h/wh001.jpg"
                              |  }
                              |}""".stripMargin
  val invalidProduct = """{"2129": {}}"""

  "Predict Api" when {
    "recommendation called" should {
      "return failed future in case of invalid response" which {
        "is invalid json" in {
          recoverToSucceededIf[Exception] {
            recommendations("invalidJsonResponse", Some(PredictIdentityHash(emailHash, secret)))
          }
        }

        "is unauthorized" in {
          recoverToSucceededIf[RestClientException] {
            recommendations("unauthorizedResponse", Some(PredictIdentityHash(emailHash, secret)))
          }
        }
      }

      "return empty recommendation if the response has no product" in {
        recommendations("emptyProductResponse", Some(PredictIdentityHash(emailHash, secret))) map { response =>
          response shouldEqual List.empty[Recommendation]
        }
      }

      "return empty recommendation if the response has no product with authentication auth" in {
        recommendations("emptyProductResponse", Some(PredictIdentityAuth(auth))) map { response =>
          response shouldEqual List.empty[Recommendation]
        }
      }

      "return empty recommendation if the response has invalid products" in {
        recommendations("invalidProductResponse", Some(PredictIdentityHash(emailHash, secret))) map { response =>
          response shouldEqual List.empty[Recommendation]
        }
      }

      "return empty recommendation if the response has invalid products with authentication auth" in {
        recommendations("invalidProductResponse", Some(PredictIdentityAuth(auth))) map { response =>
          response shouldEqual List.empty[Recommendation]
        }
      }

      "return valid recommendation object on valid response - exclude invalid products" in {
        recommendations("validResponse", Some(PredictIdentityHash(emailHash, secret))) map { response =>
          response shouldEqual List(
            Recommendation(
              "Long Sleeve Print Mock Neck T-Shirt",
              Some(
                "http://demandware.edgesuite.net/aatt_prd/on/demandware.static/-/Sites-Bossini_Catalog/default/dw8923eeb4/images/Products/FW14-520087030/FW14-520087030_01_normal_49.jpg"
              )
            ),
            Recommendation(
              "Long Sleeve Print Fleece Tunic Sweatshirt",
              None
            )
          )
        }
      }

      "return valid recommendation object on valid response - exclude invalid products with authentication auth " in {
        recommendations("validResponse", Some(PredictIdentityAuth(auth))) map { response =>
          response shouldEqual List(
            Recommendation(
              "Long Sleeve Print Mock Neck T-Shirt",
              Some(
                "http://demandware.edgesuite.net/aatt_prd/on/demandware.static/-/Sites-Bossini_Catalog/default/dw8923eeb4/images/Products/FW14-520087030/FW14-520087030_01_normal_49.jpg"
              )
            ),
            Recommendation(
              "Long Sleeve Print Fleece Tunic Sweatshirt",
              None
            )
          )
        }
      }
    }

    "load product called" should {
      "return product object if item found" in {
        loadProduct("validProduct", "[itemId]") map { response =>
          response.get shouldEqual Recommendation(
            "LSL Women Hat 60s Hat",
            Some("http://lifestylelabels.com/pub/media/catalog/product/w/h/wh001.jpg")
          )
        }
      }

      "return None if item not found" in {
        loadProduct("invalidProduct", "[itemId]") map { response =>
          response shouldEqual None
        }
      }
    }
  }

  override protected def sendRequest(request: HttpRequest): Future[HttpResponse] =
    Future.successful(
      request match {
        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validRecommendationUri(uri) && validPath(uri)("merchants/validResponse/") =>
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, validResponse))

        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validRecommendationUriFromAuth(uri) && validPath(uri)("merchants/validResponse/") =>
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, validResponse))

        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validRecommendationUri(uri) && validPath(uri)("merchants/invalidProductResponse/") =>
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, invalidProductResponse))

        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validRecommendationUriFromAuth(uri) && validPath(uri)("merchants/invalidProductResponse/") =>
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, invalidProductResponse))

        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validRecommendationUri(uri) && validPath(uri)("merchants/emptyProductResponse/") =>
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, emptyProductResponse))

        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validRecommendationUriFromAuth(uri) && validPath(uri)("merchants/emptyProductResponse/") =>
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, emptyProductResponse))

        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validRecommendationUri(uri) && validPath(uri)("merchants/unauthorizedResponse/") =>
          HttpResponse(Unauthorized, Nil, HttpEntity(ContentTypes.`application/json`, unauthorizedResponse))

        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validRecommendationUri(uri) && validPath(uri)("merchants/invalidJsonResponse/") =>
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, invalidJsonResponse))

        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validLoadProductUri(uri) && validPath(uri)("productinfo/merchants/validProduct/") =>
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, validProduct))

        case HttpRequest(HttpMethods.GET, uri, _, _, _)
            if validLoadProductUri(uri) && validPath(uri)("productinfo/merchants/invalidProduct/") =>
          HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, invalidProduct))

        case r @ HttpRequest(HttpMethods.GET, _, _, _, _) =>
          system.log.error("Unexpected request: {}", r)
          HttpResponse(InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, ""))
      }
    )

  val validPath = (u: Uri) => u.path.toString endsWith _
  val validHost = (u: Uri) => u.scheme == "https" && u.authority.toString == "recommender.scarabresearch.com"

  val validRecommendationUri = (u: Uri) => {
    u.rawQueryString.fold(false) { p =>
      (p startsWith "f=f:MAIL_PERSONAL,l:3,o:0") &&
      p.contains(s"&eh=${URLEncoder.encode(emailHash, "UTF-8")}") &&
      p.contains(s"&es=$secret") &&
      p.contains(s"&test=true")
    } && validHost(u)
  }

  val validRecommendationUriFromAuth = (u: Uri) => {
    u.rawQueryString.fold(false) { p =>
      (p startsWith "f=f:MAIL_PERSONAL,l:3,o:0") &&
      p.contains(s"&ci=${URLEncoder.encode(auth, "UTF-8")}") &&
      p.contains(s"&test=true")
    } && validHost(u)
  }

  val validLoadProductUri = (u: Uri) => {
    u.rawQueryString.fold(false)(_ == s"v=i:${URLEncoder.encode("[itemId]", "UTF-8")}") && validHost(u)
  }
}
