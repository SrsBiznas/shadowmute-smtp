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

      helo.domain mustBe "sample.testing"
    }

    "parse an EHLO command" in {
      val incoming = SmtpConnection.IncomingMessage("EHLO sample.testing")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Ehlo]

      val ehlo: Ehlo = parseResult.asInstanceOf[Ehlo]

      ehlo.domain mustBe "sample.testing"
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

    "parse a basic MAIL command" in {
      val incoming =
        SmtpConnection.IncomingMessage("MAIL FROM:<userx@y.foo.org>")

      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Mail]

      val mailResult = parseResult.asInstanceOf[Mail]

      mailResult.reversePath mustBe Some("userx@y.foo.org")
    }

    "parse a MAIL with extra parameters command" in {
      val incoming = SmtpConnection.IncomingMessage(
        "MAIL FROM:<userx@y.foo.org> some extension parameters")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Mail]

      val mailResult = parseResult.asInstanceOf[Mail]

      mailResult.reversePath mustBe Some("userx@y.foo.org")
    }

    "parse a MAIL without a FROM" in {
      val incoming = SmtpConnection.IncomingMessage("MAIL <userx@y.foo.org>")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[SyntaxError]
    }

    "parse a MAIL missing the second half" in {
      val incoming = SmtpConnection.IncomingMessage("MAIL")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[SyntaxError]
    }

    "parse a MAIL with an invalid return-path" in {
      val incoming = SmtpConnection.IncomingMessage("MAIL FROM:userx@y.foo.org")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[RequestedActionNotTaken]
    }

    "parse a MAIL command with an invalid email in return path" in {
      val incoming =
        SmtpConnection.IncomingMessage("MAIL FROM:<userx@y.foo@org>")

      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[RequestedActionNotTaken]
    }

    "parse a MAIL command with a null return path" in {
      val incoming =
        SmtpConnection.IncomingMessage("MAIL FROM:<>")

      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Mail]

      val mailResult = parseResult.asInstanceOf[Mail]

      mailResult.reversePath mustBe empty
    }

    "parse a MAIL command with a broken truncation" in {
      val incoming =
        SmtpConnection.IncomingMessage("MAIL FRO")

      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[SyntaxError]
    }

    "parse a basic RCPT command" in {
      val incoming =
        SmtpConnection.IncomingMessage("RCPT TO:<userx@y.foo.org>")

      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Rcpt]

      val mailResult = parseResult.asInstanceOf[Rcpt]

      mailResult.recipient mustBe "userx@y.foo.org"
    }

    "parse a RCPT with extra parameters command" in {
      val incoming = SmtpConnection.IncomingMessage(
        "RCPT TO:<userx@y.foo.org> some extension parameters")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[Rcpt]

      val mailResult = parseResult.asInstanceOf[Rcpt]

      mailResult.recipient mustBe "userx@y.foo.org"
    }

    "parse a RCPT without a TO" in {
      val incoming = SmtpConnection.IncomingMessage("RCPT <userx@y.foo.org>")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[SyntaxError]
    }

    "parse a RCPT missing the second half" in {
      val incoming = SmtpConnection.IncomingMessage("RCPT")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[SyntaxError]
    }

    "parse a RCPT with an invalid destination" in {
      val incoming = SmtpConnection.IncomingMessage("RCPT TO:userx@y.foo.org")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[RequestedActionNotTaken]
    }

    "parse a RCPT command with an invalid email in destination path" in {
      val incoming =
        SmtpConnection.IncomingMessage("RCPT TO:<userx@y.foo@org>")

      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[RequestedActionNotTaken]
    }

    "parse a RCPT command with a null destination path" in {
      val incoming =
        SmtpConnection.IncomingMessage("RCPT TO:<>")

      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[MailboxNotAllowed]
    }

    "parse a RCPT command with a broken truncation" in {
      val incoming =
        SmtpConnection.IncomingMessage("RCPT T")

      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.left.value

      parseResult mustBe a[SyntaxError]
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

    "Return 354 when DATA command is received" in {
      val incoming = SmtpConnection.IncomingMessage("DATA")
      val parsed = CommandParser.parse(incoming)

      val parseResult = parsed.right.value

      parseResult mustBe a[OpenDataChannel]
    }
  }
}
