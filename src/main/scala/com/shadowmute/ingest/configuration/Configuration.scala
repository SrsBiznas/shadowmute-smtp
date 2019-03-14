package com.shadowmute.ingest.configuration

trait Configuration {
  def mailDropPath: String

  def mailboxObservationInterval: Int
}
