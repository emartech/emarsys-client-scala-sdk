package com.emarsys.client.predict

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.stream.scaladsl.Flow
import akka.stream.ActorMaterializer
import com.emarsys.client.predict.PredictApi.{PredictIdentityAuth, PredictIdentityHash, Recommendation}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncWordSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

class PredictApiSpec extends AsyncWordSpec with Matchers with ScalaFutures with PredictApi {

  implicit val system          = ActorSystem("predict-api-test-system")
  implicit val materializer    = ActorMaterializer()
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
                                 |        "FW14-520087030": {
                                 |            "item": "FW14-520087030"
                                 |        }
                                 |    }
                                 |}""".stripMargin
  val validResponse          = """{
                               |    "cohort": "AAAA",
                               |    "features": {},
                               |    "products": {
                               |        "FW14-520087030": {
                               |            "category": "Big Sale>Ladies>Up to 80% off|Ladies>Categories>T-Shirts (Long Sleeves)",
                               |            "image": "http://demandware.edgesuite.net/aatt_prd/on/demandware.static/-/Sites-Bossini_Catalog/default/dw8923eeb4/images/Products/FW14-520087030/FW14-520087030_01_normal_49.jpg",
                               |            "item": "FW14-520087030",
                               |            "link": "http://www.bossini.com/en/ladies-tops-tshirts-long/FW14-520087030.html",
                               |            "msrp": 130.0,
                               |            "price": 40.0,
                               |            "title": "Long Sleeve Print Mock Neck T-Shirt"
                               |        },
                               |        "invalid": {
                               |            "item": "FW14-520087030"
                               |        },
                               |        "FW15-720318090": {
                               |            "category": "Big Sale>Ladies>Up to 80% off|Ladies>Categories>Sweatshirts",
                               |            "image": "http://demandware.edgesuite.net/aatt_prd/on/demandware.static/-/Sites-Bossini_Catalog/default/dw8923eeb4/images/Products/FW15-720318090/FW15-720318090_01_normal_97.jpg",
                               |            "item": "FW15-720318090",
                               |            "link": "http://www.bossini.com/en/ladies-tops-hoodies-sweatshirt/FW15-720318090.html",
                               |            "msrp": 280.0,
                               |            "price": 120.0,
                               |            "title": "Long Sleeve Print Fleece Tunic Sweatshirt"
                               |        }
                               |    },
                               |    "session": "179C9E00A32028C",
                               |    "visitor": "4330136DDC76BD77"
                               |}""".stripMargin
  val validProduct           = """{
                              |  "2129": {
                              |    "item": "2129",
                              |    "category": "WOMEN>Accessories",
                              |    "title": "LSL Women Hat 60s Hat",
                              |    "available": true,
                              |    "msrp": 55.0,
                              |    "price": 55.0,
                              |    "msrp_gpb": "45.76",
                              |    "price_gpb": "45.76",
                              |    "link": "http://lifestylelabels.com/lsl-women-hat-60s-hat.html",
                              |    "image": "http://lifestylelabels.com/pub/media/catalog/product/w/h/wh001.jpg",
                              |    "zoom_image": "http://lifestylelabels.com/pub/media/catalog/product/w/h/wh001.jpg"
                              |  }
                              |}""".stripMargin
  val validProductWithNoMsrp = """{
                              |  "2129": {
                              |    "item": "2129",
                              |    "category": "WOMEN>Accessories",
                              |    "title": "LSL Women Hat 60s Hat",
                              |    "available": true,
                              |    "price": 55.0,
                              |    "price_gpb": "45.76",
                              |    "link": "http://lifestylelabels.com/lsl-women-hat-60s-hat.html",
                              |    "image": "http://lifestylelabels.com/pub/media/catalog/product/w/h/wh001.jpg",
                              |    "zoom_image": "http://lifestylelabels.com/pub/media/catalog/product/w/h/wh001.jpg"
                              |  }
                              |}""".stripMargin
  val invalidProduct         = """{"2129": {}}"""

  "Predict Api" when {

    "recommendation called" should {

      "return failed future in case of invalid response" which {

        "is invalid json" in {
          recoverToSucceededIf[Exception] {
            recommendations("invalidJsonResponse", Some(PredictIdentityHash(emailHash, secret)))
          }
        }

        "is unauthorized" in {
          recoverToSucceededIf[Exception] {
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
              "FW14-520087030",
              "Long Sleeve Print Mock Neck T-Shirt",
              "http://www.bossini.com/en/ladies-tops-tshirts-long/FW14-520087030.html",
              "http://demandware.edgesuite.net/aatt_prd/on/demandware.static/-/Sites-Bossini_Catalog/default/dw8923eeb4/images/Products/FW14-520087030/FW14-520087030_01_normal_49.jpg",
              "Big Sale>Ladies>Up to 80% off|Ladies>Categories>T-Shirts (Long Sleeves)",
              Some(130.0f),
              40.0f),
            Recommendation(
              "FW15-720318090",
              "Long Sleeve Print Fleece Tunic Sweatshirt",
              "http://www.bossini.com/en/ladies-tops-hoodies-sweatshirt/FW15-720318090.html",
              "http://demandware.edgesuite.net/aatt_prd/on/demandware.static/-/Sites-Bossini_Catalog/default/dw8923eeb4/images/Products/FW15-720318090/FW15-720318090_01_normal_97.jpg",
              "Big Sale>Ladies>Up to 80% off|Ladies>Categories>Sweatshirts",
              Some(280.0f),
              120.0f)
          )
        }
      }

      "return valid recommendation object on valid response - exclude invalid products with authentication auth " in {
        recommendations("validResponse", Some(PredictIdentityAuth(auth))) map { response =>
          response shouldEqual List(
            Recommendation(
              "FW14-520087030",
              "Long Sleeve Print Mock Neck T-Shirt",
              "http://www.bossini.com/en/ladies-tops-tshirts-long/FW14-520087030.html",
              "http://demandware.edgesuite.net/aatt_prd/on/demandware.static/-/Sites-Bossini_Catalog/default/dw8923eeb4/images/Products/FW14-520087030/FW14-520087030_01_normal_49.jpg",
              "Big Sale>Ladies>Up to 80% off|Ladies>Categories>T-Shirts (Long Sleeves)",
              Some(130.0f),
              40.0f),
            Recommendation(
              "FW15-720318090",
              "Long Sleeve Print Fleece Tunic Sweatshirt",
              "http://www.bossini.com/en/ladies-tops-hoodies-sweatshirt/FW15-720318090.html",
              "http://demandware.edgesuite.net/aatt_prd/on/demandware.static/-/Sites-Bossini_Catalog/default/dw8923eeb4/images/Products/FW15-720318090/FW15-720318090_01_normal_97.jpg",
              "Big Sale>Ladies>Up to 80% off|Ladies>Categories>Sweatshirts",
              Some(280.0f),
              120.0f)
          )
        }
      }
    }

    "load product called" should {

      "return product object if item found" in {
        loadProduct("validProduct", "[itemId]") map { response =>
          response.get shouldEqual Recommendation("2129",
                                                  "LSL Women Hat 60s Hat",
                                                  "http://lifestylelabels.com/lsl-women-hat-60s-hat.html",
                                                  "http://lifestylelabels.com/pub/media/catalog/product/w/h/wh001.jpg",
                                                  "WOMEN>Accessories",
                                                  Some(55.0f),
                                                  55.0f)
        }
      }

      "return product object even if msrp is missing" in {
        loadProduct("validProductWithNoMsrp", "[itemId]") map { response =>
          response.get shouldEqual Recommendation("2129",
                                                  "LSL Women Hat 60s Hat",
                                                  "http://lifestylelabels.com/lsl-women-hat-60s-hat.html",
                                                  "http://lifestylelabels.com/pub/media/catalog/product/w/h/wh001.jpg",
                                                  "WOMEN>Accessories",
                                                  None,
                                                  55.0f)
        }
      }

      "return None if item not found" in {
        loadProduct("invalidProduct", "[itemId]") map { response =>
          response shouldEqual None
        }
      }
    }
  }

  override lazy val connectionFlow = Flow[HttpRequest].map {

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
        if validLoadProductUri(uri) && validPath(uri)("productinfo/merchants/validProductWithNoMsrp/") =>
      HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, validProductWithNoMsrp))

    case HttpRequest(HttpMethods.GET, uri, _, _, _)
        if validLoadProductUri(uri) && validPath(uri)("productinfo/merchants/invalidProduct/") =>
      HttpResponse(OK, Nil, HttpEntity(ContentTypes.`application/json`, invalidProduct))

    case r @ HttpRequest(HttpMethods.GET, _, _, _, _) =>
      system.log.error("Unexpected request: {}", r)
      HttpResponse(InternalServerError, Nil, HttpEntity(ContentTypes.`application/json`, ""))
  }

  val validPath = (u: Uri) => u.path.toString endsWith _
  val validHost = (u: Uri) => u.scheme == "https" && u.authority.toString == "recommender.scarabresearch.com:443"

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
    u.rawQueryString.fold(false)(_ == "v=i:[itemId]") && validHost(u)
  }
}
