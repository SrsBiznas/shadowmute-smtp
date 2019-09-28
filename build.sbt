name := "shadowmute-ingest"

version := "0.15.0"

scalaVersion := "2.13.0"

lazy val akkaVersion = "2.5.25"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe" % "config" % "1.3.4",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe.play" %% "play-json" % "2.7.4",
  "javax.mail" % "javax.mail-api" % "1.6.2" exclude ("javax.activation", "activation"),
  "com.sun.mail" % "javax.mail" % "1.6.2",
  // Database layer
  "com.typesafe.slick" %% "slick" % "3.3.2",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2",
  "org.postgresql" % "postgresql" % "42.2.8",
  "com.github.tminglei" %% "slick-pg" % "0.18.0",
  // Prometheus
  "io.prometheus" % "simpleclient" % "0.6.0",
  "io.prometheus" % "simpleclient_httpserver" % "0.6.0",
  "io.prometheus" % "simpleclient_hotspot" % "0.6.0",
  // Tests
  "org.scalatest" %% "scalatest" % "3.0.8" % "test"
)

mainClass in (Compile, run) := Some(
  "com.shadowmute.ingest.ShadowmuteApplication")

unmanagedResourceDirectories in Compile += baseDirectory.value / "conf"

javaOptions in Universal ++= Seq(
  "-Dconfig.file=/etc/shadowmute/ingest.conf",
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

dockerEntrypoint := Seq("bin/shadowmute-ingest")
