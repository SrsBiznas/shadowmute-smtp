package com.shadowmute.ingest

class Response()

case class Ok(content: String) extends Response {
  override def toString = s"250 ${content}"
}

case class CommandNotRecognized() extends Response {
  override def toString = "500  Syntax error / Command not recognized"
}
