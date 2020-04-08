package com.shadowmute.smtp.protocol

import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.UUID

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack}
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestActors.BlackholeActor
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.shadowmute.smtp.Logger
import com.shadowmute.smtp.configuration._
import com.shadowmute.smtp.mailbox.UnwrappedEchoActor
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

class SmtpConnectionSpec
    extends TestKit(ActorSystem("SmtpConnectionSpec"))
    with ImplicitSender
    with WordSpecLike
    with MustMatchers {
  "SMTP Connection" must {

    val blackholeRegistry = system.actorOf(Props[BlackholeActor])

    class ConnectedActor(configuration: Configuration)
        extends SmtpConnection(configuration, blackholeRegistry) {
      startWith(
        Connected,
        Connection(new InetSocketAddress("1.2.3.4", 25))
      )
    }

    class GreetedActor(configuration: Configuration)
        extends SmtpConnection(configuration, blackholeRegistry) {
      startWith(
        Greeted,
        InitialSession(new InetSocketAddress("1.2.3.4", 25), "test")
      )
    }

    class IncomingMessageActor(configuration: Configuration)
        extends SmtpConnection(configuration, blackholeRegistry) {
      startWith(
        IncomingMessage,
        MailSession(new InetSocketAddress("1.2.3.4", 25), "test", None, List())
      )
    }

    val basicConfiguration = new RuntimeConfiguration()

    "send a banner when in an initial state" in {
      val smtpConnection =
        system.actorOf(
          Props(new SmtpConnection(basicConfiguration, blackholeRegistry))
        )

      smtpConnection ! SmtpConnection.SendBanner(
        new InetSocketAddress("1.2.3.4", 25)
      )

      receiveOne(10.seconds).toString must startWith("220 ")
    }

    "respond to a HELO request" in {
      val smtpConnection =
        system.actorOf(Props(new ConnectedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("HELO test.actor")

      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "respond to a EHLO request" in {
      val smtpConnection =
        system.actorOf(Props(new ConnectedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("EHLO test.actor")

      val expected = receiveOne(10.seconds).toString

      val grouped = expected.split(SmtpHandler.NL)
      grouped.length mustBe 4

      grouped(0) must startWith("250-")
      grouped(1) must startWith("250-")
      grouped(2) must startWith("250-")
      grouped(3) must startWith("250 ")

      val trimmed = grouped.map(_.drop(4))
      val asSet = trimmed.toSet

      asSet must contain("SMTPUTF8")
      asSet must contain("8BITMIME")
    }

    "respond to a NOOP request" in {
      val smtpConnection =
        system.actorOf(Props(new ConnectedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("NOOP test.actor")

      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "respond to a VRFY request" in {
      val smtpConnection =
        system.actorOf(Props(new ConnectedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("VRFY test.actor")

      receiveOne(10.seconds).toString must startWith("252 ")
    }

    "respond to QUIT in the initial state" in {
      val smtpConnection =
        system.actorOf(Props(new ConnectedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("QUIT")

      receiveOne(10.seconds).toString must startWith("221 ")
    }

    "respond to QUIT in the greeted state" in {

      val smtpConnection =
        system.actorOf(Props(new GreetedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("QUIT")

      receiveOne(10.seconds).toString must startWith("221 ")
    }

    "respond to QUIT in the incoming message state" in {
      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("QUIT")

      receiveOne(10.seconds).toString must startWith("221 ")
    }

    "send an out of order response when mail is called twice" in {
      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage(
        "MAIL FROM:<duplicated@sender>"
      )

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "send an out of order response when mail is called during connected" in {
      val smtpConnection =
        system.actorOf(Props(new ConnectedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage(
        "MAIL FROM:<early@sender>"
      )

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Respond with OK when Mail command is received" in {
      val smtpConnection =
        system.actorOf(Props(new GreetedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("MAIL FROM:<new@sender>")

      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "Respond with OK when Rcpt command is received in MailSession state" in {
      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("RCPT TO:<new@recip>")

      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "Respond with Too many Recipients when 101st Rcpt command is received in MailSession state" in {
      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      for (i <- 1 to 100) {
        smtpConnection ! SmtpConnection.IncomingMessage(
          s"RCPT TO:<new@receipt.$i>"
        )
      }
      // Discard the 100 responses
      receiveN(100)

      smtpConnection ! SmtpConnection.IncomingMessage("RCPT TO:<new@receipt>")
      receiveOne(10.seconds).toString must startWith("452 ")
    }

    "Respond with command out of order Rcpt command is received in Connected state" in {
      val smtpConnection =
        system.actorOf(Props(new ConnectedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage(
        "RCPT TO:<test@testing.source>"
      )

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Respond with command out of order Rcpt command is received in Greeted state" in {
      val smtpConnection =
        system.actorOf(Props(new GreetedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage(
        "RCPT TO:<test@testing.source>"
      )

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Respond with command out of order when Data command is received without recipients" in {
      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("DATA")

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Open a data channel when the Data command is received" in {
      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage(
        s"RCPT TO:<new@receipt.data>"
      )

      receiveOne(10.seconds)

      smtpConnection ! SmtpConnection.IncomingMessage("DATA")
      receiveOne(10.seconds).toString must startWith("354 ")
    }

    "Open and close a data channel with the termination sequence" in {
      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage(
        s"RCPT TO:<new@receipt.data>"
      )

      receiveOne(10.seconds)

      smtpConnection ! SmtpConnection.IncomingMessage("DATA")
      receiveOne(10.seconds)

      smtpConnection ! SmtpConnection.IncomingMessage("data one")
      receiveOne(10.seconds) mustBe a[ReadNext]

      smtpConnection ! SmtpConnection.IncomingMessage("data two")
      receiveOne(10.seconds) mustBe a[ReadNext]

      smtpConnection ! SmtpConnection.IncomingMessage(".")
      val terminate = receiveOne(10.seconds)
      terminate.toString must startWith("250 ")

      smtpConnection ! SmtpConnection.IncomingMessage("HELO")
      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "Respond with command out of order Data command is received in Initial state" in {
      val smtpConnection =
        system.actorOf(
          Props(new SmtpConnection(basicConfiguration, blackholeRegistry))
        )
      smtpConnection ! SmtpConnection.IncomingMessage("Data")

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Respond with command out of order Data command is received in Greeted state" in {
      val smtpConnection =
        system.actorOf(Props(new GreetedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("Data")

      receiveOne(10.seconds).toString must startWith("503 ")
    }

    "Trim a leading decimal for data transparency" in {
      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage(
        s"RCPT TO:<new@receipt.data>"
      )

      receiveOne(10.seconds)

      smtpConnection ! SmtpConnection.IncomingMessage("DATA")
      receiveOne(10.seconds)

      smtpConnection ! SmtpConnection.IncomingMessage("data one")
      receiveOne(10.seconds) mustBe a[ReadNext]

      smtpConnection ! SmtpConnection.IncomingMessage("..leading decimal")
      receiveOne(10.seconds) mustBe a[ReadNext]

      smtpConnection ! SmtpConnection.IncomingMessage(".")
      val terminate = receiveOne(10.seconds)
      terminate.toString must startWith("250 ")

      smtpConnection ! SmtpConnection.IncomingMessage("HELO")
      receiveOne(10.seconds).toString must startWith("250 ")
    }

    "Ensure a Reset succeeds on an initial actor" in {
      val smtpConnection =
        system.actorOf(Props(new ConnectedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("RSET")
      receiveOne(10.seconds) mustBe a[Ok]

      smtpConnection ! SubscribeTransitionCallBack(testActor)
      val actorState =
        receiveOne(10.seconds).asInstanceOf[CurrentState[State]]

      actorState.state mustBe Connected
    }

    "Ensure a Reset succeeds on an greeted actor" in {

      val smtpConnection =
        system.actorOf(Props(new GreetedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("RSET")
      receiveOne(10.seconds) mustBe a[Ok]

      smtpConnection ! SubscribeTransitionCallBack(testActor)
      val actorState =
        receiveOne(10.seconds).asInstanceOf[CurrentState[State]]

      actorState.state mustBe Greeted
    }

    "Ensure a Reset succeeds on an incoming message actor" in {

      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("RSET")
      receiveOne(10.seconds) mustBe a[Ok]

      smtpConnection ! SubscribeTransitionCallBack(testActor)
      val actorState =
        receiveOne(10.seconds).asInstanceOf[CurrentState[State]]

      actorState.state mustBe Greeted
    }

    "Ensure a Helo resets on an incoming message actor" in {

      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("HELO source.domain")
      receiveOne(10.seconds) mustBe a[Ok]

      smtpConnection ! SubscribeTransitionCallBack(testActor)
      val actorState =
        receiveOne(10.seconds).asInstanceOf[CurrentState[State]]

      actorState.state mustBe Greeted
    }

    "Ensure a Ehlo resets on an incoming message actor" in {

      val smtpConnection =
        system.actorOf(Props(new IncomingMessageActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("EHLO source.domain")
      receiveOne(10.seconds) mustBe a[Ok]

      smtpConnection ! SubscribeTransitionCallBack(testActor)
      val actorState =
        receiveOne(10.seconds).asInstanceOf[CurrentState[State]]

      actorState.state mustBe Greeted
    }

    "ensure the file gets saved when an incoming message ends" in {
      val recipient = UUID.randomUUID().toString

      val uea = system.actorOf(Props(new UnwrappedEchoActor()))

      class FileDropActor(configuration: Configuration)
          extends SmtpConnection(configuration, uea) {
        startWith(
          DataChannel,
          DataChannel(
            new InetSocketAddress("1.2.3.4", 25),
            "test",
            None,
            List(s"$recipient@shadowmute.com"),
            Vector.empty
          )
        )
      }

      val dropPathTarget = Files.createTempDirectory(
        "smtst_390_"
      )
      dropPathTarget.toFile.deleteOnExit()

      class StaticConfiguration extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString

            override def discardDirectory: String = "discard"

            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }

        override def mailboxObservationInterval: Int = 1

        override def validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = new FilterConfiguration {
          override def personalProviders: Seq[String] = Nil
        }
      }
      val localConfig = new StaticConfiguration()

      val smtpConnection =
        system.actorOf(Props(new FileDropActor(localConfig)))

      val random = UUID.randomUUID()
      smtpConnection ! SmtpConnection.IncomingMessage(random.toString)

      implicit val timeout: Timeout = 5.seconds
      val f = smtpConnection ? SmtpConnection.IncomingMessage(".")
      Await.result(f, 1.second)

      // Wait for asynchronous file drops to complete
      Thread.sleep(500)

      // Ensure the UUID is in the new file
      import scala.jdk.CollectionConverters._

      val recipientTarget = dropPathTarget.resolve(recipient)
      recipientTarget.toFile.deleteOnExit()

      Logger().debug(
        s"Looking for recipient target: ${recipientTarget.toString}"
      )
      Files.exists(recipientTarget) mustBe true

      val recipientContents =
        Files.newDirectoryStream(recipientTarget).asScala.toList

      recipientContents.length mustBe 1

      val droppedFile = recipientTarget.resolve(recipientContents.head)
      val src =
        Source.fromFile(droppedFile.toString).getLines.mkString("")

      val randomFound = src.contains(random.toString)
      if (!randomFound) {
        // Debugging a heisentest
        Logger().debug(s"SRC: $src")
        Logger().debug(s"RND: ${random.toString}")
      }

      randomFound mustBe true

      droppedFile.toFile.deleteOnExit()
    }

    "Ensure a Start TLS resets an actor" in {

      // clear the receipt buffers
      receiveWhile(100.millis, 100.millis, 10) {
        case _ => ()
      }

      val smtpConnection =
        system.actorOf(Props(new GreetedActor(basicConfiguration)))

      smtpConnection ! SmtpConnection.IncomingMessage("STARTTLS")
      receiveOne(10.seconds) mustBe a[TLSReady]

      smtpConnection ! SubscribeTransitionCallBack(testActor)
      val actorState =
        receiveOne(10.seconds).asInstanceOf[CurrentState[State]]

      actorState.state mustBe Connected
    }
  }
}
