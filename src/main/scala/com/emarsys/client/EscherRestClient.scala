package com.emarsys.client

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, ResponseEntity}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.emarsys.client.RestClientErrors.InvalidResponseFormatException
import spray.json.DeserializationException

import scala.concurrent.Future

trait EscherRestClient extends RestClient {
  def runStreamSigned(
      request: HttpRequest,
      serviceName: String,
      headers: List[String],
      maxRetries: Int
  ): Source[ByteString, NotUsed] = {
    Source
      .fromFuture(runSigned[ResponseEntity](request, serviceName, headers, maxRetries).map(_.dataBytes))
      .flatMapConcat(identity)
  }

  protected def runSigned[S](request: HttpRequest, serviceName: String, headers: List[String] = Nil, maxRetries: Int)(
      implicit um: Unmarshaller[ResponseEntity, S]
  ): Future[S] = {
    runRawSigned(request, serviceName, headers, maxRetries).flatMap { response =>
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
      maxRetries: Int
  ): Future[HttpResponse] = {
    val headersToSign = headers.map(RawHeader(_, ""))

    for {
      signed   <- signRequestWithHeaders(headersToSign)(serviceName)(executor, materializer)(request)
      response <- runRaw(signed, maxRetries)
    } yield response
  }

}
