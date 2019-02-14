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
