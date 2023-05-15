package com.advancedtelematic.ota_lith

import akka.actor.ActorSystem
import com.advancedtelematic.director.DirectorBoot
import com.advancedtelematic.director.daemon.DirectorDaemonBoot
import com.advancedtelematic.ota.deviceregistry.{DeviceRegistryBoot, DeviceRegistryDaemon}
import com.advancedtelematic.treehub.TreehubBoot
import com.advancedtelematic.tuf.keyserver.KeyserverBoot
import com.advancedtelematic.tuf.reposerver.ReposerverBoot
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.security.Security

object OtaLithBoot extends App {
  private lazy val appConfig = ConfigFactory.load()

  Security.addProvider(new BouncyCastleProvider)

  val reposerverDbConfig = appConfig.getConfig("ats.reposerver.database")
  val reposerverBind = new ReposerverBoot(appConfig, reposerverDbConfig, new MetricRegistry)(ActorSystem("reposerver-actor-system")).bind()

  val keyserverDbConfig = appConfig.getConfig("ats.keyserver.database")
  val keyserverBind = new KeyserverBoot(appConfig, keyserverDbConfig, new MetricRegistry)(ActorSystem("keyserver-actor-system")).bind()

  val directorDbConfig = appConfig.getConfig("ats.director-v2.database")
  val directorBind = new DirectorBoot(appConfig, directorDbConfig, new MetricRegistry)(ActorSystem("director-actor-system")).bind()

  val treehubDbConfig = appConfig.getConfig("ats.treehub.database")
  val treehubBind = new TreehubBoot(appConfig, treehubDbConfig, new MetricRegistry)(ActorSystem("treehub-actor-system")).bind()

  val deviceRegistryDbConfig = appConfig.getConfig("ats.device-registry.database")
  val deviceRegistryBind = new DeviceRegistryBoot(appConfig, deviceRegistryDbConfig, new MetricRegistry)(ActorSystem("deviceregistry-actor-system")).bind()
}

object OtaLithDaemonBoot extends App {
  private lazy val appConfig = ConfigFactory.load()

  Security.addProvider(new BouncyCastleProvider)

  val directorDbConfig = appConfig.getConfig("ats.director-v2.database")
  val directorDaemonBind = new DirectorDaemonBoot(appConfig, directorDbConfig, new MetricRegistry)(ActorSystem("director-actor-system")).bind()

  val deviceRegistryDbConfig = appConfig.getConfig("ats.device-registry.database")
  val deviceRegistryDaemonBind = new DeviceRegistryDaemon(appConfig, deviceRegistryDbConfig, new MetricRegistry)(ActorSystem("deviceregistry-actor-system")).bind()
}
