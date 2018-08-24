package com.emarsys.client

object RestClientErrors {

  case class RestClientException(message: String, httpCode: Int, responseBody: String) extends Exception(message)

  case class InvalidResponseFormatException(message: String, responseBody: String, cause: Throwable) extends Exception(message)

}
