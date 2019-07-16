package com.shadowmute.ingest

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.Paths
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Date, UUID}
import java.util.concurrent.Executors

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.shadowmute.ingest.configuration.Configuration
import com.shadowmute.ingest.mailbox.RecipientQuery
import com.shadowmute.ingest.metrics._
import javax.mail.internet.MailDateFormat
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

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
  ): Future[Option[String]] = {

    val uuidMatch =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r.anchored

    val sanitizedMailbox = targetMailbox.toLowerCase()

    sanitizedMailbox match {
      case uuidMatch() =>
        val mailboxAsUUID = UUID.fromString(sanitizedMailbox)

        implicit val timeout: Timeout = 100.millis
        val result = mailboxRegistry ? RecipientQuery(mailboxAsUUID)

        result.asInstanceOf[Future[Option[UUID]]].map(_.map(_.toString))
      case _ =>
        Future.successful(None)
    }
  }

  private def updateMessageExpiration(message: MailMessage) = {
    val messageExpiry = message.expiration.getOrElse(
      Instant
        .now()
        .plus(
          configuration.mailDrop.defaultExpirationDays,
          ChronoUnit.DAYS
        )
    )

    val mdf = new MailDateFormat()
    val asDate = Date.from(messageExpiry)
    val expirationHeader = mdf.format(asDate)
    val expiringBody = Vector(s"Expiry-Date: $expirationHeader") ++ message.body

    message.copy(
      body = expiringBody,
      expiration = Option(messageExpiry)
    )
  }

  def dropMessage(message: MailMessage): Future[Boolean] = {

    val extractedRecipient = extractRecipientMailbox(message.recipient)

    val specialMailboxes = configuration.mailDrop.specialMailboxes

    val userKeyResult: Future[String] =
      extractedRecipient
        .fold(
          // This *must* be folded to push the option into the Future
          Future.successful(None: Option[String])
        )(
          address =>
            if (specialMailboxes.contains(address)) {
              MetricCollector.SpecialMailboxRouted.inc()
              Future.successful(
                Option(configuration.mailDrop.specialMailboxDirectory)
              )
            } else {
              convertMailboxToUserKeyPath(address)
            }
        )
        .map(
          _.fold({

            MetricCollector.MessageDiscarded.inc()
            configuration.mailDrop.discardDirectory
          })(userKey => {
            MetricCollector.MessageRecipientRouted.inc()
            userKey.toString
          })
        )

    val realPath = Paths.get(configuration.mailDrop.dropPath)

    // update the expiry date at the last second
    val expiringMessage = updateMessageExpiration(message)

    userKeyResult.map[Boolean](userKeyDir => {
      val path = new File(
        s"$realPath${File.separator}$userKeyDir"
      )

      if (path.toPath == path.toPath.normalize()) {
        path.mkdirs()

        val outputFile = File.createTempFile("000", ".msg", path)

        val bw = new BufferedWriter(new FileWriter(outputFile))
        val outputData = Json.toJson(expiringMessage).toString()

        MetricCollector.MessageSize.observe(outputData.length)

        bw.write(outputData)

        bw.close()
        true
      } else {
        false
      }
    })
  }
}
