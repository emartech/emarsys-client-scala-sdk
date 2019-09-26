package com.emarsys.client.predict

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.emarsys.client.Config.emsApi.predict
import com.emarsys.client.RestClient
import com.emarsys.escher.akka.http.config.EscherConfig
import fommil.sjs.FamilyFormats._
import spray.json._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

trait PredictApi extends RestClient {

  import PredictApi._

  val retryConfig = defaultRetryConfig.copy(maxRetries = 0)

  def recommendations(merchantId: String, predictIdentity: Option[PredictIdentity]): Future[List[Recommendation]] = {
    predictIdentity match {
      case Some(PredictIdentityHash(predictUserId, predictSecret)) =>
        recommendations(merchantId, predictUserId, predictSecret)
      case Some(PredictIdentityAuth(predictAuth)) => recommendations(merchantId, predictAuth)
      case _                                      => Future.successful(List())
    }
  }

  val baseUrl                                                 = s"${predict.protocol}://${predict.host}:${predict.port}"

  def recommendations(merchantId: String, emailHash: String, secret: String): Future[List[Recommendation]] = {
    val path = s"/merchants/$merchantId/"
    val query =
      s"?f=f:MAIL_PERSONAL,l:3,o:0&eh=${URLEncoder.encode(emailHash, "UTF-8")}&es=${URLEncoder.encode(secret, "UTF-8")}&test=true"

    sendRequest(path, query)
  }

  def recommendations(merchantId: String, predictAuth: String): Future[List[Recommendation]] = {
    val path = s"/merchants/$merchantId/"
    val query =
      s"?f=f:MAIL_PERSONAL,l:3,o:0&ci=${URLEncoder.encode(predictAuth, "UTF-8")}&test=true"

    sendRequest(path, query)
  }

  def sendRequest(path: String, query: String): Future[List[Recommendation]] = {
    val request = RequestBuilding.Get(Uri(baseUrl + path + query))

    run[RecommendationResponse](request, retryConfig) map { response =>
      response.products.values.toList.flatMap(parseRecommendation)
    }
  }

  def loadProduct(merchantId: String, itemId: String): Future[Option[Recommendation]] = {
    val path = s"/productinfo/merchants/$merchantId/?v=i:$itemId"
    run[RawProducts](RequestBuilding.Get(Uri(baseUrl + path)), retryConfig) map { response =>
      response.values.toList.headOption.flatMap(parseRecommendation)
    }
  }

  def parseRecommendation(rawProduct: JsObject): Option[Recommendation] = {
    Try(rawProduct.convertTo[Recommendation]) match {
      case Success(recommendation) => Some(recommendation)
      case Failure(e) =>
        system.log.warning("Failed to parse recommendation: {} error: {}", rawProduct, e.getMessage)
        None
    }
  }
}

object PredictApi {

  type RawProducts = Map[String, JsObject]
  final case class RecommendationResponse(products: RawProducts)

  sealed trait PredictIdentity
  final case class PredictIdentityHash(predictUserId: String, predictSecret: String) extends PredictIdentity
  final case class PredictIdentityAuth(predictAuth: String)                          extends PredictIdentity

  final case class Recommendation(title: String, image: Option[String])

  def apply(eConfig: EscherConfig)(
      implicit
      sys: ActorSystem,
      mat: Materializer,
      ex: ExecutionContextExecutor
  ): PredictApi =
    new RestClient with PredictApi {
      implicit override val system: ActorSystem                = sys
      implicit override val materializer: Materializer         = mat
      implicit override val executor: ExecutionContextExecutor = ex
    }
}
