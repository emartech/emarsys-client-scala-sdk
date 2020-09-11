package com.emarsys.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.pattern.after
import akka.stream.scaladsl.{Source, TcpIdleTimeoutException}
import akka.stream.{BufferOverflowException, Materializer, StreamTcpException}
import akka.util.ByteString
import com.emarsys.client.Config.RetryConfig
import spray.json.DeserializationException

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.control.NonFatal

trait RestClient {
  import RestClient._
  import RestClientErrors._

  implicit val system: ActorSystem
  lazy val materializer: Materializer = implicitly
  implicit val executor: ExecutionContextExecutor

  val failLevel: Logging.LogLevel =
    if (Config.emsApi.restClient.errorOnFail) Logging.ErrorLevel else Logging.WarningLevel
  val defaultRetryConfig: RetryConfig = Config.emsApi.retry

  protected def sendRequest(request: HttpRequest): Future[HttpResponse] = Http().singleRequest(request)

  protected def runStreamed(
      request: HttpRequest,
      retryConfig: RetryConfig = defaultRetryConfig
  ): Source[ByteString, NotUsed] = {
    Source
      .future(runRaw(request, retryConfig).map(_.entity.dataBytes))
      .flatMapConcat(identity)
  }

  protected def run[S](request: HttpRequest, retryConfig: RetryConfig = defaultRetryConfig)(
      implicit um: Unmarshaller[ResponseEntity, S]
  ): Future[S] = {
    runRaw(request, retryConfig).flatMap { response =>
      Unmarshal(response.entity).to[S].recoverWith {
        case err: DeserializationException =>
          Unmarshal(response.entity).to[String].flatMap { body =>
            Future.failed(InvalidResponseFormatException(err.getMessage, body, err))
          }
      }
    }
  }

  protected def runRaw(request: HttpRequest, retryConfig: RetryConfig = defaultRetryConfig): Future[HttpResponse] = {
    internalRun(request, retryConfig).map(withHeaderErrorHandling(request))
  }

  private def internalRun(
      request: HttpRequest,
      retryConfig: RetryConfig
  ): Future[Either[InternalClientError, HttpResponse]] = {
    def shouldRetry(result: RequestResult) = result match {
      case SuccessfulRequest(_) => false
      case FailureResponse(response) =>
        response.status match {
          case ServerError(_) => true
          case _              => false
        }
      case RequestException(_: BufferOverflowException) => false
      case RequestException(_)                          => true
    }

    def errorStatusMap(error: Throwable) = error match {
      case _: BufferOverflowException                         => (429, error.getMessage)
      case _: StreamTcpException | _: TcpIdleTimeoutException => (504, error.getMessage)
      case otherException                                     => throw otherException
    }

    for {
      response <- sendRequestWithRetry(request, retryConfig)(shouldRetry)(errorStatusMap)
    } yield response
  }

  private def sendRequestWithRetry[S](request: HttpRequest, retryConfig: RetryConfig)(
      shouldRetry: RequestResult => Boolean
  )(
      errorStatusMap: Throwable => (Int, String)
  ): Future[Either[InternalClientError, HttpResponse]] = {
    val start = System.nanoTime()

    def isRetriable(retriesLeft: Int, requestResult: RequestResult) = {
      val elapsed = (System.nanoTime() - start).nanos
      retriesLeft > 0 && elapsed < retryConfig.dontRetryAfter && shouldRetry(requestResult)
    }

    def wrapResponse(response: HttpResponse): RequestResult = {
      if (response.status.isSuccess()) SuccessfulRequest(response)
      else FailureResponse(response)
    }

    def wrapException: PartialFunction[Throwable, RequestResult] = {
      case NonFatal(exception) => RequestException(exception)
    }

    def handleFailedResponse(retriesLeft: Int, failureResponse: FailureResponse) = {
      val response = failureResponse.response
      Unmarshal(response.entity).to[String].flatMap { responseBody =>
        if (isRetriable(retriesLeft, failureResponse)) doRetry(responseBody, retriesLeft - 1)
        else failRequest(response.status.intValue(), request, responseBody)
      }
    }

    def handleException(retriesLeft: Int, requestError: RequestException) = {
      val error = requestError.exception
      if (isRetriable(retriesLeft, requestError)) {
        doRetry(error.getMessage, retriesLeft - 1)
      } else {
        val (status, cause) = errorStatusMap(error)
        failRequest(status, request, cause)
      }
    }

    def getDelay(retriesLeft: Int): FiniteDuration = {
      val n = retryConfig.maxRetries - retriesLeft
      retryConfig.initialRetryDelay * (1 << (n - 1)) // Math.pow(2, n)... for ints...
    }

    def doRetry(cause: String, retriesLeft: Int): Future[Either[InternalClientError, HttpResponse]] = {
      logRetry(request, retriesLeft, cause)
      val delay = getDelay(retriesLeft)
      after(delay, system.scheduler)(loop(retriesLeft))
    }

    def loop(retriesLeft: Int): Future[Either[InternalClientError, HttpResponse]] = {
      val result = sendRequest(request)
        .map(wrapResponse)
        .recover(wrapException)

      result.flatMap {
        case SuccessfulRequest(response) => Future.successful(Right(response))
        case fr: FailureResponse         => handleFailedResponse(retriesLeft, fr)
        case re: RequestException        => handleException(retriesLeft, re)
      }
    }

    loop(retryConfig.maxRetries)
  }

  private def failRequest[S](status: Int, request: HttpRequest, cause: String) = {
    logFailure(status, request, cause)
    Future.successful(Left(InternalClientError(status, cause)))
  }

  private def logFailure[S](status: Int, request: HttpRequest, msg: String): Unit = {
    system.log.log(failLevel, "Request to {} failed with status: {} / body: {}", request.uri, status, msg)
  }

  private def logRetry[A, S](request: HttpRequest, retriesLeft: Int, cause: String): Unit = {
    system.log.info("Retrying request: {} / {} attempt(s) left, cause: {}", request.uri, retriesLeft, cause)
  }

  private def withHeaderErrorHandling[S](request: HttpRequest): PartialFunction[Either[InternalClientError, S], S] = {
    case Left(InternalClientError(status, responseBody)) =>
      throw RestClientException(s"Rest client request failed for ${request.uri}", status, responseBody)
    case Right(response) => response
  }
}

object RestClient {
  sealed private[RestClient] trait RequestResult
  final private[RestClient] case class SuccessfulRequest(response: HttpResponse) extends RequestResult
  final private[RestClient] case class FailureResponse(response: HttpResponse)   extends RequestResult
  final private[RestClient] case class RequestException(exception: Throwable)    extends RequestResult
  final private[RestClient] case class InternalClientError(status: Int, cause: String)
}
