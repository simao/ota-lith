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

mainClass in Compile := Some("com.advancedtelematic.ota_lith.OtaLithBoot")

import com.typesafe.sbt.packager.docker._
import sbt.Keys._
import com.typesafe.sbt.SbtNativePackager.Docker
import DockerPlugin.autoImport._
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport._

dockerRepository in Docker := Some("uptane")

packageName in Docker := packageName.value

dockerUpdateLatest := true

dockerAliases ++= Seq(dockerAlias.value.withTag(git.gitHeadCommit.value))

defaultLinuxInstallLocation in Docker := s"/opt/${moduleName.value}"

dockerCommands := Seq(
  Cmd("FROM", "advancedtelematic/alpine-jre:adoptopenjdk-jre8u262-b10"),
  ExecCmd("RUN", "mkdir", "-p", s"/var/log/${moduleName.value}"),
  Cmd("ADD", "opt /opt"),
  Cmd("WORKDIR", s"/opt/${moduleName.value}"),
  ExecCmd("ENTRYPOINT", s"/opt/${moduleName.value}/bin/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /opt/${moduleName.value}"),
  Cmd("RUN", s"mkdir /var/lib/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /var/lib/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /var/log/${moduleName.value}"),
  Cmd("USER", "daemon")
)

// fork := true // TODO: Not compatible with .properties ?
