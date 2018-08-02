package com.emarsys.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.emarsys.escher.akka.http.EscherDirectives

import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json.DeserializationException

trait RestClient extends EscherDirectives {
  import RestClient._

  implicit val system: ActorSystem
  implicit val materializer: Materializer
  implicit val executor: ExecutionContextExecutor

  val failLevel = if(Config.emsApi.restClient.errorOnFail) Logging.ErrorLevel else Logging.WarningLevel
  val connectionFlow: Flow[HttpRequest, HttpResponse, _]
  val serviceName: String
  lazy val maxRetryCount: Int = 0

  protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  def runRaw[S](request: HttpRequest, retry: Int = maxRetryCount)(implicit um: Unmarshaller[ResponseEntity, S]): Future[S] = {
    runRawWithHeader(request, Nil, retry)
  }

  def runRawWithHeader[S](request: HttpRequest, headers: List[String], retry: Int = maxRetryCount)(implicit um: Unmarshaller[ResponseEntity, S]): Future[S] = {
    for {
      result <- runRawE[S](request, headers, retry).map {
        case Left((status, responseBody)) =>
          system.log.error("Request to {} failed with status: {} / body: {}", request.uri, status, responseBody)
          throw RestClientException(s"Rest client request failed for ${request.uri}", status, responseBody)
        case Right(response) => response
      }
    } yield result
  }

  def runRawE[S](request: HttpRequest, headers: List[String], retry: Int = maxRetryCount)(implicit um: Unmarshaller[ResponseEntity, S]): Future[Either[(Int, String), S]] = {
    val headersToSign = headers.map(RawHeader(_, ""))
    for {
      signed <- signRequestWithHeaders(headersToSign)(serviceName)(executor, materializer)(request)
      response <- sendRequest(signed)
      result <- response.status match {
        case Success(_) => Unmarshal(response.entity).to[S].map(Right(_))
          .recoverWith {
            case err: DeserializationException =>
              Unmarshal(response.entity).to[String].flatMap { body =>
                Future.failed(InvalidResponseFormatException(err.getMessage, body, err))
              }
          }
        case ServerError(_) if retry > 0 => Unmarshal(response.entity).to[String].flatMap { _ =>
          system.log.info("Retrying request: {} / {} attempt(s) left", request.uri, retry - 1)
          runRawE[S](request, headers, retry - 1)
        }
        case status => Unmarshal(response.entity).to[String].map { responseBody =>
          system.log.log(failLevel,"Request to {} failed with status: {} / body: {}", request.uri, status, responseBody)
          Left((status.intValue, responseBody))
        }
      }
    } yield result
  }

  implicit class RichUri(uri: Uri) {
    def +(pathSuffix: String): Uri = {
      val pathSuffixWithSlash = if(pathSuffix.startsWith("/")) {
        pathSuffix
      } else {
        "/" + pathSuffix
      }

      val path = uri.path

      uri.withPath(path + pathSuffixWithSlash)
    }
  }
}

object RestClient {
  case class InvalidResponseFormatException(message: String, responseBody: String, cause: Throwable) extends Exception(message)
}