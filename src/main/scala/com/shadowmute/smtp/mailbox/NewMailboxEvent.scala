package com.shadowmute.smtp.mailbox

case class NewMailboxEvent(recipients: Seq[Recipient])
