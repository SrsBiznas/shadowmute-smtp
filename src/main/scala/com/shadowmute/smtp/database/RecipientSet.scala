package com.shadowmute.smtp.database

import com.shadowmute.smtp.mailbox.Recipient

case class RecipientSet(recipients: Seq[Recipient], maxIndex: Long)
    extends Seq[Recipient] {
  def length: Int = recipients.length

  override def iterator: Iterator[Recipient] = recipients.iterator

  def apply(index: Int): Recipient = recipients.apply(index)
}
