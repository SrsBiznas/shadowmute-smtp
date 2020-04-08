package com.shadowmute.smtp.protocol

class Response()

case class Ok(content: List[String]) extends Response {
  def this(single: String) = this(List(single))

  def prependCode(elems: List[String]): List[String] = {
    elems match {
      case Nil          => Nil
      case elem :: Nil  => List(s"250 $elem")
      case elem :: tail => s"250-$elem" :: prependCode(tail)
    }
  }
  override def toString: String = {
    prependCode(content) mkString SmtpHandler.NL
  }
}

case class WelcomeMessage() extends Response {
  override def toString = "220 shadowmute.com"
}

object Ok {
  def apply(content: String) = new Ok(content)
}

case class ClosingConnection(content: String) extends Response {
  override def toString = s"221 $content"
}

case class CommandNotRecognized() extends Response {
  override def toString: String = {
    "500 Syntax error / Command not recognized"
  }
}

case class CannotVerifyUser() extends Response {
  override def toString: String = {
    "252 Cannot and will not verify the user"
  }
}

case class TooManyRecipients() extends Response {
  override def toString: String = {
    "452 Too many recipients"
  }
}

case class RequestedActionNotTaken(content: String) extends Response {
  override def toString: String = {
    s"550 $content"
  }
}

case class SyntaxError() extends Response {
  override def toString: String = {
    "501 Syntax Error"
  }
}

case class CommandNotImplemented() extends Response {
  override def toString: String = {
    "502 Command Not Implemented"
  }
}

case class CommandOutOfSequence() extends Response {
  override def toString: String = {
    "503 Command out of sequence"
  }
}

case class MailboxNotAllowed() extends Response {
  override def toString: String = {
    "553 Mailbox not allowed"
  }
}

case class StartMailInput() extends Response {
  override def toString: String = {
    "354 Start mail input; end with <CRLF>.<CRLF>"
  }
}

case class ReadNext() extends Response {}

case class TLSReady() extends Response {
  override def toString: String = "220 Ready to start TLS"
}

//  451: RequestedActionAborted,
//  550: RequestedActionNotTaken,
//  553: MailboxNotAllowed,
//  501: SyntaxError
//  503: BadCommandSequence,
//  455: UnableToAccomodate,
//  555: ParametersNotImplemented
