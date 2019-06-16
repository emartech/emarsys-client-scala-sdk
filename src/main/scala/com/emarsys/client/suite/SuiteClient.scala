package com.emarsys.client.suite

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.scaladsl.Flow
import com.emarsys.client.Config.emsApi.suite
import com.emarsys.client.RestClient

import scala.concurrent.Future

trait SuiteClient extends RestClient {

  import SuiteClient._

  val serviceName = suite.serviceName
  override lazy val connectionFlow: Flow[HttpRequest, HttpResponse, _] =
    if (suite.protocol == "https") Http().outgoingConnectionHttps(suite.host, suite.port)
    else Http().outgoingConnection(suite.host, suite.port)

  protected def createCustomerHeader(customerId: Int) = RawHeader("X-SUITE-CUSTOMERID", customerId.toString)

  def baseUrl(customerId: Int) =
    s"${suite.protocol}://${suite.host}${suite.apiPath}/$customerId/"

  def runSuiteRequest[S](
      request: HttpRequest,
      maxRetries: Int
  )(implicit um: Unmarshaller[ResponseEntity, SuiteRawResponse[S]]): Future[SuiteRawResponse[S]] =
    runSigned[SuiteRawResponse[S]](request, serviceName, Nil, maxRetries)
}

object SuiteClient {
  case class SuiteRawResponse[T](replyCode: Int, replyText: String, data: T)
}
