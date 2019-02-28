package com.shadowmute.ingest

import org.scalatest._

import scala.io.Source

import java.util.UUID
import java.net.InetSocketAddress

import java.io.File
import java.nio.file.Files

import configuration.Configuration

class MailDropSpec extends WordSpec with MustMatchers {
  "Mail Drop" must {

    "Write a message to the user folder" in {

      val uuid = UUID.randomUUID()

      val relayIP = new InetSocketAddress("21.22.23.24", 25)

      val newMessage = MailMessage(
        recipient = s"${uuid}@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString
      )

      val dropPath = Files.createTempDirectory(
        "smtst_32_"
      )
      dropPath.toFile.deleteOnExit()

      class StaticConfig extends Configuration {
        override val mailDropPath = dropPath.toString
      }

      val staticConfig = new StaticConfig()

      val dropper = new MailDrop(staticConfig)

      dropper.dropMessage(newMessage)

      import scala.collection.JavaConversions._

      val recipientTarget = dropPath.resolve(uuid.toString)
      recipientTarget.toFile().deleteOnExit()

      Files.exists(recipientTarget) mustBe true

      val recipientContents = Files.newDirectoryStream(recipientTarget).toList

      recipientContents.length mustBe 1

      val droppedFile = recipientTarget.resolve(recipientContents.head)
      val src =
        Source.fromFile(droppedFile.toString).getLines.mkString("")

      src.contains(uuid.toString) mustBe true

      droppedFile.toFile().deleteOnExit()
    }

    "Ensure a message to the user folder can't traverse paths to non-existing dir" in {
      val uuid = UUID.randomUUID()

      val relayIP = new InetSocketAddress("69.70.71.72", 25)

      val newMessage = MailMessage(
        recipient =
          s"${uuid}${File.separator}..${File.separator}canary@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString
      )

      val dropPath = Files.createTempDirectory(
        "smtst_80_"
      )
      dropPath.toFile.deleteOnExit()

      class StaticConfig extends Configuration {
        override val mailDropPath = dropPath.toString
      }

      val staticConfig = new StaticConfig()

      val dropper = new MailDrop(staticConfig)

      dropper.dropMessage(newMessage)

      val recipientTarget = dropPath.resolve("canary")
      recipientTarget.toFile().deleteOnExit()

      Files.exists(recipientTarget) mustBe false
    }

    "Ensure a message to the user folder can't traverse paths to existing dir" in {
      val uuid = UUID.randomUUID()

      val relayIP = new InetSocketAddress("69.70.71.72", 25)

      val newMessage = MailMessage(
        recipient =
          s"${uuid}${File.separator}..${File.separator}canary@shadowmute.com",
        body = Vector("test", "body"),
        reversePath = Some("reverse@drop.path"),
        sourceDomain = "drop.path",
        relayIP = relayIP.toString
      )

      val canary = "existing_canary"

      val dropPath = Files.createTempDirectory(
        "smtst_119_"
      )

      val canaryPath = dropPath.resolve(canary)
      canaryPath.toFile.mkdirs()

      class StaticConfig extends Configuration {
        override val mailDropPath = dropPath.toString
      }

      val staticConfig = new StaticConfig()

      val dropper = new MailDrop(staticConfig)

      dropper.dropMessage(newMessage)

      Logger().debug(s"CanaryPath: ${canaryPath.toString}")

      Files.list(canaryPath).count() mustBe 0

      Files.delete(canaryPath)
      Files.delete(dropPath)
    }
  }
}
