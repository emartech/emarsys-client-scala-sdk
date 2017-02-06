package com.emarsys.formats

import com.emarsys.client.suite.ContactFieldApi.FieldItem
import com.emarsys.client.suite.DataTransformers.GetDataRawResponseData
import com.emarsys.client.suite.SegmentApi.{BehaviorCriteria, ContactCriteria}
import com.emarsys.client.suite.SuiteClient.SuiteRawResponse
import fommil.sjs.FamilyFormats
import spray.json.JsonFormat
import spray.json._
import shapeless._

object SuiteSdkFormats extends DefaultJsonProtocol with FamilyFormats  {

  implicit override def eitherFormat[A, B](implicit a: JsonFormat[A], b: JsonFormat[B])  = super.eitherFormat[A, B]

  implicit val getDataRawResponseF : JsonFormat[SuiteRawResponse[GetDataRawResponseData]] = cachedImplicit
  implicit val contactCriteriaF    : JsonFormat[ContactCriteria]                          = cachedImplicit
  implicit val behaviorCriteriaF   : JsonFormat[BehaviorCriteria]                         = cachedImplicit

  override implicit def coproductHint[T: Typeable]: CoproductHint[T] = new FlatCoproductHint[T]("coproductType")

  implicit object FieldItemHint extends ProductHint[FieldItem] {
    override def nulls = AlwaysJsNullTolerateAbsent
  }
}