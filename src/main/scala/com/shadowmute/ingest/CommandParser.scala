package com.shadowmute.ingest

object CommandParser {
  def parse(
      incoming: SmtpConnection.IncomingMessage): Either[Response, Verb] = {
    val verbAction = incoming.message.split(" ", 2)

    val verb = verbAction(0).toLowerCase
    if (verb == "helo") {
      Right(Helo(verbAction.lift(1).getOrElse("")))
    } else if (verb == "ehlo") {
      Right(Ehlo(verbAction.lift(1).getOrElse("")))
    } else if (verb == "noop") {
      Right(Noop(verbAction.lift(1).getOrElse("")))
    } else if (verb == "quit") {
      Right(Quit())
    } else if (verb == "vrfy") {
      Right(Vrfy(verbAction.lift(1).getOrElse("")))
    } else {
      Logger().debug(s"[-] not helo {$verb}")
      Left(CommandNotRecognized())
    }
  }
}
