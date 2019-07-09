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

    override def specialMailboxDirectory: String =
      mailDropConfig.getString("specialMailboxDirectory")

    override def specialMailboxes: Seq[String] = {
      mailDropConfig
        .getStringList("specialMailboxes")
        .asScala
        .map(_.toLowerCase)
    }
  }

  override val mailboxObservationInterval: Int =
    source.getInt("mailboxObserver.observationInterval")

  override val validRecipientDomains: Seq[String] = {
    source
      .getStringList("ingest.acceptedRecipientDomains")
      .asScala
      .map(_.toLowerCase)
  }

  override def tls: TlsConfiguration = new TlsConfiguration {
    val tlsConfig: Config = source.getConfig("tls")
    override def keystorePassphrase: String =
      tlsConfig.getString("keystorePassphrase")

    override def keystorePath: String = tlsConfig.getString("keystorePath")
  }

  override def filters: FilterConfiguration = new FilterConfiguration {
    val filterConfig: Config = source.getConfig("filters")
    override def personalProviders: Seq[String] =
      filterConfig
        .getStringList(
          "personalProviders"
        )
        .asScala
        .map(_.toLowerCase)
  }
}
