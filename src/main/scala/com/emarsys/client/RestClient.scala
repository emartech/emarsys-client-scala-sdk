package com.emarsys.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.emarsys.escher.akka.http.EscherDirectives

import scala.concurrent.{ExecutionContextExecutor, Future}

trait RestClient extends EscherDirectives {
  implicit val system: ActorSystem
  implicit val materializer: Materializer
  implicit val executor: ExecutionContextExecutor

  val connectionFlow: Flow[HttpRequest, HttpResponse, _]
  val serviceName: String
  lazy val maxRetryCount: Int = 0

  protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  def runRaw[S](request: HttpRequest, retry: Int = maxRetryCount)(implicit um: Unmarshaller[ResponseEntity, S]): Future[S] = {
    for {
      result <- runRawE[S](request, retry).map {
        case Left((status, responseBody)) => {
          system.log.error("Request to {} failed with status: {} / body: {}", request.uri, status, responseBody)
          throw RestClientException(s"Rest client request failed for ${request.uri}", status, responseBody)
        }
        case Right(response) => response
      }
    } yield result
  }

  def runRawE[S](request: HttpRequest, retry: Int = maxRetryCount)(implicit um: Unmarshaller[ResponseEntity, S]): Future[Either[(Int, String), S]] = {
    for {
      signed <- signRequest(serviceName)(executor, materializer)(request)
      response <- sendRequest(signed)
      result <- response.status match {
        case Success(_) => Unmarshal(response.entity).to[S].map(Right(_))
        case ServerError(_) if retry > 0 =>
          system.log.info("Retrying request: {} / {} attempt(s) left", request.uri, retry - 1)
          runRawE[S](request, retry - 1)
        case status => Unmarshal(response.entity).to[String].map { responseBody =>
          system.log.error("Request to {} failed with status: {} / body: {}", request.uri, status, responseBody)
          Left((status.intValue, responseBody))
        }
      }
    } yield result
  }


}
