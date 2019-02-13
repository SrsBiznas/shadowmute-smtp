package com.shadowmute.ingest

import akka.actor.ActorSystem

object ShadowmuteApplication extends App {
  // Create the actor system
  val system: ActorSystem = ActorSystem("shadowmute-master")

  val streamServer = new StreamTcpServer(system)
}
