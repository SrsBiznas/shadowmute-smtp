package com.shadowmute.ingest.smtp

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.TLSProtocol.{SendBytes, SslTlsInbound, SslTlsOutbound}
import akka.stream._
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl._
import akka.util.{ByteString, Timeout}
import com.shadowmute.ingest.configuration.Configuration
import com.shadowmute.ingest.metrics.MetricCollector
import com.shadowmute.ingest.{
  SwitchTargetB,
  SwitchingBidiFlow,
  TLSSessionGenerator
}

import scala.concurrent.Future
import scala.concurrent.duration._

class SmtpHandler(
    val system: ActorSystem,
    configuration: Configuration,
    mailboxRegistry: ActorRef,
    tlsSessionGenerator: TLSSessionGenerator
) {

  val maxumumMessageSize = 1000

  implicit val timeout: Timeout = 5.seconds

  def handle(
      incomingConnection: IncomingConnection
  )(implicit m: Materializer): NotUsed = {

    val serverFlow = wrappedFlow(incomingConnection)

    incomingConnection.handleWith(serverFlow)
  }

  def smtpProcessorFlow(
      incomingConnection: IncomingConnection
  ): Flow[ByteString, Response, NotUsed] = {
    val welcomeMessage =
      Source.single(SmtpConnection.SendBanner(incomingConnection.remoteAddress))

    val connectionProcessor = system.actorOf(
      Props(new SmtpConnection(configuration, mailboxRegistry))
    )

    val processedSmtp = Flow[ByteString]
      .via(
        Framing.delimiter(
          ByteString(SmtpHandler.NL),
          maximumFrameLength = maxumumMessageSize,
          allowTruncation = true
        )
      )
      .map(_.utf8String)

      // Portion of the handler where it drops into genuine SMTP verb decoding
      .map(SmtpConnection.IncomingMessage)

      // Trigger the banner
      .merge(welcomeMessage)
      .mapAsync[Response](1)(
        m => (connectionProcessor ? m).asInstanceOf[Future[Response]]
      )

      // Catch the connection close requests
      .takeWhile(_.isInstanceOf[ClosingConnection] == false, inclusive = true)

      // If ReadNext, don't bother replying
      .filter(_.isInstanceOf[ReadNext] == false)

    processedSmtp
  }

  def generateSwitchableTlsFlow(
      placeboTLS: BidiFlow[
        SslTlsOutbound,
        ByteString,
        ByteString,
        SslTlsInbound,
        NotUsed
      ],
      concreteTlsSession: BidiFlow[
        SslTlsOutbound,
        ByteString,
        ByteString,
        SslTlsInbound,
        NotUsed
      ],
      smtpProcessor: Flow[ByteString, Response, NotUsed]
  ): Flow[ByteString, ByteString, NotUsed] = {

    val switcher = new SwitchingBidiFlow()

    Flow.fromGraph[ByteString, ByteString, NotUsed](
      GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
        {

          import GraphDSL.Implicits._

          val networkIn = builder.add(Flow[ByteString])

          val tlsSwitch = builder.add(switcher)

          val placebo = builder.add(placeboTLS)
          val encryptedChannel = builder.add(concreteTlsSession)

          val smtp: FlowShape[ByteString, Response] = builder.add(smtpProcessor)

          val mergeOut =
            builder.add(Merge[ByteString](2, eagerComplete = true))
          val mergeDecrypted =
            builder.add(Merge[ByteString](2, eagerComplete = true))

          networkIn ~> tlsSwitch.dataIn

          tlsSwitch.ingressA ~> placebo.in2
          tlsSwitch.ingressB ~> encryptedChannel.in2

          placebo.out2.collect {
            case sb: TLSProtocol.SessionBytes => sb.bytes
          } ~> mergeDecrypted
          encryptedChannel.out2.collect {
            case sb: TLSProtocol.SessionBytes => sb.bytes
          } ~> mergeDecrypted

          mergeDecrypted ~> smtp

          smtp.map(smtpResponse => {
            val outboundData =
              s"${smtpResponse.toString}${SmtpHandler.NL}".getBytes

            val switchMode = if (smtpResponse.isInstanceOf[TLSReady]) {
              MetricCollector.StartTLSInitiated.inc()
              Option(SwitchTargetB())
            } else {
              None
            }

            (ByteString(outboundData), switchMode)
          }) ~> tlsSwitch.processedResult

          tlsSwitch.egressA.map(SendBytes) ~> placebo.in1
          tlsSwitch.egressB.map(SendBytes) ~> encryptedChannel.in1

          placebo.out1 ~> mergeOut
          encryptedChannel.out1 ~> mergeOut

          val tail = builder.add(Flow[ByteString])

          mergeOut ~> tail

          FlowShape(networkIn.in, tail.out)

        }
      }
    )
  }

  def wrappedFlow(
      incomingConnection: IncomingConnection
  ): Flow[ByteString, ByteString, NotUsed] = {

    val placeboTLS = TLSPlacebo()

    val concreteTlsSession = tlsSessionGenerator.newSession(system)

    val smtpProcessor = smtpProcessorFlow(incomingConnection)

    generateSwitchableTlsFlow(placeboTLS, concreteTlsSession, smtpProcessor)
  }
}

object SmtpHandler {
  val NL = "\r\n"
}
