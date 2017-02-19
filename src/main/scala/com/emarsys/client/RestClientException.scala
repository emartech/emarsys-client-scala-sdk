package com.emarsys.client

case class RestClientException(message: String, httpCode: Int, responseBody: String) extends Exception(message)
