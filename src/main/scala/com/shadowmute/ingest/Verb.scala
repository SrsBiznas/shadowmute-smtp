package com.shadowmute.ingest

class Verb()

case class Helo(domain: String) extends Verb

case class Ehlo(domain: String) extends Verb

case class Noop(content: String) extends Verb

case class Quit() extends Verb

case class Vrfy(content: String) extends Verb

case class Mail(reversePath: Option[String], parameters: Option[String])
    extends Verb
