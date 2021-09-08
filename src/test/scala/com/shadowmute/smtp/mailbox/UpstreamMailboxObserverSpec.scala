package com.shadowmute.smtp.mailbox

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import com.shadowmute.smtp.database.ReadWriteDataLayer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._

class UpstreamMailboxObserverSpec
    extends TestKit(ActorSystem("UpstreamMailboxObserverSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "An Upstream Mailbox Observer" must {

    "send no messages to the registry when no new recipient is found" in {
      val probe = TestProbe()
      val observer =
        system.actorOf(Props(classOf[UpstreamMailboxObserver], probe.ref))

      observer ! Symbol("refresh")

      probe.expectNoMessage(100.millis)
    }

    "send a message to the registry when a new recipient is found" in {
      val probe = TestProbe()

      val observer =
        system.actorOf(Props(classOf[UpstreamMailboxObserver], probe.ref))

      val dataLayer = new ReadWriteDataLayer()

      Await.ready(
        dataLayer.deleteAll(),
        100.millis
      )

      val userKey = UUID.randomUUID()
      val mailbox = UUID.randomUUID()

      val newUser = Await.result(
        dataLayer.createUserRecord(
          dataLayer.UserRecord(1, userKey)
        ),
        100.millis
      )

      Await.result(
        dataLayer.createRecipientRecord(
          dataLayer.RecipientRecord(newUser.id, mailbox, None)
        ),
        100.millis
      )

      observer ! Symbol("refresh")

      probe.expectMsgAllClassOf(classOf[NewMailboxEvent])
    }
  }
}
