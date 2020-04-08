package com.shadowmute.smtp

object Logger {
  val l = com.typesafe.scalalogging.Logger("SM")

  def apply(): com.typesafe.scalalogging.Logger = l
}
