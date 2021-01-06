package com.emarsys.formats

import com.emarsys.client.suite.SuiteClient.SuiteRawResponse
import com.emarsys.client.suite.DataTransformers._
import com.emarsys.client.suite.ContactFieldApi._
import com.emarsys.client.suite.ContactListApi.ContactList
import com.emarsys.client.suite.SegmentApi._
import com.emarsys.client.suite.SegmentRunApi._
import spray.json._

object SuiteSdkFormats extends DefaultJsonProtocol {
  // DataTransformers
  implicit val getDataRequestF: RootJsonFormat[GetDataRequest] = jsonFormat3(GetDataRequest.apply)
  implicit val getDataErrorF: JsonFormat[GetDataError]         = jsonFormat3(GetDataError.apply)
  implicit val getDataRawResultF: JsonFormat[GetDataRawResult] = jsonFormat2(GetDataRawResult.apply)
  implicit def suiteRawResponseF[T: JsonFormat]: RootJsonFormat[SuiteRawResponse[T]] =
    jsonFormat3(SuiteRawResponse.apply)

  // ContactFieldApi
  implicit val fieldItemF: JsonFormat[FieldItem]                         = jsonFormat4(FieldItem.apply)
  implicit val createFieldRequestF: RootJsonFormat[CreateFieldRequest]   = jsonFormat3(CreateFieldRequest.apply)
  implicit val createFieldResponseF: RootJsonFormat[CreateFieldResponse] = jsonFormat1(CreateFieldResponse.apply)

  // SegmentApi
  implicit val contactCriteriaLeafF: JsonFormat[ContactCriteriaLeaf] = jsonFormat4(ContactCriteriaLeaf.apply)
  implicit val contactCriteriaBranchF: JsonFormat[ContactCriteriaBranch] = lazyFormat(
    jsonFormat2(ContactCriteriaBranch.apply)
  )
  implicit val contactCriteriaF: JsonFormat[ContactCriteria] = new JsonFormat[ContactCriteria] {
    def write(obj: ContactCriteria): JsValue = obj match {
      case leaf: ContactCriteriaLeaf     => leaf.toJson
      case branch: ContactCriteriaBranch => branch.toJson
    }

    def read(json: JsValue): ContactCriteria =
      (json.convertTo(safeReader[ContactCriteriaLeaf]), json.convertTo(safeReader[ContactCriteriaBranch])) match {
        case (Right(leaf), _)   => leaf
        case (_, Right(branch)) => branch
        case (Left(el), Left(eb)) =>
          deserializationError(s"Could not read ContactCriteria\nLeaf error: $el\nBranch error: $eb")
      }
  }

  implicit val behaviorCriteriaLeafF: JsonFormat[BehaviorCriteriaLeaf] = jsonFormat12(BehaviorCriteriaLeaf.apply)
  implicit val behaviorCriteriaBranchF: JsonFormat[BehaviorCriteriaBranch] = lazyFormat(
    jsonFormat2(BehaviorCriteriaBranch.apply)
  )
  implicit val behaviorCriteriaF: JsonFormat[BehaviorCriteria] = new JsonFormat[BehaviorCriteria] {
    def write(obj: BehaviorCriteria): JsValue = obj match {
      case leaf: BehaviorCriteriaLeaf     => leaf.toJson
      case branch: BehaviorCriteriaBranch => branch.toJson
    }

    def read(json: JsValue): BehaviorCriteria =
      (json.convertTo(safeReader[BehaviorCriteriaLeaf]), json.convertTo(safeReader[BehaviorCriteriaBranch])) match {
        case (Right(leaf), _)   => leaf
        case (_, Right(branch)) => branch
        case (Left(el), Left(eb)) =>
          deserializationError(s"Could not read BehaviorCriteria\nLeaf error: $el\nBranch error: $eb")
      }
  }
  implicit val createRequestF: RootJsonFormat[CreateRequest]             = jsonFormat5(CreateRequest.apply)
  implicit val createRawResponseDataF: JsonFormat[CreateRawResponseData] = jsonFormat1(CreateRawResponseData.apply)

  // SegmentRunApi
  implicit val contactListDetailsRawF: JsonFormat[ContactListDetailsRaw] = jsonFormat4(ContactListDetailsRaw.apply)
  implicit val segmentRunResultRawF: JsonFormat[SegmentRunResultRaw]     = jsonFormat3(SegmentRunResultRaw.apply)

  // ContactListApi
  implicit val contactListF: JsonFormat[ContactList] = jsonFormat4(ContactList.apply)
}
