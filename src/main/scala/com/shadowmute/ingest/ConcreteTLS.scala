package com.shadowmute.ingest

import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BidiFlow, TLS}
import akka.stream.{TLSClientAuth, TLSProtocol, TLSRole}
import akka.util.ByteString
import com.shadowmute.ingest.configuration.TlsConfiguration
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.collection.immutable.ArraySeq

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

    val sslConfig = AkkaSSLConfig()

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      keyManagerFactory.getKeyManagers,
      trustManagerFactory.getTrustManagers,
      new SecureRandom
    )

    val defaultParams = sslContext.getDefaultSSLParameters
    val defaultProtocols = defaultParams.getProtocols
    val protocols =
      sslConfig.configureProtocols(defaultProtocols, sslConfig.config)
    defaultParams.setProtocols(protocols)

    // ciphers
    val defaultCiphers = defaultParams.getCipherSuites
    val cipherSuites =
      sslConfig.configureCipherSuites(defaultCiphers, sslConfig.config)
    defaultParams.setCipherSuites(cipherSuites)

    val negotiateNewSession = TLSProtocol.NegotiateNewSession
      .withCipherSuites(ArraySeq.unsafeWrapArray(cipherSuites): _*)
      .withProtocols(ArraySeq.unsafeWrapArray(protocols): _*)
      .withParameters(defaultParams)
      .withClientAuth(TLSClientAuth.None)

    TLS(sslContext, Option(sslConfig), negotiateNewSession, TLSRole.server)
  }

}
