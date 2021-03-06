package com.shadowmute.smtp.metrics

import io.prometheus.client.{Counter, Summary}

class MetricCollector {}

object MetricCollector {
  val ConnectionInitialized: Counter = Counter
    .build()
    .name("smtp_connection_init")
    .help("Total SMTP connections.")
    //    .labelNames("lbl")
    .register()

  val MessageInitiated: Counter = Counter
    .build()
    .name("smtp_message_initiated")
    .help("Total messages initiated.")
    .register()

  val MessageCompleted: Counter = Counter
    .build()
    .name("smtp_message_completed")
    .help("Total messages completed.")
    .register()

  val MessageRecipientRouted: Counter = Counter
    .build()
    .name("smtp_message_routed")
    .help("Total messages routed to users.")
    .register()

  val SpecialMailboxRouted: Counter = Counter
    .build()
    .name("smtp__special_message_routed")
    .help("Total special messages (eg. support@ or abuse@) routed.")
    .register()

  val MessageDiscarded: Counter = Counter
    .build()
    .name("smtp_message_discarded")
    .help("Total messages discarded.")
    .register()

  val MessageSize: Summary = Summary
    .build()
    .name("smtp_message_size")
    .help("Received message size.")
    .register()

  val ConnectionTerminatedGracefully: Counter = Counter
    .build()
    .name("smtp_connection_term_quit")
    .help("Total connections terminated gracefully.")
    .register()

  val StartTLSInitiated: Counter = Counter
    .build()
    .name("starttls_init")
    .help("StartTLS initiated")
    .register()
}
