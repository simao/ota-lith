import com.jsuereth.sbtpgp.SbtPgp.autoImport.usePgpKeyHex
import sbt.Keys._
import sbt._
import xerial.sbt.Sonatype.GitHubHosting
import xerial.sbt.Sonatype.autoImport._

import java.net.URI

object Publish {
  private def readSettings(envKey: String, propKey: Option[String] = None): String = {
    sys.env
      .get(envKey)
      .orElse(propKey.flatMap(sys.props.get(_)))
      .getOrElse("")
  }

  lazy val repoHost = URI.create(repoUrl).getHost

  lazy val repoUser = readSettings("PUBLISH_USER")

  lazy val repoPassword = readSettings("PUBLISH_PASSWORD")

  lazy val repoUrl = readSettings("PUBLISH_URL")

  lazy val repoRealm = readSettings("PUBLISH_REALM")

  lazy val settings = Seq(
    usePgpKeyHex("6ED5E5ABE9BF80F173343B98FFA246A21356D296"),
    isSnapshot := version.value.trim.endsWith("SNAPSHOT"),
    pomIncludeRepository := { _ => false },
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    publishTo := sonatypePublishToBundle.value,
    publishMavenStyle := true,
    sonatypeProjectHosting := Some(GitHubHosting("uptane", "ota-tuf", "releases@uptane.github.io")),
    credentials += Credentials(repoRealm, repoHost, repoUser, repoPassword),
    publishTo := {
      if (repoUrl.isEmpty) {
        sonatypePublishToBundle.value
      } else {
        if (isSnapshot.value)
          Some("snapshots" at repoUrl)
        else
          Some("releases" at repoUrl)
      }
    }
  )

  lazy val disable = Seq(
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeProfileName := "io.github.uptane",
    publish / skip := true,
    publishArtifact := true,
    publish := (()),
    publishTo := None,
    publishLocal := (())
  )
}
