package com.shadowmute.ingest

import java.time.Instant
import java.util.UUID

import play.api.libs.json._

case class MailMessage(
    recipient: String,
    body: Vector[String],
    reversePath: Option[String],
    sourceDomain: String,
    relayIP: String,
    key: UUID,
    expiration: Option[Instant] = None
) {
  def updateExpiration(other: Instant): MailMessage = {
    expiration.fold(
      this.copy(expiration = Option(other))
    ) { prev =>
      {
        if (prev.isBefore(other))
          this
        else
          this.copy(expiration = Option(other))
      }
    }
  }
}

object MailMessage {
  implicit val format: Format[MailMessage] = Json.format[MailMessage]
}
