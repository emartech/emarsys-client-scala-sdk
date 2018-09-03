package com.emarsys.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.emarsys.escher.akka.http.EscherDirectives

import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json.DeserializationException

trait RestClient extends EscherDirectives {
  import RestClientErrors._

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

  def runStream(request: HttpRequest, retry: Int = maxRetryCount): Source[ByteString, NotUsed] = {
    runStreamWithHeader(request, Nil, retry)
  }

  def runRawWithHeader[S](request: HttpRequest, headers: List[String], retry: Int = maxRetryCount)(implicit um: Unmarshaller[ResponseEntity, S]): Future[S] = {
    runWithHeaders(request, headers, retry)(entity =>
      Unmarshal(entity).to[S].recoverWith {
        case err: DeserializationException =>
          Unmarshal(entity).to[String].flatMap { body =>
            Future.failed(InvalidResponseFormatException(err.getMessage, body, err))
          }
      }
    )(identity)
  }

  def runStreamWithHeader(request: HttpRequest,
                          headers: List[String],
                          retry: Int = maxRetryCount): Source[ByteString, NotUsed] = {
    runWithHeaders(request, headers, retry)(
      entity => Future.successful(entity.dataBytes.mapMaterializedValue(_ => NotUsed))
    )(
      futureStream => Source.fromFuture(futureStream).flatMapConcat(identity)
    )
  }

  def runWithHeaders[S, D](request: HttpRequest, headers: List[String], retry: Int = maxRetryCount)(transformer: ResponseEntity => Future[S])(responseTransformer: Future[S] => D): D = {
    responseTransformer(runE[S](request, headers, retry)(transformer).map(withHeaderErrorHandling[S](request)))
  }

  def runE[S](request: HttpRequest, headers: List[String], retry: Int = maxRetryCount)(transformer: ResponseEntity => Future[S]): Future[Either[(Int, String), S]] = {
    val headersToSign = headers.map(RawHeader(_, ""))
    for {
      signed <- signRequestWithHeaders(headersToSign)(serviceName)(executor, materializer)(request)
      response <- sendRequest(signed)
      result <- response.status match {
        case Success(_) => transformer(response.entity).map(Right(_))
        case ServerError(_) if retry > 0 => Unmarshal(response.entity).to[String].flatMap { _ =>
          system.log.info("Retrying request: {} / {} attempt(s) left", request.uri, retry - 1)
          runE[S](request, headers, retry - 1)(transformer)
        }
        case status => Unmarshal(response.entity).to[String].map { responseBody =>
          system.log.log(failLevel,"Request to {} failed with status: {} / body: {}", request.uri, status, responseBody)
          Left((status.intValue, responseBody))
        }
      }
    } yield result
  }

  private def withHeaderErrorHandling[S](request: HttpRequest): PartialFunction[Either[(Int, String), S], S] = {
    case Left((status, responseBody)) =>
      throw RestClientException(s"Rest client request failed for ${request.uri}", status, responseBody)
    case Right(response) => response
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
