package com.shadowmute.ingest

import akka.stream._
import akka.stream.scaladsl._

import akka.actor.ActorSystem
import scala.concurrent._

import scala.concurrent.Future

class StreamTcpServer(system: ActorSystem) {

  import Tcp._
  implicit val sys = system
  implicit val materializer = ActorMaterializer()

  val host = "127.0.0.1"
  val port = 1025

  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind(host, port)

  val smtpHandler = new SmtpHandler(system)

  connections runForeach { connection ⇒
    Logger().debug(s"[*] New connection from: ${connection.remoteAddress}")

    smtpHandler.handle(connection)
  }
}

object StreamTcpServer {}