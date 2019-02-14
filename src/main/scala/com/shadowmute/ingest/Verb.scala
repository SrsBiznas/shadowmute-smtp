package com.shadowmute.ingest

class Verb()

case class Helo(content: String) extends Verb

case class Ehlo(content: String) extends Verb

case class Noop(content: String) extends Verb

case class Quit() extends Verb

case class Vrfy(content: String) extends Verb
