package com.emarsys.client

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, ResponseEntity}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.emarsys.client.Config.RetryConfig
import com.emarsys.client.RestClientErrors.InvalidResponseFormatException
import spray.json.DeserializationException

import scala.concurrent.Future

trait EscherRestClient extends RestClient {
  def runStreamSigned(
      request: HttpRequest,
      serviceName: String,
      headers: List[String],
      retryConfig: RetryConfig = defaultRetryConfig
  ): Source[ByteString, NotUsed] = {
    Source
      .fromFuture(runSigned[ResponseEntity](request, serviceName, headers, retryConfig).map(_.dataBytes))
      .flatMapConcat(identity)
  }

  protected def runSigned[S](request: HttpRequest, serviceName: String, headers: List[String] = Nil, retryConfig: RetryConfig = defaultRetryConfig)(
      implicit um: Unmarshaller[ResponseEntity, S]
  ): Future[S] = {
    runRawSigned(request, serviceName, headers, retryConfig).flatMap { response =>
      consumeResponse[S](response).recoverWith {
        case err: DeserializationException =>
          consumeResponse[String](response).flatMap { body =>
            Future.failed(InvalidResponseFormatException(err.getMessage, body, err))
          }
      }
    }
  }

  protected def runRawSigned(
      request: HttpRequest,
      serviceName: String,
      headers: List[String],
      retryConfig: RetryConfig = defaultRetryConfig
  ): Future[HttpResponse] = {
    val headersToSign = headers.map(RawHeader(_, ""))

    for {
      signed   <- signRequestWithHeaders(headersToSign)(serviceName)(executor, materializer)(request)
      response <- runRaw(signed, retryConfig)
    } yield response
  }

}
