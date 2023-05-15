package com.advancedtelematic.tuf.keyserver.daemon

import java.security.Security
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import com.advancedtelematic.tuf.keyserver.{Settings, VersionInfo}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import com.advancedtelematic.libats.slick.db.{BootMigrations, DatabaseSupport, SlickEncryptionConfig}
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.slick.monitoring.{DatabaseMetrics, DbHealthResource}
import com.advancedtelematic.metrics.MetricsSupport
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class KeyserverDaemon(override val globalConfig: Config, override val dbConfig: Config,
                      override val metricRegistry: MetricRegistry)
                     (implicit override val system: ActorSystem) extends BootApp
  with Settings
  with VersionInfo
  with BootMigrations
  with DatabaseSupport
  with MetricsSupport
  with DatabaseMetrics
  with PrometheusMetricsSupport
  with SlickEncryptionConfig {

  import com.advancedtelematic.libats.http.LogDirectives._
  import com.advancedtelematic.libats.http.VersionDirectives._
  import akka.http.scaladsl.server.Directives._

  import system.dispatcher

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def bind(): Future[ServerBinding] = {
    log.info("Starting key gen daemon")

    system.actorOf(KeyGeneratorLeader.props(), "keygen-leader")

    val routes: Route = (versionHeaders(nameVersion) & logResponseMetrics(projectName + "-daemon")) {
      DbHealthResource(versionMap, metricRegistry = metricRegistry).route ~ prometheusMetricsRoutes
    }

    Http().newServerAt(host, daemonPort).bindFlow(routes)
  }
}

class DaemonBootMain extends BootAppDefaultConfig with BootAppDatabaseConfig with VersionInfo {
  Security.addProvider(new BouncyCastleProvider())

  def main(args: Array[String]): Unit = {
    new KeyserverDaemon(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()
  }
}

// Compatibility with old charts
object DaemonBoot extends DaemonBootMain
object KeyGenerationDaemon extends DaemonBootMain
