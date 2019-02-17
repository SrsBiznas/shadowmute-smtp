package com.shadowmute.ingest

import akka.actor.{Actor, FSM, Props}

sealed trait State

case object Init extends State
case object Greeted extends State
case object IncomingMessage extends State

sealed trait Data
case object Uninitialized extends Data
case class InitialSession(sourceDomain: String) extends Data
case class MailSession(sourceDomain: String,
                       reversePath: Option[String],
                       recipients: Seq[String])
    extends Data

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
                Logger().debug(s"[*] EHLO from ${ehlo.domain}")
                sender() ! Ok("shadowmute.com")
                goto(Greeted) using InitialSession(ehlo.domain)
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
                sender() ! Ok("shadowmute.com")
                stay()
              }
              case mail: Mail => {
                sender() ! Ok("OK")
                goto(IncomingMessage) using MailSession(session.sourceDomain,
                                                        mail.reversePath,
                                                        Nil)
              }
              case unmatched: Verb => {
                Logger().debug("[97]")
                sender() ! commonCommands(unmatched)
                stay()
              }
            }
          }
        )
    }
    case _ => {
      Logger().debug("[107]")
      sender() ! CommandNotRecognized()
      stay()
    }
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
              case _: Mail => {
                sender() ! CommandOutOfSequence()
                stay()
              }
              case unmatched: Verb => {
                Logger().debug("[97]")
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
}

object SmtpConnection {
  case object SendBanner

  case class IncomingMessage(message: String)

  val props = Props[SmtpConnection]
}
