package com.advancedtelematic.ota.deviceregistry.db

import cats.syntax.either._
import cats.syntax.option._
import cats.syntax.show._
import com.advancedtelematic.libats.data.DataType.{CampaignId, CorrelationId}
import com.advancedtelematic.libats.messaging_datatype.DataType.{Event, EventType}
import com.advancedtelematic.ota.deviceregistry.data.DataType.{IndexedEvent, _}
import java.util.UUID

object EventIndex {
  type EventIndexResult = Either[String, IndexedEvent]

  private def parseEventOfTypeWithCorrelationId(event: Event, indexedEventType: IndexedEventType.Value): EventIndexResult = {
    event.payload.hcursor.downField("correlationId").as[CorrelationId]
      .leftMap(err => s"Could not parse payload for event ${event.show}: $err")
      .map { correlationId =>
        IndexedEvent(event.deviceUuid, event.eventId, indexedEventType, correlationId.some)
      }
  }

  private def parseEventOfTypeWithCampaignId(event: Event, indexedEventType: IndexedEventType.Value): EventIndexResult = {
    event.payload.hcursor.downField("campaignId").as[UUID]
      .leftMap(err => s"Could not parse payload for event ${event.show}: $err")
      .map { campaignId =>
        IndexedEvent(event.deviceUuid, event.eventId, indexedEventType, CampaignId(campaignId).some)
      }
  }

  private def parseEventOfType(event: Event, indexedEventType: IndexedEventType.Value): EventIndexResult =
    IndexedEvent(event.deviceUuid, event.eventId, indexedEventType, None).asRight

  def index(event: Event): EventIndexResult = event.eventType match {
    case EventType("DownloadComplete", 0) =>
      parseEventOfType(event, IndexedEventType.DownloadComplete)
    case EventType("InstallationComplete", 0) =>
      parseEventOfTypeWithCorrelationId(event, IndexedEventType.InstallationComplete)
    case EventType("EcuDownloadStarted", 0) =>
      parseEventOfTypeWithCorrelationId(event, IndexedEventType.EcuDownloadStarted)
    case EventType("EcuDownloadCompleted", 0) =>
      parseEventOfTypeWithCorrelationId(event, IndexedEventType.EcuDownloadCompleted)
    case EventType("EcuInstallationStarted", 0) =>
      parseEventOfTypeWithCorrelationId(event, IndexedEventType.EcuInstallationStarted)
    case EventType("EcuInstallationApplied", 0) =>
      parseEventOfTypeWithCorrelationId(event, IndexedEventType.EcuInstallationApplied)
    case EventType("EcuInstallationCompleted", 0) =>
      parseEventOfTypeWithCorrelationId(event, IndexedEventType.EcuInstallationCompleted)
    case EventType("DevicePaused", 0) =>
      parseEventOfTypeWithCorrelationId(event, IndexedEventType.DevicePaused)
    case EventType("DeviceResumed", 0) =>
      parseEventOfTypeWithCorrelationId(event, IndexedEventType.DeviceResumed)
    case EventType("campaign_accepted", 0) =>
      parseEventOfTypeWithCampaignId(event, IndexedEventType.CampaignAccepted)
    case EventType("campaign_declined", 0) =>
      parseEventOfTypeWithCampaignId(event, IndexedEventType.CampaignDeclined)
    case EventType("campaign_postponed", 0) =>
      parseEventOfTypeWithCampaignId(event, IndexedEventType.CampaignPostponed)
    case eventType =>
      s"Unknown event type $eventType".asLeft
  }
}
