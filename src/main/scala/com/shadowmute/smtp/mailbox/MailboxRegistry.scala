package com.shadowmute.smtp.mailbox

import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import akka.actor.Actor
import com.shadowmute.smtp.MailMessage
import com.shadowmute.smtp.configuration.MailDropConfiguration
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.Try

class MailboxRegistry(mailDropConfiguration: MailDropConfiguration)
    extends Actor {

  val registry = new ConcurrentHashMap[UUID, UUID]()

  case class MessageWithPath(message: MailMessage, path: Path)

  def searchForMissedMessages(recipient: Recipient): Unit = {
    val searchPath = Paths.get(
      mailDropConfiguration.dropPath,
      mailDropConfiguration.discardDirectory
    )

    val fiveMinsAgo = Instant
      .now()
      .minus(5, ChronoUnit.MINUTES)

    val latestMessages = Files
      .list(searchPath)
      .filter(p =>
        Instant
          .ofEpochMilli(p.toFile.lastModified())
          .isAfter(fiveMinsAgo)
      )

    val messages = latestMessages.map[MessageWithPath](path => {
      val contents = Files.readAllBytes(path)
      val message = Json.parse(contents).validate[MailMessage].get
      MessageWithPath(message, path)
    })

    val correctTarget =
      Paths.get(mailDropConfiguration.dropPath, recipient.owner.toString)

    if (Files.notExists(correctTarget)) {
      Try(
        Files.createDirectory(correctTarget)
      )
    }

    messages
      .filter(_.message.recipient.startsWith(s"${recipient.mailbox}@"))
      .forEach { case MessageWithPath(_, path) =>
        val dest = correctTarget.resolve(path.getFileName)
        Files.move(path, dest)
      }

  }

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  def updateRegistry(recipient: Recipient): Unit = {
    registry.computeIfAbsent(
      recipient.mailbox,
      _ => {
        Future { searchForMissedMessages(recipient) }
        recipient.owner
      }
    )
  }

  override def receive: Receive = {
    case NewMailboxEvent(recipients) =>
      recipients.foreach(updateRegistry)
    case RecipientQuery(mailbox) =>
      sender() ! Option(registry.get(mailbox))
  }
}
