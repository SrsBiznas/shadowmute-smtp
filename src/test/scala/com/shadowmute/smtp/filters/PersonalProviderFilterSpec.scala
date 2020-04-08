package com.shadowmute.smtp.filters

import java.util.UUID

import com.shadowmute.smtp.MailMessage
import com.shadowmute.smtp.configuration.{
  Configuration,
  FilterConfiguration,
  MailDropConfiguration,
  TlsConfiguration
}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PersonalProviderFilterSpec extends AnyWordSpec with Matchers {
  "Personal Provider filter" must {

    class StaticConfig extends Configuration {
      override val mailDrop: MailDropConfiguration =
        new MailDropConfiguration {
          override def dropPath: String = "blank"

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

      override def filters: FilterConfiguration = new FilterConfiguration {
        override def personalProviders: Seq[String] = List(
          "testable.com"
        )
      }
    }

    "pass through a message not sent from a blocked reverse domain but close" in {
      val configuration = new StaticConfig()
      val filter = new PersonalProviderFilter(configuration)

      val message = MailMessage(
        recipient = "testingbox@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@not-testable.com"),
        sourceDomain = "drop.path",
        relayIP = "1.2.3.4",
        key = UUID.randomUUID(),
        expiration = None
      )

      val result = filter.applyFilter(message)
      result.expiration mustBe empty
    }

    "pass through a message not sent from a blocked reverse domain" in {

      val configuration = new StaticConfig()
      val filter = new PersonalProviderFilter(configuration)

      val message = MailMessage(
        recipient = "testingbox@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@xyz.com"),
        sourceDomain = "drop.path",
        relayIP = "1.2.3.4",
        key = UUID.randomUUID(),
        expiration = None
      )

      val result = filter.applyFilter(message)
      result.expiration mustBe empty
    }

    "add an expiration to a message sent from a blocked reverse domain" in {
      val configuration = new StaticConfig()
      val filter = new PersonalProviderFilter(configuration)

      val message = MailMessage(
        recipient = "testingbox@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@testable.com"),
        sourceDomain = "drop.path",
        relayIP = "1.2.3.4",
        key = UUID.randomUUID(),
        expiration = None
      )

      val result = filter.applyFilter(message)
      result.expiration mustBe defined
    }

    "add an expiration to a message sent from a blocked reverse subdomain" in {
      val configuration = new StaticConfig()
      val filter = new PersonalProviderFilter(configuration)

      val message = MailMessage(
        recipient = "testingbox@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@mailer.testable.com"),
        sourceDomain = "drop.path",
        relayIP = "1.2.3.4",
        key = UUID.randomUUID(),
        expiration = None
      )

      val result = filter.applyFilter(message)
      result.expiration mustBe defined
    }
  }
}
