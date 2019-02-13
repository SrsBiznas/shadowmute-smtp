package com.shadowmute.ingest

class Verb()

case class Helo(content: String) extends Verb

case class Ehlo(content: String) extends Verb
