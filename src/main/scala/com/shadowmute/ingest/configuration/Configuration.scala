package com.shadowmute.ingest.configuration

trait Configuration {
  def mailDrop: MailDropConfiguration

  def validRecipientDomains: Seq[String]

  def mailboxObservationInterval: Int
}

trait MailDropConfiguration {
  def dropPath: String

  def discardDirectory: String
}
