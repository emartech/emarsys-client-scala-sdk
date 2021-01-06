package com.emarsys.client.suite

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.Uri
import com.emarsys.client.suite.ContactListApi.ContactList
import com.emarsys.formats.SuiteSdkFormats._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.emarsys.client.Config
import com.emarsys.escher.akka.http.config.EscherConfig

import scala.concurrent.{ExecutionContextExecutor, Future}

trait ContactListApi extends SuiteClient{

  def contactLists(customerId: Int,
                   retryConfig: Config.RetryConfig = defaultRetryConfig.copy(maxRetries = 0)): Future[List[ContactList]] = {

    val path    = "/contactlist"
    val request = RequestBuilding.Get(Uri(baseUrl(customerId) + path))

    runSuiteRequest[List[ContactList]](request, retryConfig).map(response => response.data)
  }
}

object ContactListApi {

  def apply(eConfig: EscherConfig)(implicit sys: ActorSystem, ex: ExecutionContextExecutor): ContactListApi = {
    new SuiteClient with ContactListApi {
      implicit override val system   = sys
      implicit override val executor = ex
      override val escherConfig      = eConfig
    }
  }

  final case class ContactList(id: String, name: String, created: String, `type`: Int)
}
