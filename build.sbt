name := "shadowmute-smtp"

version := "0.15.0"

scalaVersion := "2.13.6"

lazy val prometheusVersion = "0.12.0"
lazy val slickVersion = "3.3.3"
lazy val akkaVersion = "2.6.16"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe" % "config" % "1.4.1",
  "ch.qos.logback" % "logback-classic" % "1.2.5",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "com.typesafe.play" %% "play-json" % "2.9.2",
//  "javax.mail" % "javax.mail-api" % "2.0.1" exclude ("javax.activation", "activation"),
  "jakarta.mail" % "jakarta.mail-api" % "2.0.1",
  "com.sun.mail" % "jakarta.mail" % "2.0.1",
  // Database layer
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
  "org.postgresql" % "postgresql" % "42.2.23",
  "com.github.tminglei" %% "slick-pg" % "0.19.7",
  // Prometheus
  "io.prometheus" % "simpleclient" % prometheusVersion,
  "io.prometheus" % "simpleclient_httpserver" % prometheusVersion,
  "io.prometheus" % "simpleclient_hotspot" % prometheusVersion,
  // Tests
  "org.scalatest" %% "scalatest" % "3.2.9" % "test"
)

mainClass in (Compile, run) := Some(
  "com.shadowmute.smtp.ShadowmuteApplication")

unmanagedResourceDirectories in Compile += baseDirectory.value / "conf"

javaOptions in Universal ++= Seq(
  "-Dconfig.file=/etc/shadowmute/smtp.conf",
  "-Dlogger.file=/etc/shadowmute/logger.xml",
  "-Dpidfile.path=/dev/null"
)

// This is important to prevent tests from hitting the dev DB
javaOptions in Test += s"-Dconfig.file=${sourceDirectory.value}/test/resources/testing.conf"
javaOptions in Test += "-Duser.timezone=UTC"
fork in Test := true

scalacOptions ++= Seq("-Ywarn-unused",
                      "-Ywarn-unused:imports",
                      "-Ywarn-dead-code",
                      "-deprecation")

scalafmtOnCompile in ThisBuild := true

enablePlugins(JavaAppPackaging)
// Required to run (ba)sh commands
enablePlugins(AshScriptPlugin)
enablePlugins(DockerPlugin)

// Due to the fact that Docker is not available in CI, use this
import com.typesafe.sbt.packager.docker.DockerVersion
dockerVersion := Some(DockerVersion(18, 9, 2, None))

daemonUserUid in Docker := Some("1")
daemonUser in Docker := "daemon"

dockerBaseImage := "adoptopenjdk/openjdk11:slim"
dockerUpdateLatest := true

dockerExposedPorts ++= Seq(2025)

dockerEntrypoint := Seq("bin/shadowmute-smtp")
