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
      startWith(IncomingMessage, MailSession("test", None, List()))
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

    "Respond with OK when Rcpt command is received in MailSession state" in {
      val smtpConnection =
        system.actorOf(Props(classOf[IncomingMessageActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage("RCPT TO:<new@recip>")

      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "Respond with Too many Recipients when 101st Rcpt command is received in MailSession state" in {
      val smtpConnection =
        system.actorOf(Props(classOf[IncomingMessageActor], this))

      for (i <- 1 to 100) {
        (smtpConnection ! SmtpConnection.IncomingMessage(
          s"RCPT TO:<new@receipt.${i}>"))
      }
      // Discard the 100 responses
      receiveN(100)

      smtpConnection ! SmtpConnection.IncomingMessage("RCPT TO:<new@receipt>")
      receiveOne(10.seconds).toString must startWith("452 ")
    }

    "Respond with command out of order Rcpt command is received in Initial state" in {
      val smtpConnection = system.actorOf(SmtpConnection.props)
      smtpConnection ! SmtpConnection.IncomingMessage(
        "RCPT TO:<test@testing.source>")

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Respond with command out of order Rcpt command is received in Greeted state" in {
      val smtpConnection = system.actorOf(Props(classOf[GreetedActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage(
        "RCPT TO:<test@testing.source>")

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Open a data channel when the Data command is received" in {
      val smtpConnection =
        system.actorOf(Props(classOf[IncomingMessageActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage(
        s"RCPT TO:<new@receipt.data>")

      receiveOne(10.seconds)

      smtpConnection ! SmtpConnection.IncomingMessage("DATA")
      receiveOne(10.seconds).toString must startWith("354 ")
    }

    "Open and close a data channel with the termination sequence" in {
      val smtpConnection =
        system.actorOf(Props(classOf[IncomingMessageActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage(
        s"RCPT TO:<new@receipt.data>")

      receiveOne(10.seconds)

      smtpConnection ! SmtpConnection.IncomingMessage("DATA")
      val dataOpen = receiveOne(10.seconds)
      Logger().debug(s"DO[174]: ${dataOpen}")

      smtpConnection ! SmtpConnection.IncomingMessage("data one")
      receiveOne(10.seconds) mustBe a[ReadNext]

      smtpConnection ! SmtpConnection.IncomingMessage("data two")
      receiveOne(10.seconds) mustBe a[ReadNext]

      smtpConnection ! SmtpConnection.IncomingMessage(".")
      val terminate = receiveOne(10.seconds)
      Logger().debug(s"DO[182]: ${terminate}")
      terminate.toString must startWith("250 ")

      smtpConnection ! SmtpConnection.IncomingMessage("HELO")
      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "Respond with command out of order Data command is received in Initial state" in {
      val smtpConnection = system.actorOf(SmtpConnection.props)
      smtpConnection ! SmtpConnection.IncomingMessage("Data")

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Respond with command out of order Data command is received in Greeted state" in {
      val smtpConnection = system.actorOf(Props(classOf[GreetedActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage("Data")

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Trim a leading decimal for data transparency" in {
      val smtpConnection =
        system.actorOf(Props(classOf[IncomingMessageActor], this))

      smtpConnection ! SmtpConnection.IncomingMessage(
        s"RCPT TO:<new@receipt.data>")

      receiveOne(10.seconds)

      smtpConnection ! SmtpConnection.IncomingMessage("DATA")
      val dataOpen = receiveOne(10.seconds)
      Logger().debug(s"DO[174]: ${dataOpen}")

      smtpConnection ! SmtpConnection.IncomingMessage("data one")
      receiveOne(10.seconds) mustBe a[ReadNext]

      smtpConnection ! SmtpConnection.IncomingMessage("..leading decimal")
      receiveOne(10.seconds) mustBe a[ReadNext]

      smtpConnection ! SmtpConnection.IncomingMessage(".")
      val terminate = receiveOne(10.seconds)
      Logger().debug(s"DO[182]: ${terminate}")
      terminate.toString must startWith("250 ")

      smtpConnection ! SmtpConnection.IncomingMessage("HELO")
      receiveOne(10.seconds).toString must startWith("250 ")
    }
  }
}
