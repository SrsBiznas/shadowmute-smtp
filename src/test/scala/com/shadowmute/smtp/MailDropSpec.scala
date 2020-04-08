package com.shadowmute.smtp

import java.io.File
import java.net.InetSocketAddress
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.{Comparator, UUID}

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestActors.BlackholeActor
import akka.testkit.TestKit
import com.shadowmute.smtp.configuration.{
  Configuration,
  FilterConfiguration,
  MailDropConfiguration,
  TlsConfiguration
}
import com.shadowmute.smtp.mailbox.{
  AlwaysNoneActor,
  RecipientQuery,
  UnwrappedEchoActor
}
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

import scala.jdk.CollectionConverters._

class MailDropSpec
    extends TestKit(ActorSystem("MailDropSpec"))
    with WordSpecLike
    with MustMatchers {

  "Mail Drop" must {

    "write a message to the user folder" in {

      val uuid = UUID.randomUUID()

      val relayIP = new InetSocketAddress("21.22.23.24", 25)

      val newMessage = MailMessage(
        recipient = s"$uuid@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString,
        key = UUID.randomUUID(),
        expiration = None
      )

      val dropPathTarget = Files.createTempDirectory(
        "smtst_32_"
      )
      dropPathTarget.toFile.deleteOnExit()

      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString

            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }

        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val staticConfig = new StaticConfig()

      val dropper =
        new MailDrop(
          staticConfig,
          system.actorOf(Props(new UnwrappedEchoActor()))
        )

      Await.ready(
        dropper.dropMessage(newMessage),
        300.millis
      )

      val recipientTarget = dropPathTarget.resolve(uuid.toString)

      recipientTarget.toFile.deleteOnExit()

      Files.exists(recipientTarget) mustBe true

      val recipientContents =
        Files.newDirectoryStream(recipientTarget).asScala.toList

      recipientContents.length mustBe 1

      val droppedFile = recipientTarget.resolve(recipientContents.head)
      val src =
        Source.fromFile(droppedFile.toString).getLines.mkString("")

      src.contains(uuid.toString) mustBe true

      droppedFile.toFile.deleteOnExit()
    }

    "Ensure a message to the user folder can't traverse paths to non-existing dir" in {
      val uuid = UUID.randomUUID()

      val relayIP = new InetSocketAddress("69.70.71.72", 25)

      val newMessage = MailMessage(
        recipient =
          s"$uuid${File.separator}..${File.separator}canary@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString,
        UUID.randomUUID()
      )

      val dropPathTarget = Files.createTempDirectory(
        "smtst_108_"
      )
      dropPathTarget.toFile.deleteOnExit()

      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString

            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }
        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val staticConfig = new StaticConfig()

      val dropper =
        new MailDrop(staticConfig, system.actorOf(Props[BlackholeActor]))

      Await.ready(
        dropper.dropMessage(newMessage),
        200.millis
      )

      val recipientTarget = dropPathTarget.resolve("canary")
      recipientTarget.toFile.deleteOnExit()

      Files.exists(recipientTarget) mustBe false
    }

    "Ensure a message to the user folder can't traverse paths to existing dir" in {
      val uuid = UUID.randomUUID()

      val relayIP = new InetSocketAddress("69.70.71.72", 25)

      val newMessage = MailMessage(
        recipient =
          s"$uuid${File.separator}..${File.separator}canary@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString,
        UUID.randomUUID()
      )

      val canary = "existing_canary"

      val dropPathTarget = Files.createTempDirectory(
        "smtst_159_"
      )

      val canaryPath = dropPathTarget.resolve(canary)
      canaryPath.toFile.mkdirs()

      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString

            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }
        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val staticConfig = new StaticConfig()

      val dropper =
        new MailDrop(staticConfig, system.actorOf(Props[BlackholeActor]))

      Await.ready(
        dropper.dropMessage(newMessage),
        200.millis
      )

      Files.list(canaryPath).count() mustBe 0

      Files.delete(canaryPath)
      hardDelete(dropPathTarget)
    }

    def hardDelete(directory: Path): Unit = {
      Files
        .walk(directory)
        .sorted(Comparator.reverseOrder())
        .forEach(Files.delete(_))
    }

    "pull recipient mailbox from a valid recipient" in {
      val dropPathTarget = Files.createTempDirectory(
        "smtst_206_"
      )
      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString
            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }
        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val staticConfig = new StaticConfig()
      val mailDrop =
        new MailDrop(staticConfig, system.actorOf(Props[BlackholeActor]))

      val testRecipient = "test.recipient.179@shadowmute.com"
      val extracted = mailDrop.extractRecipientMailbox(testRecipient)

      extracted mustBe defined

      extracted mustBe Some("test.recipient.179")

      // Do the same thing with mixed case domain

      val testRecipientMixedCase = "test.recipient.202@ShadowMute.COM"
      val extractedMixedCase =
        mailDrop.extractRecipientMailbox(testRecipientMixedCase)

      extractedMixedCase mustBe defined

      extractedMixedCase mustBe Some("test.recipient.202")
    }

    "not pull recipient mailbox from an invalid host domain" in {
      val dropPathTarget = Files.createTempDirectory(
        "smtst_245_"
      )
      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString
            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }
        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val staticConfig = new StaticConfig()
      val mailDrop =
        new MailDrop(staticConfig, system.actorOf(Props[BlackholeActor]))

      val testRecipient = "test.recipient.259@mutedshadows.com"
      val extracted = mailDrop.extractRecipientMailbox(testRecipient)

      extracted mustBe empty
    }

    "not throw an exception in a non-RFC compliant UUID" in {
      val dropPathTarget = Files.createTempDirectory(
        "smtst_272_"
      )
      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString
            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }
        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val staticConfig = new StaticConfig()
      val mailDrop =
        new MailDrop(
          staticConfig,
          system.actorOf(Props(new UnwrappedEchoActor()))
        )

      val nonCompliant = "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"

      val result = Await.result(
        mailDrop.convertMailboxToUserKeyPath(nonCompliant),
        100.millis
      )

      result mustBe defined

      result.get.toString mustBe nonCompliant.toLowerCase()
    }

    "convert a supplied recipient mailbox into a user key directory" in {
      val dropPathTarget = Files.createTempDirectory(
        "smtst_306_"
      )
      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString
            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }
        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val targetUserKey = UUID.randomUUID()

      class SimulatedUserRegistry extends Actor {
        override def receive: Receive = {
          case _ => sender ! Option(targetUserKey)
        }
      }

      val staticConfig = new StaticConfig()
      val mailDrop =
        new MailDrop(
          staticConfig,
          system.actorOf(Props(new SimulatedUserRegistry()))
        )

      val incomingMailbox = UUID.randomUUID()

      val result = Await.result(
        mailDrop.convertMailboxToUserKeyPath(incomingMailbox.toString),
        100.millis
      )

      result mustBe defined

      result mustBe Some(targetUserKey.toString)
    }

    "Ensure a message to a non-user folder ends up in the discard" in {
      val unknownUuid = UUID.randomUUID()

      val relayIP = new InetSocketAddress("69.70.71.72", 25)

      val canaryUuid = UUID.randomUUID()

      val newMessage = MailMessage(
        recipient = s"$unknownUuid@shadowmute.com",
        body = Vector("test", canaryUuid.toString),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString,
        UUID.randomUUID()
      )

      val dropPathTarget = Files.createTempDirectory(
        "smtst_362_"
      )
      dropPathTarget.toFile.deleteOnExit()

      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString

            override def discardDirectory: String = "discard"

            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }
        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val staticConfig = new StaticConfig()

      val dropper =
        new MailDrop(staticConfig, system.actorOf(Props[AlwaysNoneActor]))

      Await.ready(
        dropper.dropMessage(newMessage),
        200.millis
      )

      val recipientTarget = dropPathTarget.resolve("discard")
      recipientTarget.toFile.deleteOnExit()

      Files.exists(recipientTarget) mustBe true

      val child = Files
        .walk(recipientTarget)
        .sorted(Comparator.reverseOrder())
        .findFirst()

      // These are java optionals, not Option[]
      child.isPresent mustBe true

      val dropped = child.get()

      val src =
        Source.fromFile(dropped.toString).getLines.mkString("")

      src.contains(canaryUuid.toString) mustBe true
    }

    "Ensure a message to a special box ends up in the special handler drop" in {
      val relayIP = new InetSocketAddress("69.70.71.72", 25)

      val dropPathTarget = Files.createTempDirectory(
        "smtst_419_"
      )
      dropPathTarget.toFile.deleteOnExit()

      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString

            override def discardDirectory: String = "discard"

            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: List[String] = List("testing")

            override def defaultExpirationDays: Int = 60
          }
        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val canaryUuid = UUID.randomUUID()

      val newMessage = MailMessage(
        recipient = "testing@shadowmute.com",
        body = Vector("test", canaryUuid.toString),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString,
        UUID.randomUUID()
      )

      val staticConfig = new StaticConfig()

      val dropper =
        new MailDrop(staticConfig, system.actorOf(Props[AlwaysNoneActor]))

      Await.ready(
        dropper.dropMessage(newMessage),
        200.millis
      )

      val recipientTarget = dropPathTarget.resolve("special")
      recipientTarget.toFile.deleteOnExit()

      Files.exists(recipientTarget) mustBe true

      val child = Files
        .walk(recipientTarget)
        .sorted(Comparator.reverseOrder())
        .findFirst()

      // These are java optionals, not Option[]
      child.isPresent mustBe true

      val dropped = child.get()

      val src =
        Source.fromFile(dropped.toString).getLines.mkString("")

      src.contains(canaryUuid.toString) mustBe true
    }

    "write a personal provider message with an expiration to the user folder" in {

      val uuid = UUID.randomUUID()

      val relayIP = new InetSocketAddress("21.22.23.24", 25)

      val expiration = Instant.ofEpochSecond(1562634077)
      val newMessage = MailMessage(
        recipient = s"$uuid@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString,
        key = UUID.randomUUID(),
        expiration = Option(expiration)
      )

      val dropPathTarget = Files.createTempDirectory(
        "smtst_563_"
      )
      dropPathTarget.toFile.deleteOnExit()

      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString

            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }

        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val staticConfig = new StaticConfig()

      val dropper =
        new MailDrop(
          staticConfig,
          system.actorOf(Props(new UnwrappedEchoActor()))
        )

      Await.ready(
        dropper.dropMessage(newMessage),
        200.millis
      )

      val recipientTarget = dropPathTarget.resolve(uuid.toString)

      recipientTarget.toFile.deleteOnExit()

      Files.exists(recipientTarget) mustBe true

      val recipientContents =
        Files.newDirectoryStream(recipientTarget).asScala.toList

      recipientContents.length mustBe 1

      val droppedFile = recipientTarget.resolve(recipientContents.head)
      val src =
        Source.fromFile(droppedFile.toString).getLines.mkString("")

      src.contains(uuid.toString) mustBe true

      src.contains("Expiry-Date: Tue, 9 Jul 2019 01:01:17 +0000 (UTC)") mustBe true

      droppedFile.toFile.deleteOnExit()
    }

    "write a message with a default expiration to the user folder" in {

      val uuid = UUID.randomUUID()

      val relayIP = new InetSocketAddress("21.22.23.24", 25)

      val newMessage = MailMessage(
        recipient = s"$uuid@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString,
        key = UUID.randomUUID(),
        expiration = None
      )

      val dropPathTarget = Files.createTempDirectory(
        "smtst_648_"
      )
      dropPathTarget.toFile.deleteOnExit()

      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString

            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }

        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("shadowmute.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      val staticConfig = new StaticConfig()

      val dropper =
        new MailDrop(
          staticConfig,
          system.actorOf(Props(new UnwrappedEchoActor()))
        )

      Await.ready(
        dropper.dropMessage(newMessage),
        200.millis
      )

      val recipientTarget = dropPathTarget.resolve(uuid.toString)

      recipientTarget.toFile.deleteOnExit()

      Files.exists(recipientTarget) mustBe true

      val recipientContents =
        Files.newDirectoryStream(recipientTarget).asScala.toList

      recipientContents.length mustBe 1

      val droppedFile = recipientTarget.resolve(recipientContents.head)
      val src =
        Source.fromFile(droppedFile.toString).getLines.mkString("")

      src.contains(uuid.toString) mustBe true

      src.contains("Expiry-Date: ") mustBe true

      droppedFile.toFile.deleteOnExit()
    }

    "convert a shortened recipient mailbox into a user key directory" in {
      val dropPathTarget = Files.createTempDirectory(
        "smtst_741_"
      )
      class StaticConfig extends Configuration {
        override val mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString
            override def discardDirectory: String = "discard"
            override def specialMailboxDirectory: String = "special"

            override def specialMailboxes: Seq[String] = Nil

            override def defaultExpirationDays: Int = 60
          }
        override def mailboxObservationInterval: Int = 1

        override val validRecipientDomains: Seq[String] = {
          List("example.com")
        }

        override def tls: TlsConfiguration = null

        override def filters: FilterConfiguration = null
      }

      class SimulatedUserRegistry extends Actor {
        override def receive: Receive = {
          case RecipientQuery(incoming) => sender ! Option(incoming)
        }
      }

      val staticConfig = new StaticConfig()
      val mailDrop =
        new MailDrop(
          staticConfig,
          system.actorOf(Props(new SimulatedUserRegistry()))
        )

      val incomingMailbox = "abcd1234"

      val result = Await.result(
        mailDrop.convertMailboxToUserKeyPath(incomingMailbox.toString),
        100.millis
      )

      result mustBe defined

      result mustBe Some("abcd1234-0000-0000-0000-000000000000".toString)
    }
  }
}
