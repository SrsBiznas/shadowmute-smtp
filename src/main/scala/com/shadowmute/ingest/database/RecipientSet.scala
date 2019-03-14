package com.shadowmute.ingest.database

import com.shadowmute.ingest.mailbox.Recipient

case class RecipientSet(recipients: Seq[Recipient], maxIndex: Long)
    extends Seq[Recipient] {
  def length: Int = recipients.length

  override def iterator: Iterator[Recipient] = recipients.iterator

  def apply(index: Int): Recipient = recipients.apply(index)
}
