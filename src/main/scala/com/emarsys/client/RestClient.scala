package com.emarsys.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.pattern.after
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.{BufferOverflowException, Materializer, StreamTcpException}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.emarsys.escher.akka.http.EscherDirectives

import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json.DeserializationException

import scala.concurrent.duration._
import scala.util.control.NonFatal
import com.emarsys.client.RestClient.SuccessfulRequest
import com.emarsys.client.RestClient.FailureResponse
import com.emarsys.client.RestClient.RequestError

trait RestClient extends EscherDirectives {
  import RestClientErrors._
  import RestClient._

  implicit val system: ActorSystem
  implicit val materializer: Materializer
  implicit val executor: ExecutionContextExecutor

  val failLevel: Logging.LogLevel =
    if (Config.emsApi.restClient.errorOnFail) Logging.ErrorLevel else Logging.WarningLevel
  val connectionFlow: Flow[HttpRequest, HttpResponse, _]
  val serviceName: String
  lazy val maxRetryCount: Int      = 0
  val initialDelay: FiniteDuration = 200.millis

  protected def sendRequest(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  def runRaw[S](request: HttpRequest, retry: Int = maxRetryCount)(
      implicit um: Unmarshaller[ResponseEntity, S]
  ): Future[S] = {
    runRawWithHeader(request, Nil, retry)
  }

  def runStream(request: HttpRequest, retry: Int = maxRetryCount): Source[ByteString, NotUsed] = {
    runStreamWithHeader(request, Nil, retry)
  }

  def runRawWithHeader[S](request: HttpRequest, headers: List[String], retry: Int = maxRetryCount)(
      implicit um: Unmarshaller[ResponseEntity, S]
  ): Future[S] = {
    runWithHeaders(request, headers, retry)(
      entity =>
        Unmarshal(entity).to[S].recoverWith {
          case err: DeserializationException =>
            Unmarshal(entity).to[String].flatMap { body =>
              Future.failed(InvalidResponseFormatException(err.getMessage, body, err))
            }
        }
    )
  }

  def runStreamWithHeader(
      request: HttpRequest,
      headers: List[String],
      retry: Int = maxRetryCount
  ): Source[ByteString, NotUsed] = {
    Source
      .fromFuture(
        runWithHeaders(request, headers, retry)(
          entity => Future.successful(entity.dataBytes.mapMaterializedValue(_ => NotUsed))
        )
      )
      .flatMapConcat(identity)
  }

  def runWithHeaders[S, D](request: HttpRequest, headers: List[String], retry: Int = maxRetryCount)(
      transformer: ResponseEntity => Future[S]
  ): Future[S] = {
    runE[S](request, headers, retry)(transformer).map(withHeaderErrorHandling[S](request))
  }

  def runE[S](request: HttpRequest, headers: List[String], retry: Int = maxRetryCount)(
      transformer: ResponseEntity => Future[S]
  ): Future[Either[(Int, String), S]] =
    runEWithServiceName(Some(this.serviceName))(request, headers, retry)(transformer)

  def runEWithServiceName[S](serviceName: Option[String])(
      request: HttpRequest,
      headers: List[String],
      retry: Int = maxRetryCount
  )(transformer: ResponseEntity => Future[S]): Future[Either[(Int, String), S]] = {
    def shouldRetry(result: RequestResult) = result match {
      case SuccessfulRequest(_) => false
      case FailureResponse(response) =>
        response.status match {
          case ServerError(_) => true
          case _              => false
        }
      case RequestError(overflow: BufferOverflowException) => false
      case RequestError(error)                             => true
    }

    def errorStatusMap(error: Throwable) = error match {
      case _: BufferOverflowException => (429, error.getMessage)
      case _: StreamTcpException      => (504, error.getMessage)
      case otherException             => throw otherException
    }

    val headersToSign = headers.map(RawHeader(_, ""))

    for {
      signed   <- createRequest(serviceName, request, headersToSign)
      response <- sendRequestWithRetry(signed, retry)(shouldRetry)(errorStatusMap)
      result <- response match {
        case Right(value) => transformer(value.entity).map(Right(_))
        case Left(value)  => Future.successful(Left(value))
      }
    } yield result
  }

  private def sendRequestWithRetry[S](request: HttpRequest, maxRetries: Int)(
      shouldRetry: RequestResult => Boolean
  )(
      errorStatusMap: Throwable => (Int, String)
  ): Future[Either[(Int, String), HttpResponse]] = {
    def wrapResponse(response: HttpResponse): RequestResult = {
      if (response.status.isSuccess()) SuccessfulRequest(response)
      else FailureResponse(response)
    }

    def wrapException: PartialFunction[Throwable, RequestResult] = {
      case NonFatal(error) => RequestError(error)
    }

    def handleFailedResponse(retriesLeft: Int, fr: FailureResponse) = {
      val response = fr.response
      consumeResponse[String](response).flatMap { responseBody =>
        if (retriesLeft > 0 && shouldRetry(fr)) doRetry(responseBody, retriesLeft - 1)
        else failRequest(response.status.intValue(), request, responseBody)
      }
    }

    def handleException(retriesLeft: Int, re: RequestError) = {
      val error = re.error
      if (retriesLeft > 0 && shouldRetry(re)) {
        doRetry(error.getMessage(), retriesLeft - 1)
      } else {
        val (status, cause) = errorStatusMap(error)
        failRequest(status, request, cause)
      }
    }

    def getDelay(retriesLeft: Int): FiniteDuration = {
      val n = maxRetries - retriesLeft
      initialDelay * (1 << n) // Math.pow(2, n)... for ints...
    }

    def doRetry(cause: String, retriesLeft: Int): Future[Either[(Int, String), HttpResponse]] = {
      logRetry(request, retriesLeft, cause)
      val delay = getDelay(retriesLeft)
      after(delay, system.scheduler)(loop(retriesLeft))
    }

    def loop(retriesLeft: Int): Future[Either[(Int, String), HttpResponse]] = {
      val result = sendRequest(request)
        .map(wrapResponse)
        .recover(wrapException)

      result.flatMap {
        case SuccessfulRequest(response) => Future.successful(Right(response))
        case fr: FailureResponse         => handleFailedResponse(retriesLeft, fr)
        case re: RequestError            => handleException(retriesLeft, re)
      }
    }

    loop(maxRetries)
  }

  private def failRequest[S](status: Int, request: HttpRequest, cause: String) = {
    logFailure(status, request, cause)
    Future.successful(Left((status, cause)))
  }

  private def logRetry[A, S](request: HttpRequest, retriesLeft: Int, cause: String): Unit = {
    system.log.info("Retrying request: {} / {} attempt(s) left, cause: {}", request.uri, retriesLeft, cause)
  }

  private def logFailure[S](status: Int, request: HttpRequest, msg: String): Unit = {
    system.log.log(failLevel, "Request to {} failed with status: {} / body: {}", request.uri, status, msg)
  }

  private def consumeResponse[S](response: HttpResponse)(implicit um: Unmarshaller[ResponseEntity, S]): Future[S] = {
    Unmarshal(response.entity).to[S]
  }

  private def createRequest[S](serviceName: Option[String], request: HttpRequest, headersToSign: List[RawHeader]) = {
    serviceName.fold(Future.successful(request)) { serviceName =>
      signRequestWithHeaders(headersToSign)(serviceName)(executor, materializer)(request)
    }
  }

  private def withHeaderErrorHandling[S](request: HttpRequest): PartialFunction[Either[(Int, String), S], S] = {
    case Left((status, responseBody)) =>
      throw RestClientException(s"Rest client request failed for ${request.uri}", status, responseBody)
    case Right(response) => response
  }
}

object RestClient {
  sealed private[RestClient] trait RequestResult
  final private[RestClient] case class SuccessfulRequest(response: HttpResponse) extends RequestResult
  final private[RestClient] case class FailureResponse(response: HttpResponse)   extends RequestResult
  final private[RestClient] case class RequestError(error: Throwable)            extends RequestResult

}
