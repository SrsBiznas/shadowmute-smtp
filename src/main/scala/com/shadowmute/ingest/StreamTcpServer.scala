package com.shadowmute.ingest

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl._
import com.shadowmute.ingest.configuration.Configuration
import com.shadowmute.ingest.smtp.SmtpHandler

import scala.concurrent.Future

class StreamTcpServer(
    system: ActorSystem,
    configuration: Configuration,
    mailboxRegistry: ActorRef,
    concreteTLS: ConcreteTLS
) {

  import Tcp._
  implicit val sys: ActorSystem = system
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val host = "0.0.0.0"
  val port = 2025

  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind(host, port)

  val smtpHandler =
    new SmtpHandler(system, configuration, mailboxRegistry, concreteTLS)

  connections runForeach { connection =>
    Logger().debug(s"[*] New connection from: ${connection.remoteAddress}")

    smtpHandler.handle(connection)
  }
}

object StreamTcpServer {}
