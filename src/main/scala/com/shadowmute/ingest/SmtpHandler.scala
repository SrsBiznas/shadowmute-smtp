package com.shadowmute.ingest

import akka.stream.scaladsl.Tcp.IncomingConnection

import akka.stream._
import akka.stream.scaladsl._

import akka.util.ByteString

class SmtpHandler {}

object SmtpHandler {

  val banner = "220 shadowmute.com"
  val maxumumMessageSize = 256

  def handle(c: IncomingConnection)(implicit m: Materializer) = {

    val welcomeMessage = Source.single(banner)

    // SMTP exit signal (assuming the \r\n has been trimmed)
    val terminationCommand = Flow[String].takeWhile(_ != ".")

    val handlerFlow = Flow[ByteString]
      .via(
        Framing.delimiter(ByteString("\r\n"),
                          maximumFrameLength = maxumumMessageSize,
                          allowTruncation = true))
      .map(_.utf8String)
      .via(terminationCommand)
      .merge(welcomeMessage)
      // Portion of the handler where it drops into genuine SMTP verb decoding
      .map(_ + "!!!\r\n")
      .map(ByteString(_))

    c.handleWith(handlerFlow)
  }
}
