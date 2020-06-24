package com.emarsys.client.suite

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.emarsys.client.Config.RetryConfig
import com.emarsys.client.Config.emsApi.suite
import com.emarsys.client.EscherRestClient

import scala.concurrent.Future

trait SuiteClient extends EscherRestClient {
  import SuiteClient._

  val serviceName = suite.serviceName

  protected def createCustomerHeader(customerId: Int) = RawHeader("X-SUITE-CUSTOMERID", customerId.toString)

  def baseUrl(customerId: Int) =
    s"${suite.protocol}://${suite.host}:${suite.port}${suite.apiPath}/$customerId/"

  def runSuiteRequest[S](
      request: HttpRequest,
      retryConfig: RetryConfig
  )(implicit um: Unmarshaller[ResponseEntity, SuiteRawResponse[S]]): Future[SuiteRawResponse[S]] =
    runSigned[SuiteRawResponse[S]](request, serviceName, Nil, retryConfig)
}

object SuiteClient {
  case class SuiteRawResponse[T](replyCode: Int, replyText: String, data: T)
}
