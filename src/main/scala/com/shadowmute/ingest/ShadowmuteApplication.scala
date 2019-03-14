package com.shadowmute.ingest

import akka.actor.{ActorSystem, Props}
import com.shadowmute.ingest.configuration.RuntimeConfiguration
import com.shadowmute.ingest.mailbox.{MailboxRegistry, UpstreamMailboxObserver}

import scala.concurrent.duration._

object ShadowmuteApplication extends App {
  // Create the actor system
  val system: ActorSystem = ActorSystem("shadowmute-master")

  val configuration = new RuntimeConfiguration()
  val streamServer = new StreamTcpServer(system, configuration)

  val mailboxRegistry =
    system.actorOf(Props[MailboxRegistry], name = "MailboxRegistry")

  val mailboxObserver =
    system.actorOf(Props(classOf[UpstreamMailboxObserver], mailboxRegistry))

  system.scheduler.schedule(0.millis,
                            configuration.mailboxObservationInterval.seconds) {
    mailboxObserver ! 'refresh
  }(system.dispatcher)

}
