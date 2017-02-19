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
  implicit val system:       ActorSystem
  implicit val materializer: Materializer
  implicit val executor:     ExecutionContextExecutor

  val connectionFlow: Flow[HttpRequest, HttpResponse, _]
  val serviceName: String

  protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  def runRaw[S](request: HttpRequest)(implicit um: Unmarshaller[ResponseEntity, S]): Future[S] = {
    for {
    signed   <- signRequest(serviceName)(executor, materializer)(request)
    response <- sendRequest(signed)
    result   <- response.status match {
            case Success(_) => Unmarshal(response.entity).to[S]
            case status     => Unmarshal(response.entity).to[String].map { responseBody =>
              system.log.error("Request to {} failed with status: {} / body: {}", request.uri, status, responseBody)
              throw RestClientException(s"Rest client request failed for ${request.uri}", status.intValue, responseBody)
            }
          }
    } yield result
  }
}
