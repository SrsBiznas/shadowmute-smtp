package com.shadowmute.smtp

import akka.actor.{ActorSystem, Props}
import com.shadowmute.smtp.configuration.RuntimeConfiguration
import com.shadowmute.smtp.mailbox.{MailboxRegistry, UpstreamMailboxObserver}
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports

import scala.concurrent.duration._

object ShadowmuteApplication extends App {
  // Create the actor system
  val system: ActorSystem = ActorSystem("shadowmute-master")

  val configuration = new RuntimeConfiguration()

  val mailboxRegistry =
    system.actorOf(
      Props(classOf[MailboxRegistry], configuration.mailDrop),
      name = "MailboxRegistry"
    )

  val concreteTLS = new ConcreteTLS(configuration.tls)

  val streamServer = new StreamTcpServer(
    system,
    configuration,
    mailboxRegistry,
    concreteTLS
  )

  val mailboxObserver =
    system.actorOf(Props(classOf[UpstreamMailboxObserver], mailboxRegistry))

  system.scheduler
    .schedule(0.millis, configuration.mailboxObservationInterval.seconds) {
      mailboxObserver ! Symbol("refresh")
    }(system.dispatcher)

  DefaultExports.initialize()

  val server = new HTTPServer(9025)
}
