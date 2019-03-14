package com.shadowmute.ingest.configuration

import com.typesafe.config.ConfigFactory

class RuntimeConfiguration extends Configuration {
  private val source = ConfigFactory.load()

  override val mailDropPath: String = source.getString("mailDrop.path")

  override val mailboxObservationInterval: Int =
    source.getInt("mailboxObserver.observationInterval")

}
