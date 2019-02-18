package com.shadowmute.ingest

object CommandParser {

  def validateEmailAddress(email: String): Boolean = {
    email.split("@").length == 2
  }

  def parseMailVerb(contents: String): Either[Response, Verb] = {
    val parts = contents.split(" ", 2)

    val reversePathArg = parts(0).trim

    if (reversePathArg.nonEmpty && reversePathArg
          .take(5)
          .toLowerCase == "from:") {
      val reversePath = reversePathArg.drop(5)

      if (reversePath.take(1) == "<" && reversePath.takeRight(1) == ">") {
        val reversePathInternal = reversePath.drop(1).dropRight(1)

        if (reversePathInternal.length == 0) {
          Right(Mail(None, parts.lift(1)))
        } else if (validateEmailAddress(reversePathInternal)) {
          Right(Mail(Some(reversePathInternal), parts.lift(1)))
        } else {
          Left(RequestedActionNotTaken("Invalid Email Address"))
        }

      } else {
        Left(RequestedActionNotTaken("Invalid return path"))
      }
    } else {
      Left(SyntaxError())
    }
  }

  def parseRcptVerb(contents: String): Either[Response, Verb] = {
    val parts = contents.split(" ", 2)

    val recipientPathArg = parts(0).trim

    if (recipientPathArg.nonEmpty && recipientPathArg
          .take(3)
          .toLowerCase == "to:") {
      val recipientPath = recipientPathArg.drop(3)

      if (recipientPath.take(1) == "<" && recipientPath.takeRight(1) == ">") {
        val recipientPathInternal = recipientPath.drop(1).dropRight(1)

        if (recipientPathInternal.length == 0) {
          Left(MailboxNotAllowed())
        } else if (validateEmailAddress(recipientPathInternal)) {
          Right(Rcpt(recipientPathInternal, parts.lift(1)))
        } else {
          Left(RequestedActionNotTaken("Invalid Email Address"))
        }

      } else {
        Left(RequestedActionNotTaken("Invalid return path"))
      }
    } else {
      Left(SyntaxError())
    }
  }

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
    } else if (verb == "mail") {
      parseMailVerb(verbAction.lift(1).getOrElse(""))
    } else if (verb == "rcpt") {
      parseRcptVerb(verbAction.lift(1).getOrElse(""))
    } else {
      Logger().debug(s"[-] not helo {$verb}")
      Left(CommandNotRecognized())
    }
  }
}
