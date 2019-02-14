package com.shadowmute.ingest

import org.scalatest._

class CommandParserSpec extends WordSpec with MustMatchers with EitherValues {
  "Command Parser" must {
    "parse a HELO command" in {
      val incoming = SmtpConnection.IncomingMessage("HELO sample.testing")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Helo]

      val helo: Helo = parseResult.asInstanceOf[Helo]

      helo.content mustBe "sample.testing"
    }

    "parse an EHLO command" in {
      val incoming = SmtpConnection.IncomingMessage("EHLO sample.testing")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Ehlo]

      val ehlo: Ehlo = parseResult.asInstanceOf[Ehlo]

      ehlo.content mustBe "sample.testing"
    }

    "parse a NOOP command" in {
      val incoming = SmtpConnection.IncomingMessage("NOOP sample.testing")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Noop]

      val noop: Noop = parseResult.asInstanceOf[Noop]

      noop.content mustBe "sample.testing"
    }

    "parse a QUIT command" in {
      val incoming = SmtpConnection.IncomingMessage("QUIT sample.testing")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Quit]
    }

    "parse a VRFY command" in {
      val incoming = SmtpConnection.IncomingMessage("VRFY sample.testing")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Vrfy]
    }

    "Return command not recognized when empty" in {
      val incoming = SmtpConnection.IncomingMessage("")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[CommandNotRecognized]
    }

    "Return command not recognized with a non-supported command" in {
      val incoming = SmtpConnection.IncomingMessage("NOTSUPPORTED")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[CommandNotRecognized]
    }
  }
}
