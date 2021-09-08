package com.shadowmute.smtp.protocol

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.{Actor, ActorRef, FSM, Props}
import com.shadowmute.smtp.configuration.Configuration
import com.shadowmute.smtp.filters.PersonalProviderFilter
import com.shadowmute.smtp.metrics._
import com.shadowmute.smtp.{Logger, MailDrop, MailMessage, protocol}

sealed trait State

case object Init extends State
case object Connected extends State
case object Greeted extends State
case object IncomingMessage extends State
case object DataChannel extends State

sealed trait Data
case object Uninitialized extends Data
case class Connection(relayAddress: InetSocketAddress) extends Data
case class InitialSession(relayAddress: InetSocketAddress, sourceDomain: String)
    extends Data
case class MailSession(
    relayAddress: InetSocketAddress,
    sourceDomain: String,
    reversePath: Option[String],
    recipients: List[String]
) extends Data {
  def withRecipient(recipient: String): MailSession = {
    val newRecipients = recipient :: recipients
    MailSession(relayAddress, sourceDomain, reversePath, newRecipients)
  }

  def openDataChannel(): DataChannel = {
    DataChannel(
      relayAddress,
      sourceDomain,
      reversePath,
      recipients,
      Vector.empty
    )
  }
}

case class DataChannel(
    relayAddress: InetSocketAddress,
    sourceDomain: String,
    reversePath: Option[String],
    recipients: List[String],
    buffer: Vector[String]
) extends Data {
  def withMoreData(additional: String): DataChannel = {
    this.copy(buffer = buffer :+ additional)
  }

  override def toString: String = {
    val firstRcpt = recipients.headOption.getOrElse("??missing??")
    val output = s"""
    |TO: <$firstRcpt>
    |FROM: <${reversePath.getOrElse("")}>
    |BODY:
    |${buffer.mkString("\n")}
    |------------------------
    |
    """.stripMargin

    output
  }
}

class SmtpConnection(
    configuration: Configuration,
    mailboxRegistry: ActorRef
) extends Actor
    with FSM[State, Data] {

  startWith(Init, Uninitialized)

  val personalProviderFilter = new PersonalProviderFilter(configuration)

  def commonCommands(verb: Verb): Response = {
    verb match {
      case _: Noop =>
        Ok("0x90")
      case _: Quit =>
        MetricCollector.ConnectionTerminatedGracefully.inc()
        ClosingConnection("TTFN")
      case _: Vrfy =>
        CannotVerifyUser()
      case _: Rcpt =>
        CommandOutOfSequence()
      case _: Mail =>
        CommandOutOfSequence()
      case _: OpenDataChannel =>
        CommandOutOfSequence()
      case _ => CommandNotImplemented()
    }
  }

  when(Init) {
    case Event(SmtpConnection.SendBanner(remoteAddress), Uninitialized) =>
      MetricCollector.ConnectionInitialized.inc()

      sender() ! WelcomeMessage()
      goto(Connected) using Connection(remoteAddress)
    case _ =>
      sender() ! CommandOutOfSequence()
      stay()
  }

  when(Connected) {
    case Event(
          incoming: SmtpConnection.IncomingMessage,
          connection: Connection
        ) =>
      // Logger().debug(s"[*] Incoming ${incoming}")
      CommandParser
        .parse(incoming)
        .fold(
          rejection => {
            sender() ! rejection
            stay()
          },
          {
            case helo: Helo =>
              Logger().debug(s"[*] HELO from ${helo.domain}")
              sender() ! Ok("shadowmute.com")
              goto(Greeted) using InitialSession(
                connection.relayAddress,
                helo.domain
              )
            case ehlo: Ehlo =>
              replyToEhlo(sender(), connection.relayAddress, ehlo.domain)
            case _: Rset =>
              sender() ! Ok("Buffers reset")
              stay()
            case _: StartTLS =>
              sender() ! TLSReady()
              stay()
            case unmatched: Verb =>
              sender() ! commonCommands(unmatched)
              stay()
          }
        )
  }

  when(Greeted) {
    case Event(
          incoming: SmtpConnection.IncomingMessage,
          session: InitialSession
        ) =>
      CommandParser
        .parse(incoming)
        .fold(
          rejection => {
            sender() ! rejection
            stay()
          },
          {
            case _: Helo =>
              sender() ! Ok("shadowmute.com")
              stay()
            case _: Ehlo =>
              replyToEhlo(sender(), session.relayAddress, session.sourceDomain)
            case mail: Mail =>
              sender() ! Ok("Ok")
              goto(IncomingMessage) using MailSession(
                session.relayAddress,
                session.sourceDomain,
                mail.reversePath,
                Nil
              )
            case _: Rset =>
              sender() ! Ok("Buffers reset")
              stay()
            case _: StartTLS =>
              sender() ! TLSReady()
              goto(Connected) using Connection(session.relayAddress)
            case unmatched: Verb =>
              sender() ! commonCommands(unmatched)
              stay()
          }
        )
    case _ =>
      sender() ! CommandNotRecognized()
      stay()
  }

  def replyToEhlo(
      sender: ActorRef,
      relayAddress: InetSocketAddress,
      sourceDomain: String
  ): FSM.State[protocol.State, Data] = {

    Logger().debug(s"[*] EHLO from $sourceDomain")
    sender ! Ok(List("shadowmute.com", "8BITMIME", "SMTPUTF8", "STARTTLS"))
    goto(Greeted) using InitialSession(relayAddress, sourceDomain)
  }

  when(IncomingMessage) {
    case Event(
          incoming: SmtpConnection.IncomingMessage,
          session: MailSession
        ) =>
      CommandParser
        .parse(incoming)
        .fold(
          rejection => {
            sender() ! rejection
            stay()
          },
          {
            case rcptVerb: Rcpt =>
              if (session.recipients.length < 100) {
                sender() ! Ok("Ok")
                goto(IncomingMessage) using session
                  .withRecipient(rcptVerb.recipient)
              } else {
                sender() ! TooManyRecipients()
                stay()
              }
            case _: Mail =>
              sender() ! CommandOutOfSequence()
              stay()
            case _: OpenDataChannel =>
              if (session.recipients.isEmpty) {
                sender() ! CommandOutOfSequence()
                stay()
              } else {
                sender() ! StartMailInput()
                MetricCollector.MessageInitiated.inc()
                goto(DataChannel) using session.openDataChannel()
              }
            case _: Rset =>
              sender() ! Ok("Buffers reset")
              goto(Greeted) using InitialSession(
                session.relayAddress,
                session.sourceDomain
              )
            case _: Helo =>
              sender() ! Ok("Buffers reset")
              goto(Greeted) using InitialSession(
                session.relayAddress,
                session.sourceDomain
              )
            case _: Ehlo =>
              replyToEhlo(sender(), session.relayAddress, session.sourceDomain)
            case unmatched: Verb =>
              sender() ! commonCommands(unmatched)
              stay()
          }
        )
    case _ =>
      Logger().debug("[118]")
      sender() ! CommandNotRecognized()
      stay()
  }

  def convertDataChannelToMessages(session: DataChannel): List[MailMessage] = {
    session.recipients.map(recipient => {
      MailMessage(
        recipient,
        session.buffer,
        session.reversePath,
        session.sourceDomain,
        session.relayAddress.toString,
        key = UUID.randomUUID(),
        expiration = None
      )
    })

  }

  when(DataChannel) {
    case Event(
          incoming: SmtpConnection.IncomingMessage,
          session: DataChannel
        ) =>
      val dataLine = incoming.message

      dataLine match {
        case "." =>
          Logger().debug(session.toString())

          val messages = convertDataChannelToMessages(session)
          val dropper = new MailDrop(configuration, mailboxRegistry)
          messages.foreach(message => {
            // Apply filters
            val filtered = personalProviderFilter.applyFilter(message)

            dropper.dropMessage(filtered)
          })

          MetricCollector.MessageCompleted.inc()
          sender() ! Ok("Message received")

          goto(Greeted) using InitialSession(
            session.relayAddress,
            session.sourceDomain
          )
        case dotStarted: String if dotStarted.startsWith(".") =>
          sender() ! ReadNext()
          stay() using session.withMoreData(dataLine.drop(1))
        case _: String =>
          sender() ! ReadNext()
          stay() using session.withMoreData(dataLine)
      }
  }
}

object SmtpConnection {
  case class SendBanner(remoteAddress: InetSocketAddress)

  case class IncomingMessage(message: String)

  val props: Props = Props[SmtpConnection]()
}
