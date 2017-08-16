package com.emarsys.client.predict

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.emarsys.escher.akka.http.config.EscherConfig
import spray.json._
import fommil.sjs.FamilyFormats._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.emarsys.client.Config.emsApi.predict
import com.emarsys.client.RestClient

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

trait PredictApi extends RestClient {

  import PredictApi._

  def recommendations(merchantId: String, predictIdentity: Option[PredictIdentity]): Future[List[Recommendation]] = {
    predictIdentity match {
      case Some(PredictIdentityHash(predictUserId, predictSecret)) => recommendations(merchantId, predictUserId, predictSecret)
      case Some(PredictIdentityAuth(predictAuth)) => recommendations(merchantId, predictAuth)
      case _ =>  Future.successful(List())
    }
  }


  val serviceName = predict.serviceName
  val baseUrl     = s"${predict.protocol}://${predict.host}:${predict.port}"
  lazy val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Http().outgoingConnectionHttps(predict.host)

  override def signRequest(serviceName: String)(implicit ec: ExecutionContext, mat: Materializer) =
    r => Future.successful(r)

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

    runRaw[RecommendationResponse](request) map { response =>
      response.products.values.toList.flatMap(parseRecommendation)
    }
  }

  def loadProduct(merchantId: String, itemId: String): Future[Option[Recommendation]] = {
    val path = s"/productinfo/merchants/$merchantId/?v=i:$itemId"
    runRaw[RawProducts](RequestBuilding.Get(Uri(baseUrl + path))) map { response =>
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

  final case class LastSession(productCategory: String, date: String, productName: String, timeSpent: Int)
  final case class PredictData(recommendations: List[Recommendation], lastSession: Option[LastSession])
  object PredictData { def empty = PredictData(List(), None) }
  final case class Recommendation(item: String, title: String, link: String, image: String, category: String, msrp: Option[Float], price: Float)

  def apply(eConfig: EscherConfig)(
    implicit
    sys: ActorSystem,
    mat: Materializer,
    ex: ExecutionContextExecutor): PredictApi =
    new RestClient with PredictApi {
      override implicit val system: ActorSystem                = sys
      override implicit val materializer: Materializer         = mat
      override implicit val executor: ExecutionContextExecutor = ex
      override val escherConfig: EscherConfig                  = eConfig
    }
}
