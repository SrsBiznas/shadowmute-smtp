package com.shadowmute.ingest.filters

import java.time.Instant

import com.shadowmute.ingest.MailMessage
import com.shadowmute.ingest.configuration.Configuration

class PersonalProviderFilter(
    configuration: Configuration
) {

  private def domainMatch(needle: String, haystack: String) = {

    val domainComponent = (haystack splitAt (haystack lastIndexOf '@'))._2

    if (domainComponent.toLowerCase.endsWith(needle)) {
      val nl = needle.length
      val dcl = domainComponent.length

      val preChar = domainComponent.charAt(dcl - nl - 1)

      preChar == '@' || preChar == '.'
    } else {
      false
    }
  }

  def applyFilter(source: MailMessage): MailMessage = {
    val personalProviders = configuration.filters.personalProviders

    val personalProviderFound = source.reversePath
      .exists(path => {
        personalProviders.exists(domainMatch(_, path))
      })

    if (personalProviderFound) {
      // add expiration
      source.updateExpiration(Instant.now())
    } else {
      source
    }
  }
}
