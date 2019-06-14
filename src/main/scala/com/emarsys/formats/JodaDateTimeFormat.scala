package com.emarsys.formats

import org.joda.time.{DateTime, DateTimeZone}
import fommil.sjs.FamilyFormats
import org.joda.time.format.DateTimeFormat
import spray.json.JsonFormat
import spray.json._

import scala.util.control.NonFatal

object JodaDateTimeFormat extends DefaultJsonProtocol with FamilyFormats {

  val dateTimePattern = "yyyy-MM-dd HH:mm:ss"

  implicit def jodaDateTimeFormat: JsonFormat[DateTime] = new JsonFormat[DateTime] {

    def write(obj: DateTime): JsValue = JsString(obj.toDateTime(DateTimeZone.UTC).toString(dateTimePattern))

    def read(json: JsValue): DateTime = json match {
      case JsString(time) =>
        try {
          DateTime.parse(time, DateTimeFormat.forPattern(dateTimePattern).withZone(DateTimeZone.UTC))
        } catch { case NonFatal(ex) => throw DeserializationException("Failed to deserialize datetime", ex) }
      case _ => throw DeserializationException("Date expected in string")
    }
  }

}
