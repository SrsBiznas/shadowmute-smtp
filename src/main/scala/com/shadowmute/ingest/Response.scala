package com.shadowmute.ingest

class Response()

case class Ok(content: String) extends Response {
  override def toString = s"250 ${content}"
}

case class ClosingConnection(content: String) extends Response {
  override def toString = s"221 ${content}"
}

case class CommandNotRecognized() extends Response {
  override def toString = {
    "500 Syntax error / Command not recognized"
  }
}

case class CannotVerifyUser() extends Response {
  override def toString = {
    "252 Cannot and will not verify the user"
  }
}

case class TooManyRecipients() extends Response {
  override def toString = {
    "452 Too many recipients"
  }
}

case class RequestedActionNotTaken(content: String) extends Response {
  override def toString = {
    s"550 ${content}"
  }
}

case class SyntaxError() extends Response {
  override def toString = {
    "501 Syntax Error"
  }
}

case class CommandNotImplemented() extends Response {
  override def toString = {
    "502 Command Not Implemented"
  }
}

case class CommandOutOfSequence() extends Response {
  override def toString = {
    "503 Command out of sequence"
  }
}

case class MailboxNotAllowed() extends Response {
  override def toString = {
    "553 Mailbox not allowed"
  }
}

case class StartMailInput() extends Response {
  override def toString = {
    "354 Start mail input; end with <CRLF>.<CRLF>"
  }
}

case class ReadNext() extends Response {}

//  451: RequestedActionAborted,
//  550: RequestedActionNotTaken,
//  553: MailboxNotAllowed,
//  501: SyntaxError
//  503: BadCommandSequence,
//  455: UnableToAccomodate,
//  555: ParametersNotImplemented
