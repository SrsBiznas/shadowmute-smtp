package com.shadowmute.ingest

import play.api.libs.json._

case class MailMessage(
    recipient: String,
    body: Vector[String],
    reversePath: Option[String],
    sourceDomain: String,
    relayIP: String
)

object MailMessage {
  implicit val format = Json.format[MailMessage]
}
