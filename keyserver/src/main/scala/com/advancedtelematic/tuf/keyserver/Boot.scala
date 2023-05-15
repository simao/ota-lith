package com.advancedtelematic.tuf.keyserver

import java.security.Security
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.libats.http.LogDirectives._
import com.advancedtelematic.libats.http.VersionDirectives._
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.slick.db.{BootMigrations, CheckMigrations, DatabaseSupport, SlickEncryptionConfig}
import com.advancedtelematic.libats.slick.monitoring.DatabaseMetrics
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.{AkkaHttpConnectionMetrics, AkkaHttpRequestMetrics, MetricsSupport}
import com.advancedtelematic.tuf.keyserver.http.TufKeyserverRoutes
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.{Config, ConfigFactory}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory

import scala.concurrent.Future

trait Settings {
  private lazy val _config = ConfigFactory.load().getConfig("ats.keyserver")

  val host = _config.getString("http.server.host")
  val port = _config.getInt("http.server.port")
  val daemonPort = if(_config.hasPath("http.server.daemon-port")) _config.getInt("http.server.daemon-port") else port
}

class KeyserverBoot(override val globalConfig: Config,
                    override val dbConfig: Config,
                    override val metricRegistry: MetricRegistry)
                   (implicit override val system: ActorSystem) extends BootApp
  with Directives
  with Settings
  with VersionInfo
  with MetricsSupport
  with DatabaseSupport
  with DatabaseMetrics
  with BootMigrations
  with SlickEncryptionConfig
  with AkkaHttpRequestMetrics
  with AkkaHttpConnectionMetrics
  with PrometheusMetricsSupport {

  import system.dispatcher

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def bind(): Future[ServerBinding] = {
    log.info(s"Starting ${nameVersion} on http://$host:$port")

    val tracing = Tracing.fromConfig(globalConfig, projectName)

    val routes: Route =
      (versionHeaders(nameVersion) & requestMetrics(metricRegistry) & logResponseMetrics(projectName)) {
        tracing.traceRequests { _ =>
          new TufKeyserverRoutes(metricsRoutes = prometheusMetricsRoutes, metricRegistry = metricRegistry).routes
        }
      }

    Http().newServerAt(host, port).bindFlow(withConnectionMetrics(routes, metricRegistry))
  }
}

object Boot extends BootAppDefaultConfig with BootAppDatabaseConfig with VersionInfo {
  Security.addProvider(new BouncyCastleProvider)

  def main(args: Array[String]): Unit = {
    new KeyserverBoot(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()
  }
}
