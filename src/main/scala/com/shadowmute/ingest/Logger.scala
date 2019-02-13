package com.shadowmute.ingest

object Logger {
  val l = com.typesafe.scalalogging.Logger("SM")

  def apply() = l
}
