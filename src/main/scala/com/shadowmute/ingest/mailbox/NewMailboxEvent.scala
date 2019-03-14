package com.shadowmute.ingest.mailbox

case class NewMailboxEvent(recipients: Seq[Recipient])
