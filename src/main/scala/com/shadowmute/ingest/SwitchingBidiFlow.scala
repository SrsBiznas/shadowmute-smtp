package com.shadowmute.ingest

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet}
import akka.util.ByteString

class SwitchingBidiFlow()
    extends GraphStage[
      SwitchingBidiShape[ByteString, ByteString]
    ] {
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      var tlsEnabled = false

      setHandler(
        fromTcpNetwork,
        new InHandler {
          override def onPush(): Unit = {
            val bytes = grab(fromTcpNetwork)

            if (tlsEnabled) {
              push(toConcreteInboundTLS, bytes)
            } else {
              push(toPlaceboInboundTLS, bytes)
            }
          }
        }
      )

      setHandler(
        toConcreteInboundTLS,
        new OutHandler {
          override def onPull(): Unit = {
            if (tlsEnabled) {
              if (!hasBeenPulled(fromTcpNetwork)) {
                pull(fromTcpNetwork)
              }
            }
          }
        }
      )

      setHandler(
        toPlaceboInboundTLS,
        new OutHandler {
          override def onPull(): Unit = {
            if (!tlsEnabled) {
              pull(fromTcpNetwork)
            }
          }
        }
      )

      setHandler(
        fromSmtpProcessor,
        new InHandler {
          override def onPush(): Unit = {
            val smtpResponse = grab(fromSmtpProcessor)

            val (outgoingData, switchMode) = smtpResponse

            val tlsActive = tlsEnabled

            // Get the target before the switch gets flipped for the
            // event of the last plain text response
            val pushTarget = if (tlsEnabled) {
              toConcreteOutboundTLS
            } else {
              toPlaceboOutboundTLS
            }

            // This does not support switching back
            switchMode.foreach(mode => {
              if (mode == SwitchTargetB()) {
                tlsEnabled = true

                getHandler(toConcreteInboundTLS).onPull()
              }
            })

            push(pushTarget, outgoingData)

            // Reinitialize the concrete TLS if it changed state here
            if (tlsActive != tlsEnabled) {
              getHandler(toConcreteOutboundTLS).onPull()
            }

          }
        }
      )

      setHandler(
        toConcreteOutboundTLS,
        new OutHandler {
          override def onPull(): Unit = {
            if (tlsEnabled) {
              pull(fromSmtpProcessor)
            }
          }
        }
      )

      setHandler(
        toPlaceboOutboundTLS,
        new OutHandler {
          override def onPull(): Unit = {

            if (!tlsEnabled) {
              pull(fromSmtpProcessor)
            }
          }
        }
      )
    }

  val fromTcpNetwork: Inlet[ByteString] = Inlet("RawInput")

  val toPlaceboInboundTLS: Outlet[ByteString] = Outlet("toPlaceboTLSInbound")
  val toConcreteInboundTLS: Outlet[ByteString] = Outlet("toConcreteTLSInbound")

  val fromSmtpProcessor: Inlet[(ByteString, Option[SwitchTarget])] = Inlet(
    "ProcessedSmtpResult"
  )

  val toPlaceboOutboundTLS: Outlet[ByteString] = Outlet("toPlaceboTLSOutbound")
  val toConcreteOutboundTLS: Outlet[ByteString] = Outlet(
    "toConcreteTLSOutbound"
  )

  override def shape =
    SwitchingBidiShape(
      fromTcpNetwork,
      toPlaceboInboundTLS,
      toConcreteInboundTLS,
      fromSmtpProcessor,
      toPlaceboOutboundTLS,
      toConcreteOutboundTLS
    )
}
