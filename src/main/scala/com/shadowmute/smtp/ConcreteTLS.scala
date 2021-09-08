package com.shadowmute.smtp

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BidiFlow, TLS}
import akka.stream.{EagerClose, TLSProtocol}
import akka.util.ByteString
import com.shadowmute.smtp.configuration.TlsConfiguration

import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{
  KeyManagerFactory,
  SSLContext,
  SSLEngine,
  TrustManagerFactory
}

trait TLSSessionGenerator {
  def newSession(implicit actorSystem: ActorSystem): BidiFlow[
    TLSProtocol.SslTlsOutbound,
    ByteString,
    ByteString,
    TLSProtocol.SslTlsInbound,
    NotUsed
  ]
}

class ConcreteTLS(configuration: TlsConfiguration) extends TLSSessionGenerator {
  private val passphrase = configuration.keystorePassphrase.toCharArray

  private val keyStore = KeyStore.getInstance("PKCS12")
  keyStore.load(new FileInputStream(configuration.keystorePath), passphrase)

  private val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(keyStore)

  private val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(keyStore, passphrase)

  def newSession(implicit actorSystem: ActorSystem): BidiFlow[
    TLSProtocol.SslTlsOutbound,
    ByteString,
    ByteString,
    TLSProtocol.SslTlsInbound,
    NotUsed
  ] = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      keyManagerFactory.getKeyManagers,
      trustManagerFactory.getTrustManagers,
      new SecureRandom
    )

    TLS(() => { createSSLEngine(sslContext) }, EagerClose)
  }

  def createSSLEngine(sslContext: SSLContext): SSLEngine = {
    val engine = sslContext.createSSLEngine()

    engine.setUseClientMode(false)
    engine.setEnabledCipherSuites(
      Array(
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
      )
    )

    engine.setEnabledProtocols(Array("TLSv1.3", "TLSv1.2"))

    engine
  }

}
