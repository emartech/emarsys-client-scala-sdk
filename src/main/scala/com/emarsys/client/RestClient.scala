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

trait RestClient extends EscherDirectives {
  import RestClientErrors._

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
    def shouldRetry(error: Either[Throwable, HttpResponse]) = {
      error match {
        case Left(_: BufferOverflowException) => false
        case Left(_)                          => true
        case Right(response) =>
          response.status match {
            case ServerError(_) => true
            case _              => false
          }
      }
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
        case Left(value)  => Future.successful(Left(value))
        case Right(value) => transformer(value.entity).map(Right(_))
      }
    } yield result
  }

  private def sendRequestWithRetry[S](request: HttpRequest, maxRetries: Int)(
      shouldRetry: Either[Throwable, HttpResponse] => Boolean
  )(
      errorStatusMap: Throwable => (Int, String)
  ): Future[Either[(Int, String), HttpResponse]] = {
    def handleResponse(retriesLeft: Int)(response: HttpResponse): Future[Either[(Int, String), HttpResponse]] = {
      if (response.status.isSuccess()) Future.successful(Right(response))
      else handleFailedResponse(retriesLeft, response)
    }

    def handleFailedResponse(retriesLeft: Int, response: HttpResponse) = {
      consumeResponse[String](response).flatMap { responseBody =>
        if (retriesLeft > 0 && shouldRetry(Right(response))) doRetry(request, responseBody, retriesLeft - 1)
        else failRequest(response.status.intValue(), request, responseBody)
      }
    }

    def handleException(retriesLeft: Int): PartialFunction[Throwable, Future[Either[(Int, String), HttpResponse]]] = {
      case ex if retriesLeft > 0 && shouldRetry(Left(ex)) => doRetry(request, ex.getMessage, retriesLeft - 1)
      case ex =>
        val (status, cause) = errorStatusMap(ex)
        failRequest(status, request, cause)
    }

    def doRetry(request: HttpRequest, cause: String, retriesLeft: Int): Future[Either[(Int, String), HttpResponse]] = {
      logRetry(request, retriesLeft, cause)
      val n                     = maxRetries - retriesLeft
      val delay: FiniteDuration = initialDelay * (1 << n) // Math.pow(2, n)
      after(delay, system.scheduler)(loop(retriesLeft))
    }

    def loop(retriesLeft: Int): Future[Either[(Int, String), HttpResponse]] = {
      sendRequest(request)
        .flatMap(handleResponse(retriesLeft))
        .recoverWith(handleException(retriesLeft))
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

  implicit class RichUri(uri: Uri) {
    def +(pathSuffix: String): Uri = {
      val pathSuffixWithSlash = if (pathSuffix.startsWith("/")) {
        pathSuffix
      } else {
        "/" + pathSuffix
      }

      val path = uri.path

      uri.withPath(path + pathSuffixWithSlash)
    }
  }
}
