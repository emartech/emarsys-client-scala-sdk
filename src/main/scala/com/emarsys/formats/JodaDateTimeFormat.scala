package com.emarsys.formats

import org.joda.time.{DateTime, DateTimeZone}
import fommil.sjs.FamilyFormats
import org.joda.time.format.DateTimeFormat
import spray.json.JsonFormat
import spray.json._

object JodaDateTimeFormat extends DefaultJsonProtocol with FamilyFormats {

  val dateTimePattern = "yyyy-MM-dd HH:mm:ss"

  implicit def jodaDateTimeFormat = new JsonFormat[DateTime] {

    def write(obj: DateTime) = JsString(obj.toDateTime(DateTimeZone.UTC).toString(dateTimePattern))

    def read(json: JsValue) = json match {
      case JsString(time) => DateTime.parse(time, DateTimeFormat.forPattern(dateTimePattern).withZone(DateTimeZone.UTC))
      case _              => throw DeserializationException("Date expected in string")
    }
  }

}
