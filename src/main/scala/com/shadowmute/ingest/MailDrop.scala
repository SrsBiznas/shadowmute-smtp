package com.shadowmute.ingest

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.shadowmute.ingest.configuration.Configuration
import com.shadowmute.ingest.mailbox.RecipientQuery
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class MailDrop(configuration: Configuration, mailboxRegistry: ActorRef) {

  // Arbitrary value
  private val ExecutorPoolConcurrency = 4

  private implicit val localEc: ExecutionContext =
    ExecutionContext.fromExecutor(
      Executors.newWorkStealingPool(ExecutorPoolConcurrency)
    )

  def extractRecipientMailbox(incomingAddress: String): Option[String] = {
    val segments = incomingAddress.split("@", 2).toList

    segments match {
      case mailbox :: domain :: Nil =>
        if (configuration.validRecipientDomains.contains(domain.toLowerCase())) {
          Option(mailbox)
        } else None
      case _ => None
    }
  }

  def convertMailboxToUserKeyPath(
      targetMailbox: String
  ): Future[Option[UUID]] = {

    val uuidMatch =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r.anchored

    val sanitizedMailbox = targetMailbox.toLowerCase()

    sanitizedMailbox match {
      case uuidMatch() =>
        val mailboxAsUUID = UUID.fromString(sanitizedMailbox)

        implicit val timeout: Timeout = 100.millis
        val result = mailboxRegistry ? RecipientQuery(mailboxAsUUID)

        result.asInstanceOf[Future[Option[UUID]]]
      case _ =>
        Future.successful(None)
    }
  }

  def dropMessage(message: MailMessage): Future[Unit] = {

    val userKeyResult: Future[String] =
      extractRecipientMailbox(message.recipient)
        .fold(
          // This *must* be folded to push the option into the Future
          Future.successful(None: Option[UUID])
        )(
          convertMailboxToUserKeyPath
        )
        .map(
          _.fold(
            configuration.mailDrop.discardDirectory
          )(
            _.toString
          )
        )

    val realPath = Paths.get(configuration.mailDrop.dropPath)

    userKeyResult.map(userKeyDir => {
      val path = new File(
        s"$realPath${File.separator}$userKeyDir"
      )

      if (path.toPath == path.toPath.normalize()) {
        path.mkdirs()

        val outputFile = File.createTempFile("000", ".msg", path)

        val bw = new BufferedWriter(new FileWriter(outputFile))
        bw.write(Json.toJson(message).toString())
        bw.close()
        true
      } else {
        false
      }
    })
  }
}
