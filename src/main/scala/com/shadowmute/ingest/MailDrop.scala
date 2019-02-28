package com.shadowmute.ingest

import play.api.libs.json.Json

import java.nio.file.Paths
import java.io.{BufferedWriter, File, FileWriter}

import configuration.Configuration

class MailDrop(configuration: Configuration) {

  def dropMessage(message: MailMessage): Boolean = {

    val userDir = message.recipient.takeWhile(_ != '@')

    val realPath = Paths.get(configuration.mailDropPath)

    val path = new File(
      s"${realPath}${File.separator}${userDir}"
    )

    if (path.toPath == path.toPath.normalize()) {
      path.mkdirs()

      val outputFile = File.createTempFile("000", ".msg", path)

      val bw = new BufferedWriter(new FileWriter(outputFile))
      bw.write(Json.toJson(message).toString())
      bw.close()
      true
    } else {
      false
    }

  }
}
