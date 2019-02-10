name := "shadowmute-ingest"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.12.8"

lazy val akkaVersion = "2.5.20"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

mainClass in (Compile, run) := Some("com.shadowmute.ingest.AkkaQuickstart")

scalacOptions ++= Seq("-Ywarn-unused",
                      "-Ywarn-unused-import",
                      "-Ywarn-dead-code")

scalafmtOnCompile in ThisBuild := true
