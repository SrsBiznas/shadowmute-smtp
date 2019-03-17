package com.shadowmute.ingest.smtp

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._

class SmtpHandlerSpec extends WordSpec with MustMatchers {
  "SmtpHandler" must {
    "Terminate a flow on ClosingConnection with replay" in {
      // This tests the termination flow logic inside the
      // SmtpHandler, but hasn't been isolated so this test
      // is effectively dead code
      implicit val system = ActorSystem("reactive-tweets")
      implicit val materializer = ActorMaterializer()

      val expectedCloser = ClosingConnection("Bye")
      val terminationFlow = Flow[Response]
        .takeWhile(_.isInstanceOf[ClosingConnection] == false, inclusive = true)

      val sample: Source[Response, NotUsed] = Source(
        List(
          Ok("One"),
          Ok("Two"),
          expectedCloser,
          Ok("Bad")
        )
      )

      val transform = sample
        .via(terminationFlow)
        .runWith(Sink.seq)

      val res = Await.result(transform, 1.seconds)

      res.length mustBe 3

      res.reverse.head mustBe expectedCloser
    }
  }
}
