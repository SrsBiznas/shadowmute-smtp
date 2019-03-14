package com.shadowmute.ingest.mailbox

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class MailboxRegistrySpec
    extends TestKit(ActorSystem("MailboxRegistrySpec"))
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A Mailbox Registry" must {
    "add and retrieve mailboxes" in {

      val registry = system.actorOf(Props[MailboxRegistry])

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

      val registry = system.actorOf(Props[MailboxRegistry])

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
  }
}
