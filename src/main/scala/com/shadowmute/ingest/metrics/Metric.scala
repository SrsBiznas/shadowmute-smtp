package com.shadowmute.ingest.metrics

trait Metric {
  def name: String
}

case object ConnectionTerminatedGracefully extends Metric {
  val name: String = "connection_term_quit"
}


case object ConnectionInitialized extends Metric {
  val name = "connection_init"
}

case object MessageInitiated extends Metric {
  val name = "message_initiated"
}

case object MessageCompleted extends Metric {
  val name = "message_completed"
}

case object MessageRecipientRouted extends Metric {
  val name = "message_routed"
}

case object MessageDiscarded extends Metric {
  val name = "message_discarded"
}

case object MessageSize extends Metric {
  val name = "message_size"
}
