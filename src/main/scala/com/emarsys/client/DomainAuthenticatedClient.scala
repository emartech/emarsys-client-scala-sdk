package com.emarsys.client
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import com.emarsys.escher.akka.http.config.EscherConfig
import com.typesafe.config.ConfigException

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContextExecutor, Future}

trait DomainAuthenticatedClient extends EscherRestClient {
  val retryConfig = defaultRetryConfig.copy(maxRetries = 3)

  def send[S](request: HttpRequest): Future[HttpResponse] = {
    resolveServiceName(request.uri) match {
      case Some(service) => runRawSigned(request, service, Nil, retryConfig)
      case None          => runRaw(request, retryConfig)
    }
  }

  private def resolveServiceName(uri: Uri): Option[String] =
    escherConfig.trustedServices find {
      getDomains(_) contains getAddress(uri)
    } map {
      _.getString("name")
    }

  private def getDomains(config: com.typesafe.config.Config): List[String] =
    try {
      config.getStringList("domains").asScala.toList
    } catch {
      case _: ConfigException => Nil
    }

  private def getAddress(uri: Uri): String =
    uri.authority.host.address()
}

object DomainAuthenticatedClient {
  def apply(eConfig: EscherConfig)(
      implicit
      sys: ActorSystem,
      ex: ExecutionContextExecutor
  ): DomainAuthenticatedClient = {
    new DomainAuthenticatedClient {
      implicit override val system: ActorSystem                = sys
      implicit override val executor: ExecutionContextExecutor = ex
      override val escherConfig: EscherConfig                  = eConfig
    }
  }
}
