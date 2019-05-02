package com.shadowmute.ingest.smtp

import org.scalatest._

class ResponseSpec extends WordSpec with MustMatchers {
  "Responses" must {
    "Start with the correct response code in Ok" in {
      val actual = Ok("Testing")

      actual.toString must startWith("250 ")
    }

    "Start with the correct response code in Closing Connection" in {
      val actual = ClosingConnection("Testing")

      actual.toString must startWith("221 ")
    }

    "Start with the correct response code in CommandNotRecognized" in {
      val actual = CommandNotRecognized()

      actual.toString must startWith("500 ")
    }

    "Start with the correct response code in CannotVerifyUser" in {
      val actual = CannotVerifyUser()

      actual.toString must startWith("252 ")
    }

    "Start with the correct response code in RequestedActionNotTaken" in {
      val actual = RequestedActionNotTaken("Test Reasons")

      actual.toString must startWith("550 ")
    }

    "Start with the correct response code in SyntaxError" in {
      val actual = SyntaxError()

      actual.toString must startWith("501 ")
    }

    "Start with the correct response code in TooManyRecipients" in {
      val actual = TooManyRecipients()

      actual.toString must startWith("452 ")
    }

    "Start with the correct response code in TLSReady" in {
      val actual = TLSReady()

      actual.toString must startWith("220 ")
    }

  }
}
