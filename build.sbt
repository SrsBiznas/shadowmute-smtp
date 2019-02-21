name := "shadowmute-ingest"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.12.8"

lazy val akkaVersion = "2.5.20"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

mainClass in (Compile, run) := Some(
  "com.shadowmute.ingest.ShadowmuteApplication")

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

dockerEntrypoint := Seq("bin/shadowmute-ingest",
                        "-Dconfig.file=/etc/shadowmute/default.conf",
                        "-Dlogger.file=/etc/shadowmute/logger.xml")
