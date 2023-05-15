name := "treehub"
organization := "io.github.uptane"
scalaVersion := "2.12.17"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

def itFilter(name: String): Boolean = name endsWith "IntegrationSpec"

def unitFilter(name: String): Boolean = !itFilter(name)

lazy val ItTest = config("it").extend(Test)

lazy val UnitTest = config("ut").extend(Test)

lazy val treehub = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .configs(ItTest)
  .settings(inConfig(ItTest)(Defaults.testTasks): _*)
  .configs(UnitTest)
  .settings(inConfig(UnitTest)(Defaults.testTasks): _*)
  .settings(UnitTest / testOptions := Seq(Tests.Filter(unitFilter)))
  .settings(IntegrationTest / testOptions := Seq(Tests.Filter(itFilter)))
  .settings(Seq(libraryDependencies ++= {
    val akkaV = "2.6.20"
    val akkaHttpV = "10.2.10"
    val scalaTestV = "3.0.9"
    val libatsV = "2.0.11"

    Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % "test",
      "com.typesafe.akka" %% "akka-http" % akkaHttpV,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
      "com.typesafe.akka" %% "akka-slf4j" % akkaV,
      "org.scalatest"     %% "scalatest" % scalaTestV % "test,it",

      "ch.qos.logback" % "logback-classic" % "1.4.7",
      "org.slf4j" % "slf4j-api" % "2.0.7",

      "io.github.uptane" %% "libats" % libatsV,
      "io.github.uptane" %% "libats-http" % libatsV,
      "io.github.uptane" %% "libats-http-tracing" % libatsV,
      "io.github.uptane" %% "libats-messaging" % libatsV,
      "io.github.uptane" %% "libats-messaging-datatype" % libatsV,
      "io.github.uptane" %% "libats-slick" % libatsV,
      "io.github.uptane" %% "libats-metrics-akka" % libatsV,
      "io.github.uptane" %% "libats-metrics-prometheus" % libatsV,
      "io.github.uptane" %% "libats-logging" % libatsV,
      "io.github.uptane" %% "libats-logging" % libatsV,

      "org.scala-lang.modules" %% "scala-async" % "0.9.6",
      "org.mariadb.jdbc" % "mariadb-java-client" % "3.1.4",

      "com.amazonaws" % "aws-java-sdk-s3" % "1.12.464"
    )
  }))

Compile / mainClass := Some("com.advancedtelematic.treehub.Boot")

import com.typesafe.sbt.packager.docker._

dockerRepository := Some("advancedtelematic")

Docker / packageName := packageName.value

dockerUpdateLatest := true

dockerAliases ++= Seq(dockerAlias.value.withTag(git.gitHeadCommit.value))

Docker / defaultLinuxInstallLocation := s"/opt/${moduleName.value}"

dockerBaseImage := "eclipse-temurin:17.0.3_7-jre-jammy"

Docker / daemonUser := "daemon"

enablePlugins(JavaAppPackaging, GitVersioning, BuildInfoPlugin)

Versioning.settings

buildInfoObject := "AppBuildInfo"
buildInfoPackage := "com.advancedtelematic.treehub"
buildInfoOptions += BuildInfoOption.Traits("com.advancedtelematic.libats.boot.VersionInfoProvider")
buildInfoOptions += BuildInfoOption.ToMap
buildInfoOptions += BuildInfoOption.BuildTime
