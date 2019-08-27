name := "shadowmute-ingest"

version := "0.11.1"

scalaVersion := "2.12.8"

lazy val akkaVersion = "2.5.20"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe" % "config" % "1.3.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe.play" %% "play-json" % "2.6.10",
  "javax.mail" % "javax.mail-api" % "1.6.2" exclude ("javax.activation", "activation"),
  "com.sun.mail" % "javax.mail" % "1.6.2",
  // Database layer
  "com.typesafe.slick" %% "slick" % "3.3.0",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.0",
  "org.postgresql" % "postgresql" % "42.2.5",
  "com.github.tminglei" %% "slick-pg" % "0.17.2",
  // Prometheus
  "io.prometheus" % "simpleclient" % "0.6.0",
  "io.prometheus" % "simpleclient_httpserver" % "0.6.0",
  "io.prometheus" % "simpleclient_hotspot" % "0.6.0",
  // Tests
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

mainClass in (Compile, run) := Some(
  "com.shadowmute.ingest.ShadowmuteApplication")

unmanagedResourceDirectories in Compile += baseDirectory.value / "conf"

javaOptions in Universal ++= Seq(
  "-Dconfig.file=/etc/shadowmute/ingest.conf",
  "-Dlogger.file=/etc/shadowmute/logger.xml"
)

// This is important to prevent tests from hitting the dev DB
javaOptions in Test += s"-Dconfig.file=${sourceDirectory.value}/test/resources/testing.conf"
javaOptions in Test += "-Duser.timezone=UTC"
fork in Test := true

scalacOptions ++= Seq("-Ywarn-unused",
                      "-Ywarn-unused-import",
                      "-Ywarn-dead-code")

scalafmtOnCompile in ThisBuild := true

enablePlugins(JavaAppPackaging)
// Required to run (ba)sh commands
enablePlugins(AshScriptPlugin)
enablePlugins(DockerPlugin)

// Due to the fact that Docker is not available in CI, use this
import com.typesafe.sbt.packager.docker.DockerVersion
dockerVersion := Some(DockerVersion(18, 9, 2, None))

daemonUserUid in Docker := Some("1000")
daemonUser in Docker := "daemon"

dockerBaseImage := "openjdk:8-jre-alpine"
dockerUpdateLatest := true

dockerExposedPorts ++= Seq(2025)


dockerEntrypoint := Seq("bin/shadowmute-ingest")
