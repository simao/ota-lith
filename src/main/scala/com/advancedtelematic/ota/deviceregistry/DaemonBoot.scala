package com.advancedtelematic.ota.deviceregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.http.VersionDirectives.versionHeaders
import com.advancedtelematic.libats.messaging.metrics.MonitoredBusListenerSupport
import com.advancedtelematic.libats.messaging.{MessageBus, MessageListenerSupport}
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeleteDeviceRequest, DeviceEventMessage, DeviceSeen, DeviceUpdateEvent, EcuReplacement}
import com.advancedtelematic.libats.slick.db.{BootMigrations, CheckMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.MetricsSupport
import com.advancedtelematic.ota.deviceregistry.daemon.{DeleteDeviceListener, DeviceEventListener, DeviceSeenListener, DeviceUpdateEventListener, EcuReplacementListener}
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class DeviceRegistryDaemon(override val globalConfig: Config,
                           override val dbConfig: Config,
                           override val metricRegistry: MetricRegistry)
                          (implicit override val system: ActorSystem) extends BootApp
  with DatabaseSupport
  with BootMigrations
  with MessageListenerSupport
  with MonitoredBusListenerSupport
  with MetricsSupport
  with PrometheusMetricsSupport
  with Settings
  with VersionInfo {

  lazy val messageBus = MessageBus.publisher(system, globalConfig)

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  import system.dispatcher

  def bind(): Future[ServerBinding] = {

    log.info("Starting daemon service")

    startMonitoredListener[DeviceSeen](new DeviceSeenListener(messageBus))
    startMonitoredListener[DeviceEventMessage](new DeviceEventListener)
    startMonitoredListener[DeleteDeviceRequest](new DeleteDeviceListener)
    startMonitoredListener[DeviceUpdateEvent](new DeviceUpdateEventListener(messageBus))
    startMonitoredListener[EcuReplacement](new EcuReplacementListener)

    val routes: Route = versionHeaders(version) {
      DbHealthResource(versionMap).route
    } ~ prometheusMetricsRoutes

    Http().newServerAt(host, daemonPort).bindFlow(routes)
  }
}

object DaemonBoot extends BootAppDefaultConfig with BootAppDatabaseConfig with VersionInfo {
  def main(args: Array[String]): Unit = {
    new DeviceRegistryDaemon(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()
  }
}