name := "ota-lith"
organization := "io.github.uptane"
scalaVersion := "2.12.14"

updateOptions := updateOptions.value.withLatestSnapshots(false)

libraryDependencies ++= {
  val bouncyCastleV = "1.69"
  val akkaV = "2.6.18"
  val akkaHttpV = "10.2.8"

  Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleV,
    "org.bouncycastle" % "bcpkix-jdk15on" % bouncyCastleV,

    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  )
}

lazy val treehub = (ProjectRef(file("./repos/treehub"), "treehub"))
lazy val device_registry = (ProjectRef(file("./repos/device-registry"), "ota-device-registry"))
lazy val director = (ProjectRef(file("./repos/director"), "director"))
lazy val keyserver = (ProjectRef(file("./repos/ota-tuf"), "keyserver"))
lazy val reposerver = (ProjectRef(file("./repos/ota-tuf"), "reposerver"))

dependsOn(treehub, device_registry, director, keyserver, reposerver)

enablePlugins(BuildInfoPlugin, GitVersioning, JavaAppPackaging)

buildInfoOptions += BuildInfoOption.ToMap
buildInfoOptions += BuildInfoOption.BuildTime

Compile / mainClass := Some("com.advancedtelematic.ota_lith.OtaLithBoot")

import com.typesafe.sbt.packager.docker._
import sbt.Keys._
import com.typesafe.sbt.SbtNativePackager.Docker
import DockerPlugin.autoImport._
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport._

Docker / dockerRepository  := Some("uptane")

Docker / packageName := packageName.value

dockerUpdateLatest := true

dockerAliases ++= Seq(dockerAlias.value.withTag(git.gitHeadCommit.value))

dockerBaseImage := "eclipse-temurin:17.0.3_7-jre-jammy"

dockerUpdateLatest := true

Docker / daemonUser := "daemon"

// fork := true // TODO: Not compatible with .properties ?

