package com.shadowmute.smtp

import java.nio.ByteBuffer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.TLSProtocol.SendBytes
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source, TLSPlacebo}
import akka.stream.{ActorMaterializer, FlowShape}
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class SwitchingBidiFlowSpec
    extends TestKit(ActorSystem("SwitchingBidiFlowSpec"))
    with WordSpecLike
    with MustMatchers {

  "Switching Bidi Flow" must {

    "Perform a simple echo cycle using two placebos" in {

      implicit val materializer: ActorMaterializer = ActorMaterializer()

      akka.stream.TLSProtocol

      val terminus = Sink.seq[ByteString]

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

      val switcher = new SwitchingBidiFlow

      val asFlow = Flow.fromGraph[ByteString, ByteString, NotUsed](
        GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
          {

            import GraphDSL.Implicits._

            val networkIn = builder.add(Flow[ByteString])

            val tlsSwitch = builder.add(switcher)

            val placebo1 = builder.add(TLSPlacebo())
            val placebo2 = builder.add(TLSPlacebo())

            val mergeOut =
              builder.add(Merge[ByteString](2, eagerComplete = true))

            val mergeDecrypted =
              builder.add(Merge[ByteString](2, eagerComplete = true))

            networkIn.out ~> tlsSwitch.dataIn

            tlsSwitch.plaintextWrapperIn ~> placebo1.in2
            tlsSwitch.secureWrapperIn ~> placebo2.in2

            placebo1.out2.map(_.bytes) ~> mergeDecrypted
            placebo2.out2.map(_.bytes) ~> mergeDecrypted

            mergeDecrypted.out.map(unencrypted => {
              Logger().debug("Simulated Flow Map")
              (unencrypted, None)
            }) ~> tlsSwitch.processedResult

            tlsSwitch.plainTextWrapperOut.map(SendBytes) ~> placebo1.in1
            tlsSwitch.secureWrapperOut.map(SendBytes) ~> placebo2.in1

            placebo1.out1 ~> mergeOut
            placebo2.out1 ~> mergeOut

            FlowShape(networkIn.in, mergeOut.out)

          }
        }
      )

      val completeGraph = ints.via(asFlow).runWith(terminus)

      val result = Await
        .result(
          completeGraph,
          1000.millis
        )
        .map(_.asByteBuffer.getInt)

      result.toList mustBe testData
    }

    "Perform a simple echo cycle using two placebos with a switch" in {

      implicit val materializer: ActorMaterializer = ActorMaterializer()

      akka.stream.TLSProtocol

      val terminus = Sink.seq[ByteString]

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

      val switcher = new SwitchingBidiFlow

      val asFlow = Flow.fromGraph[ByteString, ByteString, NotUsed](
        GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
          {

            import GraphDSL.Implicits._

            val networkIn = builder.add(Flow[ByteString])

            val tlsSwitch = builder.add(switcher)

            val placebo1 = builder.add(TLSPlacebo())
            val placebo2 = builder.add(TLSPlacebo())

            val mergeOut =
              builder.add(Merge[ByteString](2, eagerComplete = true))

            val mergeDecrypted =
              builder.add(Merge[ByteString](2, eagerComplete = true))

            networkIn.out ~> tlsSwitch.dataIn

            tlsSwitch.plaintextWrapperIn ~> placebo1.in2

            tlsSwitch.secureWrapperIn ~> placebo2.in2

            placebo1.out2.map(_.bytes) ~> mergeDecrypted
            placebo2.out2.map(_.bytes) ~> mergeDecrypted

            mergeDecrypted.out.map(unencrypted => {

              val bb = unencrypted.asByteBuffer
              Logger().debug(s"Simulated Flow Map: $unencrypted / $bb")
              val bbi = bb.getInt
              Logger().debug(s"Extracted int value of $bbi")
              val switchMessage = if (bbi == 1005) {
                Option(SecureTunnelEnabled)
              } else {
                None
              }
              (unencrypted, switchMessage)

            }) ~> tlsSwitch.processedResult

            tlsSwitch.plainTextWrapperOut.map(SendBytes) ~> placebo1.in1
            tlsSwitch.secureWrapperOut.map(SendBytes) ~> placebo2.in1

            placebo1.out1 ~> mergeOut
            placebo2.out1 ~> mergeOut

            val debugTail = builder.add(Flow[ByteString])

            mergeOut.out.map(untouched => {
              Logger().debug("merge outbound")
              untouched
            }) ~> debugTail

            FlowShape(networkIn.in, debugTail.out)
          }
        }
      )

      val completeGraph = ints.via(asFlow).runWith(terminus)

      val result = Await
        .result(
          completeGraph,
          300.millis
        )
        .map(_.asByteBuffer.getInt)

      result.toList mustBe testData
    }
  }
}
