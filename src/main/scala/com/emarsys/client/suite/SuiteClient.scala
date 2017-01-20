package com.emarsys.client.suite

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.scaladsl.Flow
import com.emarsys.client.Config.suiteConfig
import com.emarsys.client.RestClient

import scala.concurrent.Future

private[suite] trait SuiteClient extends RestClient {

  import SuiteClient._

  val serviceName = suiteConfig.serviceName
  lazy val connectionFlow: Flow[HttpRequest, HttpResponse, _] = Http().outgoingConnectionHttps(suiteConfig.host)

  protected def createCustomerHeader(customerId: Int) = RawHeader("X-SUITE-CUSTOMERID", customerId.toString)

  def baseUrl(customerId: Int) =
    s"https://${suiteConfig.host}:${suiteConfig.port}${suiteConfig.suiteApiPath}/$customerId/"

  def run[S](request: HttpRequest)(implicit um: Unmarshaller[ResponseEntity, SuiteRawResponse[S]]): Future[SuiteRawResponse[S]] =
    runRaw[SuiteRawResponse[S]](request)
}

object SuiteClient {
  case class SuiteRawResponse[T](replyCode: Int, replyText: String, data: T)
}