package com.shadowmute.ingest

import java.util.UUID

import play.api.libs.json._

case class MailMessage(
    recipient: String,
    body: Vector[String],
    reversePath: Option[String],
    sourceDomain: String,
    relayIP: String,
    key: UUID
)

object MailMessage {
  implicit val format = Json.format[MailMessage]
}
