package com.shadowmute.ingest

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}

import org.scalatest._

import scala.concurrent.duration._

class SmtpConnectionSpec
    extends TestKit(ActorSystem("SmtpConnectionSpec"))
    with ImplicitSender
    with WordSpecLike
    with MustMatchers {
  "SMTP Connection" must {
    "send a banner when in an initial state" in {
      val smtpConnection = system.actorOf(SmtpConnection.props)

      smtpConnection ! SmtpConnection.SendBanner

      receiveOne(10.seconds).toString must startWith("220 ")
    }

    "respond to a HELO request" in {
      val smtpConnection = system.actorOf(SmtpConnection.props)

      smtpConnection ! SmtpConnection.IncomingMessage("HELO test.actor")

      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "respond to a EHLO request" in {
      val smtpConnection = system.actorOf(SmtpConnection.props)

      smtpConnection ! SmtpConnection.IncomingMessage("EHLO test.actor")

      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "respond to a NOOP request" in {
      val smtpConnection = system.actorOf(SmtpConnection.props)

      smtpConnection ! SmtpConnection.IncomingMessage("NOOP test.actor")

      receiveOne(10.seconds).toString must startWith("250 ")
    }
  }
}
