package com.shadowmute.smtp.protocol

import java.nio.ByteBuffer

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import com.shadowmute.smtp.TLSSessionGenerator
import com.shadowmute.smtp.configuration.{
  Configuration,
  FilterConfiguration,
  MailDropConfiguration,
  TlsConfiguration
}
import com.shadowmute.smtp.mailbox.AlwaysNoneActor
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent._
import scala.concurrent.duration._

class SmtpHandlerSpec extends AnyWordSpec with Matchers {
  "SmtpHandler" must {
    "Terminate a flow on ClosingConnection with replay" in {
      // This tests the termination flow logic inside the
      // SmtpHandler, but hasn't been isolated so this test
      // is effectively dead code
      implicit val system: ActorSystem = ActorSystem("reactive-tweets")

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

    "Create a routable graph for TLS wrapping" in {
      val initialTLS = TLSPlacebo()
      val secondaryTLS = TLSPlacebo()

      val dummyFlow = Flow[ByteString].map(_ => Ok("test"))

      val testData = List(1, 2, 3, 4, 1005, -6, -7, -8, -9, -10)

      val ints: Source[ByteString, NotUsed] = Source
        .fromIterator(() => testData.iterator)
        .map[ByteString](b => {

          val bb = ByteBuffer.allocate(4)
          bb.putInt(b)
          bb.flip()
          val res = ByteString.fromByteBuffer(bb)
          res
        })

      implicit val system: ActorSystem = ActorSystem("flow-builder")

      val emptyConfig = new Configuration {
        override def mailDrop: MailDropConfiguration = ???

        override def validRecipientDomains: Seq[String] = ???

        override def mailboxObservationInterval: Int = ???

        override def tls: TlsConfiguration = ???

        override def filters: FilterConfiguration = ???
      }

      val emptyActor = system.actorOf(Props[AlwaysNoneActor]())

      val emptyTLSGenerator = new TLSSessionGenerator {
        override def newSession(implicit actorSystem: ActorSystem): BidiFlow[
          TLSProtocol.SslTlsOutbound,
          ByteString,
          ByteString,
          TLSProtocol.SslTlsInbound,
          NotUsed
        ] = ???
      }

      val smtpHandler =
        new SmtpHandler(system, emptyConfig, emptyActor, emptyTLSGenerator)
      val partialGraph = smtpHandler.generateSwitchableTlsFlow(
        initialTLS,
        secondaryTLS,
        dummyFlow
      )

      val terminus = Sink.seq[ByteString]

      val result = Await
        .result(ints.via(partialGraph).runWith(terminus), 200.millis)
        .toList

      result.head.utf8String mustBe s"250 test${SmtpHandler.NL}"
    }
  }
}
