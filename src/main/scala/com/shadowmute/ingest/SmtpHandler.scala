package com.shadowmute.ingest

import akka.stream.scaladsl.Tcp.IncomingConnection

import akka.stream._
import akka.stream.scaladsl._

import akka.util.{ByteString, Timeout}

import akka.actor.{ActorSystem, Props}

import scala.concurrent.duration._

class SmtpHandler(system: ActorSystem) {

  implicit val sys = system

  val maxumumMessageSize = 1000

  def handle(c: IncomingConnection)(implicit m: Materializer) = {

    val welcomeMessage = Source.single(SmtpConnection.SendBanner)

    val connectionProcessor = system.actorOf(
      Props(new SmtpConnection())
    )

    import akka.pattern.ask

    implicit val timeout: Timeout = 5.seconds

    val handlerFlow = Flow[ByteString]
      .via(Framing.delimiter(ByteString(SmtpHandler.NL),
                             maximumFrameLength = maxumumMessageSize,
                             allowTruncation = true))
      .map(_.utf8String)

      // Portion of the handler where it drops into genuine SMTP verb decoding
      .map(SmtpConnection.IncomingMessage(_))

      // Trigger the banner
      .merge(welcomeMessage)
      .mapAsync(1)(m => connectionProcessor ? m)

      // Catch the connection close requests
      .takeWhile(_.isInstanceOf[ClosingConnection] == false, inclusive = true)

      // If ReadNext, don't bother replying
      .filter(_.isInstanceOf[ReadNext] == false)
      .map(_ + SmtpHandler.NL)
      .map(ByteString(_))

    c.handleWith(handlerFlow)
  }
}

object SmtpHandler {
  val NL = "\r\n"
}
