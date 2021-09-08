package com.shadowmute.smtp.mailbox

import java.nio.file.{Files, Paths}
import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.shadowmute.smtp.configuration.{
  Configuration,
  FilterConfiguration,
  MailDropConfiguration,
  TlsConfiguration
}
import com.shadowmute.smtp.{MailDrop, MailMessage}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._

class MailboxRegistrySpec
    extends TestKit(ActorSystem("MailboxRegistrySpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A Mailbox Registry" must {
    "add and retrieve mailboxes" in {

      val mailDropConfig: MailDropConfiguration =
        new MailDropConfiguration {
          override def dropPath: String = ""
          override def discardDirectory: String = "slush"
          override def specialMailboxDirectory: String = ""
          override def specialMailboxes: Seq[String] = Nil
          override def defaultExpirationDays: Int = 30
        }

      val registry =
        system.actorOf(Props(classOf[MailboxRegistry], mailDropConfig))

      val insertedMailbox = UUID.randomUUID()
      val mailboxOwner = UUID.randomUUID()

      registry ! NewMailboxEvent(List(Recipient(insertedMailbox, mailboxOwner)))

      // Give the registry time to catch up in the event of logging
      Thread.sleep(200)

      implicit val timeout: Timeout = 100.millis
      val future = registry ? RecipientQuery(insertedMailbox)

      val response = Await.result(future, 101.millis)

      response mustBe a[Option[_]]

      response.asInstanceOf[Option[_]] mustBe defined

      response.asInstanceOf[Option[_]].get mustBe a[UUID]

      response mustBe Some(mailboxOwner)
    }

    "return None for a non-existent mailbox" in {

      val mailDropConfig: MailDropConfiguration =
        new MailDropConfiguration {
          override def dropPath: String = ""
          override def discardDirectory: String = "slush"
          override def specialMailboxDirectory: String = ""
          override def specialMailboxes: Seq[String] = Nil
          override def defaultExpirationDays: Int = 30
        }

      val registry =
        system.actorOf(Props(classOf[MailboxRegistry], mailDropConfig))

      val insertedMailbox = UUID.randomUUID()
      val mailboxOwner = UUID.randomUUID()
      val fakeMailbox = UUID.randomUUID()

      registry ! NewMailboxEvent(List(Recipient(insertedMailbox, mailboxOwner)))

      // Give the registry time to catch up in the event of logging
      Thread.sleep(200)

      implicit val timeout: Timeout = 100.millis
      val future = registry ? RecipientQuery(fakeMailbox)

      val response = Await.result(future, 101.millis)

      response mustBe a[Option[_]]

      response.asInstanceOf[Option[_]] mustBe empty
    }

    "copy slush mail belonging to the new recipient to the correct mailbox" in {
      val newMailbox = UUID.randomUUID()

      val dropPathTarget = Files.createTempDirectory(
        "smtst_92_"
      )

      val slushPath = Paths.get(dropPathTarget.toString, "slush")
      Files.createDirectory(
        slushPath
      )

      val configuration = new Configuration {
        override def mailDrop: MailDropConfiguration =
          new MailDropConfiguration {
            override def dropPath: String = dropPathTarget.toString
            override def discardDirectory: String = "slush"
            override def specialMailboxDirectory: String = ""
            override def specialMailboxes: Seq[String] = Nil
            override def defaultExpirationDays: Int = 30
          }
        override def validRecipientDomains: Seq[String] = List("example.com")
        override def mailboxObservationInterval: Int = -1
        override def tls: TlsConfiguration = null
        override def filters: FilterConfiguration = null
      }
      val mailboxRegistry =
        system.actorOf(
          Props(classOf[MailboxRegistry], configuration.mailDrop)
        )

      val maildrop = new MailDrop(configuration, mailboxRegistry)

      val earlyMessage = MailMessage(
        s"$newMailbox@example.com",
        Vector("test"),
        None,
        "source.domain",
        "relay.ip",
        UUID.randomUUID()
      )

      maildrop.dropMessage(
        earlyMessage
      )

      Thread.sleep(500)

      val slushContents = Files.list(slushPath)
      slushContents.count() mustBe 1

      val ownerID = UUID.randomUUID()
      val newRecipient = Recipient(newMailbox, ownerID)
      mailboxRegistry ! NewMailboxEvent(List(newRecipient))

      Thread.sleep(500)

      val slushContentsAfter = Files.list(slushPath)
      slushContentsAfter.count() mustBe 0

      val ownerPath = Paths.get(dropPathTarget.toString, ownerID.toString)

      val ownerContents = Files.list(ownerPath)
      ownerContents.count() mustBe 1
    }
  }
}
