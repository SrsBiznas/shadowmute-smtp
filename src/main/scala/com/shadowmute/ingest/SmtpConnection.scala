package com.shadowmute.ingest

import akka.actor.{Actor, ActorRef, FSM, Props}

sealed trait State

case object Init extends State
case object Greeted extends State
case object IncomingMessage extends State
case object DataChannel extends State

sealed trait Data
case object Uninitialized extends Data
case class InitialSession(sourceDomain: String) extends Data
case class MailSession(sourceDomain: String,
                       reversePath: Option[String],
                       recipients: List[String])
    extends Data {
  def withRecipient(recipient: String) = {
    val newRecipients = recipient :: recipients
    MailSession(sourceDomain, reversePath, newRecipients)
  }

  def openDataChannel() = {
    DataChannel(sourceDomain, reversePath, recipients, Vector.empty)
  }
}

case class DataChannel(
    sourceDomain: String,
    reversePath: Option[String],
    recipients: List[String],
    buffer: Vector[String]
) extends Data {
  def withMoreData(additional: String) = {
    this.copy(buffer = buffer :+ additional)
  }

  override def toString() = {
    val firstRcpt = recipients.headOption.getOrElse("??missing??")
    val output = s"""
    |TO: <${firstRcpt}>
    |FROM: <${reversePath.getOrElse("")}>
    |BODY:
    |${buffer.mkString("\n")}
    |------------------------
    |
    """.stripMargin

    output
  }
}

class SmtpConnection extends Actor with FSM[State, Data] {

  startWith(Init, Uninitialized)

  def commonCommands(verb: Verb): Response = {
    verb match {
      case _: Noop => {
        Ok("0x90")
      }
      case _: Quit => {
        ClosingConnection("TTFN")
      }
      case _: Vrfy => {
        CannotVerifyUser()
      }
      case _: Rcpt => {
        CommandOutOfSequence()
      }
      case _: Mail => {
        CommandOutOfSequence()
      }
      case _: OpenDataChannel => {
        CommandOutOfSequence()
      }
      case _ => CommandNotImplemented()
    }
  }

  when(Init) {
    case Event(SmtpConnection.SendBanner, Uninitialized) => {
      Logger().debug("[*] Init")
      sender() ! "220 shadowmute.com"
      stay()
    }
    case Event(incoming: SmtpConnection.IncomingMessage, Uninitialized) => {
      // Logger().debug(s"[*] Incoming ${incoming}")
      CommandParser
        .parse(incoming)
        .fold(
          rejection => {
            sender() ! rejection
            stay()
          }, {
            _ match {
              case helo: Helo => {
                Logger().debug(s"[*] HELO from ${helo.domain}")
                sender() ! Ok("shadowmute.com")
                goto(Greeted) using InitialSession(helo.domain)
              }
              case ehlo: Ehlo => {
                replyToEhlo(sender(), ehlo.domain)
              }
              case _: Rset => {
                sender() ! Ok("Buffers reset")
                stay()
              }
              case unmatched: Verb => {
                sender() ! commonCommands(unmatched)
                stay()
              }
            }
          }
        )
    }
  }

  when(Greeted) {
    case Event(incoming: SmtpConnection.IncomingMessage,
               session: InitialSession) => {
      CommandParser
        .parse(incoming)
        .fold(
          rejection => {
            sender() ! rejection
            stay()
          }, {
            _ match {
              case _: Helo => {
                sender() ! Ok("shadowmute.com")
                stay()
              }
              case _: Ehlo => {
                replyToEhlo(sender(), session.sourceDomain)
              }
              case mail: Mail => {
                sender() ! Ok("Ok")
                goto(IncomingMessage) using MailSession(session.sourceDomain,
                                                        mail.reversePath,
                                                        Nil)
              }
              case _: Rset => {
                sender() ! Ok("Buffers reset")
                stay()
              }
              case unmatched: Verb => {
                sender() ! commonCommands(unmatched)
                stay()
              }
            }
          }
        )
    }
    case _ => {
      sender() ! CommandNotRecognized()
      stay()
    }
  }

  def replyToEhlo(sender: ActorRef, sourceDomain: String) = {

    Logger().debug(s"[*] EHLO from ${sourceDomain}")
    sender ! Ok(List("shadowmute.com", "8BITMIME", "SMTPUTF8"))
    goto(Greeted) using InitialSession(sourceDomain)
  }

  when(IncomingMessage) {
    case Event(incoming: SmtpConnection.IncomingMessage,
               session: MailSession) => {
      CommandParser
        .parse(incoming)
        .fold(
          rejection => {
            sender() ! rejection
            stay()
          }, {
            _ match {
              case rcptVerb: Rcpt => {
                if (session.recipients.length < 100) {
                  sender() ! Ok("Ok")
                  goto(IncomingMessage) using session.withRecipient(
                    rcptVerb.recipient)
                } else {
                  sender ! TooManyRecipients()
                  stay()
                }
              }
              case _: Mail => {
                sender() ! CommandOutOfSequence()
                stay()
              }
              case _: OpenDataChannel => {
                if (session.recipients.length == 0) {
                  sender ! CommandOutOfSequence()
                  stay()
                } else {
                  sender ! StartMailInput()
                  goto(DataChannel) using session.openDataChannel()
                }
              }
              case _: Rset => {
                sender() ! Ok("Buffers reset")
                goto(Greeted) using InitialSession(session.sourceDomain)
              }
              case _: Helo => {
                sender() ! Ok("Buffers reset")
                goto(Greeted) using InitialSession(session.sourceDomain)
              }
              case _: Ehlo => {
                replyToEhlo(sender(), session.sourceDomain)
              }
              case unmatched: Verb => {
                sender() ! commonCommands(unmatched)
                stay()
              }
            }
          }
        )
    }
    case _ => {
      Logger().debug("[118]")
      sender() ! CommandNotRecognized()
      stay()
    }
  }

  when(DataChannel) {
    case Event(incoming: SmtpConnection.IncomingMessage,
               session: DataChannel) => {
      val dataLine = incoming.message

      dataLine match {
        case "." => {
          Logger().debug(session.toString())
          // FEATURE: #25
          // TODO: actually store the message
          sender() ! Ok("Message received")

          goto(Greeted) using (InitialSession(session.sourceDomain))
        }
        case dotStarted: String if dotStarted.startsWith(".") => {
          sender() ! ReadNext()
          stay() using session.withMoreData(dataLine.drop(1))
        }
        case _: String => {
          sender() ! ReadNext()
          stay() using session.withMoreData(dataLine)
        }
      }
    }
  }
}

object SmtpConnection {
  case object SendBanner

  case class IncomingMessage(message: String)

  val props = Props[SmtpConnection]
}
