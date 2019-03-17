package com.shadowmute.ingest.mailbox

import akka.actor.Actor

class UnwrappedEchoActor() extends Actor {
  override def receive: Receive = {
    case RecipientQuery(uuid) => sender ! Option(uuid)
  }
}
