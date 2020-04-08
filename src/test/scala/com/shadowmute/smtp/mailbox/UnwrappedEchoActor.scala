package com.shadowmute.smtp.mailbox

import akka.actor.Actor

class UnwrappedEchoActor() extends Actor {
  override def receive: Receive = {
    case RecipientQuery(uuid) => sender ! Option(uuid)
  }
}
