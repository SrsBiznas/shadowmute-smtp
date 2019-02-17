package com.shadowmute.ingest

import akka.actor.{ActorSystem, Props}
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

    "respond to a VRFY request" in {
      val smtpConnection = system.actorOf(SmtpConnection.props)

      smtpConnection ! SmtpConnection.IncomingMessage("VRFY test.actor")

      receiveOne(10.seconds).toString must startWith("252 ")
    }

    "respond to QUIT in the initial state" in {
      val smtpConnection = system.actorOf(SmtpConnection.props)

      smtpConnection ! SmtpConnection.IncomingMessage("QUIT")

      receiveOne(10.seconds).toString must startWith("221 ")
    }

    class GreetedActor extends SmtpConnection {
      startWith(Greeted, InitialSession("test"))
    }

    "respond to QUIT in the greeted state" in {

      val smtpConnection = system.actorOf(Props(classOf[GreetedActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage("QUIT")

      receiveOne(10.seconds).toString must startWith("221 ")
    }

    class IncomingMessageActor extends SmtpConnection {
      startWith(IncomingMessage, MailSession("test", None, Nil))
    }

    "respond to QUIT in the incoming message state" in {
      val smtpConnection =
        system.actorOf(Props(classOf[IncomingMessageActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage("QUIT")

      receiveOne(10.seconds).toString must startWith("221 ")
    }

    "send an out of order response when mail is called twice" in {
      val smtpConnection =
        system.actorOf(Props(classOf[IncomingMessageActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage(
        "MAIL FROM:<duplicated@sender>")

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Respond with OK when Mail command is received" in {
      val smtpConnection =
        system.actorOf(Props(classOf[GreetedActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage("MAIL FROM:<new@sender>")

      receiveOne(10.seconds).toString must startWith("250 ")
    }
  }
}
