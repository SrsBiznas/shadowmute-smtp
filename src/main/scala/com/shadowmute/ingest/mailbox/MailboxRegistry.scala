package com.shadowmute.ingest.mailbox

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import akka.actor.Actor

class MailboxRegistry extends Actor {

  val registry = new ConcurrentHashMap[UUID, UUID]()

  def updateRegistry(recipient: Recipient) {
    registry.putIfAbsent(recipient.mailbox, recipient.owner)
  }

  override def receive: Receive = {
    case NewMailboxEvent(recipients) =>
      recipients.foreach(updateRegistry)
    case RecipientQuery(mailbox) =>
      sender ! Option(registry.get(mailbox))
  }
}
