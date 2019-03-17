package com.shadowmute.ingest.configuration

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConverters._

class RuntimeConfiguration extends Configuration {
  private val source = ConfigFactory.load()

  override val mailDrop: MailDropConfiguration = new MailDropConfiguration {
    val mailDropConfig: Config = source.getConfig("mailDrop")

    override val dropPath: String = mailDropConfig.getString("dropPath")

    override val discardDirectory: String =
      mailDropConfig.getString("discardDirectory")
  }

  override val mailboxObservationInterval: Int =
    source.getInt("mailboxObserver.observationInterval")

  override val validRecipientDomains: Seq[String] = {
    source
      .getStringList("ingest.acceptedRecipientDomains")
      .asScala
      .map(_.toLowerCase)
  }
}
