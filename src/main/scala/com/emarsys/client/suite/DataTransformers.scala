package com.emarsys.client.suite

import com.emarsys.client.suite.SuiteClient.SuiteRawResponse

object DataTransformers {
  type GetDataResultPart = List[Map[String, Option[String]]]

  final case class GetDataRequest(keyId: String, keyValues: List[String], fields: Option[List[String]])
  final case class GetDataResponse(data: GetDataResult)

  final case class GetDataError(key: String, errorCode: Int, errorMsg: String)
  final case class GetDataResult(result: GetDataResultPart, errors: List[GetDataError])
  final case class GetDataRawResult(result: Either[Boolean, GetDataResultPart], errors: List[GetDataError])
  type GetDataRawResponseData = Either[String, GetDataRawResult]

  val getDataResultTransformer: (GetDataRawResult) => GetDataResult = {
    case GetDataRawResult(Right(r), e) => GetDataResult(r, e)
    case GetDataRawResult(Left(_), e)  => GetDataResult(Nil, e)
  }

  val getDataResponseTransformer: (SuiteRawResponse[GetDataRawResponseData]) => GetDataResponse = r => r.data match {
    case Right(d) => GetDataResponse(getDataResultTransformer(d))
    case Left(_)  => GetDataResponse(GetDataResult(Nil, Nil))
  }
}
