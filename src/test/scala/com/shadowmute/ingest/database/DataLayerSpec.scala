package com.shadowmute.ingest.database

import java.util.UUID

import com.shadowmute.ingest.mailbox.Recipient
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class DataLayerSpec extends WordSpec with MustMatchers {

  "Data layer" must {
    "Read a simple recipient from the database" in {
      val dataLayer = new ReadWriteDataLayer()

      Await.ready(
        dataLayer.deleteAll(),
        100.millis
      )

      val userKey = UUID.randomUUID()
      val mailbox = UUID.randomUUID()

      val newUser = Await.result(
        dataLayer.createUserRecord(
          dataLayer.UserRecord(1L, userKey)
        ),
        100.millis
      )

      Await.result(
        dataLayer.createRecipientRecord(
          dataLayer.RecipientRecord(newUser.id, mailbox, None)
        ),
        100.millis
      )

      val dbResp = dataLayer.getAllRecipients()

      val results = Await.result(dbResp, 100.millis)

      results.length mustBe 1

      results.head mustBe Recipient(mailbox, userKey)

      Await.ready(
        dataLayer.deleteAll(),
        100.millis
      )
    }

    "Return the highest recipient record read" in {
      val dataLayer = new ReadWriteDataLayer()

      Await.ready(
        dataLayer.deleteAll(),
        100.millis
      )

      val userKey = UUID.randomUUID()
      val mailbox = UUID.randomUUID()

      val newUser = Await.result(
        dataLayer.createUserRecord(
          dataLayer.UserRecord(1L, userKey)
        ),
        100.millis
      )

      Await.result(
        dataLayer.createRecipientRecord(
          dataLayer.RecipientRecord(newUser.id, mailbox, None)
        ),
        100.millis
      )

      Await.result(
        dataLayer.createRecipientRecord(
          dataLayer.RecipientRecord(newUser.id, mailbox, None)
        ),
        100.millis
      )

      val recipientId3 = Await.result(
        dataLayer.createRecipientRecord(
          dataLayer.RecipientRecord(newUser.id, mailbox, None)
        ),
        100.millis
      )

      val dbResp = dataLayer.getAllRecipients()

      val results = Await.result(dbResp, 100.millis)

      results.length mustBe 3

      results.head mustBe Recipient(mailbox, userKey)

      results.maxIndex mustBe recipientId3

      Await.ready(
        dataLayer.deleteAll(),
        100.millis
      )
    }

    "Only return the recipients after the partition value" in {
      val dataLayer = new ReadWriteDataLayer()

      Await.ready(
        dataLayer.deleteAll(),
        100.millis
      )

      val userKey = UUID.randomUUID()
      val mailboxToIgnore = UUID.randomUUID()
      val mailboxAfterPartition = UUID.randomUUID()
      val mailboxTwoAfterPartition = UUID.randomUUID()

      val newUser = Await.result(
        dataLayer.createUserRecord(
          dataLayer.UserRecord(1L, userKey)
        ),
        100.millis
      )

      val recipientToIgnore = Await.result[Long](
        dataLayer.createRecipientRecord(
          dataLayer.RecipientRecord(newUser.id, mailboxToIgnore, None)
        ),
        100.millis
      )

      Await.result(
        dataLayer.createRecipientRecord(
          dataLayer.RecipientRecord(newUser.id, mailboxAfterPartition, None)
        ),
        100.millis
      )

      Await.result(
        dataLayer.createRecipientRecord(
          dataLayer.RecipientRecord(newUser.id, mailboxTwoAfterPartition, None)
        ),
        100.millis
      )

      val dbResp = dataLayer.getAllRecipients(partition = recipientToIgnore)

      val results = Await.result(dbResp, 100.millis)

      results.length mustBe 2

      val mailboxes = results.map(_.mailbox)

      mailboxes must contain(mailboxAfterPartition)

      mailboxes must contain(mailboxTwoAfterPartition)

      Await.ready(
        dataLayer.deleteAll(),
        100.millis
      )
    }
  }
}
