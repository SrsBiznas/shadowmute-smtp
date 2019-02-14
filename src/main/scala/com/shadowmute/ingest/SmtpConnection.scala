package com.shadowmute.ingest

import akka.actor.{Actor, Props}

class SmtpConnection extends Actor {

  import context._
  def greeted: Receive = {
    case msg: SmtpConnection.IncomingMessage =>
      CommandParser.parse(msg) match {
        case Right(_: Helo) | Right(_: Ehlo) => {
          sender ! "NOPE!"
        }
        case Right(_: Noop) => {
          sender ! Ok("0x90")
        }
        case Right(_: Quit) => {
          sender ! ClosingConnection("TTFN")
        }
        case _ =>
          sender() ! CommandNotRecognized
      }
    case unknown => {
      Logger().debug(s"[-] SINKED [${unknown}]")
      sender() ! CommandNotRecognized
    }
  }

  def receive: Receive = {
    case SmtpConnection.SendBanner => {
      Logger().debug("[*] Init")
      sender() ! "220 shadowmute.com"
    }
    case msg: SmtpConnection.IncomingMessage =>
      CommandParser.parse(msg) match {
        case Right(_: Helo) => {
          become(greeted)
          sender ! Ok("shadowmute.com")
        }
        case Right(_: Ehlo) => {
          become(greeted)
          sender ! Ok("shadowmute.com")
        }
        case Right(_: Noop) => {
          sender ! Ok("0x90")
        }
        case Right(_: Quit) => {
          sender ! ClosingConnection("TTFN")
        }
        case _ =>
          sender() ! CommandNotRecognized()
      }
    case m => {
      Logger().debug(s"[-] SINKED [${m}]")
      sender() ! CommandNotRecognized()
    }
  }
}

object SmtpConnection {
  case object SendBanner

  case class IncomingMessage(message: String)

  val props = Props[SmtpConnection]
}
