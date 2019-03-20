package com.shadowmute.ingest.mailbox

import akka.actor.Actor

class AlwaysNoneActor extends Actor {
  override def receive: Receive = {
    case RecipientQuery(_) => sender ! None
  }
}
