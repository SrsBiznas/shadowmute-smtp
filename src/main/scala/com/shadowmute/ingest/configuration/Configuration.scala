package com.shadowmute.ingest.configuration

trait Configuration {
  def mailDrop: MailDropConfiguration

  def validRecipientDomains: Seq[String]

  def mailboxObservationInterval: Int

  def tls: TlsConfiguration

  def filters: FilterConfiguration
}

trait MailDropConfiguration {
  def dropPath: String

  def discardDirectory: String

  def specialMailboxDirectory: String

  def specialMailboxes: Seq[String]
}

trait TlsConfiguration {
  def keystorePassphrase: String
  def keystorePath: String
}

trait FilterConfiguration {
  def personalProviders: Seq[String]
}
