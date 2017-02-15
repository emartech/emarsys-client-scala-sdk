package com.emarsys.formats

import org.joda.time.{DateTime, DateTimeZone}
import fommil.sjs.FamilyFormats
import spray.json.JsonFormat
import spray.json._

object JodaDateTimeFormat extends DefaultJsonProtocol with FamilyFormats {

  implicit def jodaDateTimeFormat = new JsonFormat[DateTime] {

    def write(obj: DateTime) = JsString(obj.toDateTime(DateTimeZone.UTC).toString())

    def read(json: JsValue) = json match {
      case JsString(time) => DateTime.parse(time)
      case _              => throw DeserializationException("Date expected in string")
    }
  }

}
