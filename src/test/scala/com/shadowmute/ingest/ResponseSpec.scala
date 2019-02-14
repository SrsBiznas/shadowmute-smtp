package com.shadowmute.ingest

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
  }
}
