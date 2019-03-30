package com.shadowmute.ingest.metrics

import com.timgroup.statsd.NonBlockingStatsDClient

object StatsD {
  val statsD =
  new NonBlockingStatsDClient(
    "ingest",
    // FEATURE: #47
    // Pull this from the configuration
    "172.17.0.1",
    8125
  )
}
