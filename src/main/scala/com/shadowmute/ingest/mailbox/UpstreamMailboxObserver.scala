package com.shadowmute.ingest.mailbox

import akka.actor.{Actor, ActorRef}
import com.shadowmute.ingest.Logger
import com.shadowmute.ingest.database.DataLayer

import scala.concurrent.ExecutionContext

class UpstreamMailboxObserver(registry: ActorRef) extends Actor {
  var lastIdentifiedMailbox = 0L

  val dataLayer = new DataLayer()

  private val localEc = ExecutionContext.fromExecutor(
    java.util.concurrent.Executors.newSingleThreadExecutor()
  )

  def refreshMailboxes(): Unit = {

    Logger().info(
      s"[*] Refreshing mailboxes greater than $lastIdentifiedMailbox"
    )

    dataLayer
      .getAllRecipients(partition = lastIdentifiedMailbox)
      .filter(_.recipients.nonEmpty)(localEc)
      .foreach(
        recipients => {

          // Note: this happens in a future, so there's (an unlikely) threading issue here
          lastIdentifiedMailbox = lastIdentifiedMailbox.max(recipients.maxIndex)
          registry ! NewMailboxEvent(recipients)
        }
      )(localEc)
  }

  override def receive: Receive = {
    case Symbol("refresh") => refreshMailboxes()
  }
}
