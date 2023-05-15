package com.advancedtelematic.director.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable

import java.time.Instant
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import com.advancedtelematic.director.data.AdminDataType.AssignUpdateRequest
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs._
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateAssigned, DeviceUpdateEvent}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import slick.jdbc.MySQLProfile.api.Database

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import com.advancedtelematic.director.http.DeviceAssignments.AssignmentCreateResult

class AssignmentsResource(extractNamespace: Directive1[Namespace])
                         (implicit val db: Database, val ec: ExecutionContext, messageBusPublisher: MessageBusPublisher) {

  import Directives._

  val deviceAssignments = new DeviceAssignments()

  private def createAssignments(ns: Namespace, req: AssignUpdateRequest): Future[AssignmentCreateResult] = {
    val assignments = deviceAssignments.createForDevices(ns, req.correlationId, req.devices, req.mtuId)

    assignments.flatMap { createResult =>
      createResult.affected.toList.traverse_ { deviceId =>
        val msg: DeviceUpdateEvent = DeviceUpdateAssigned(ns, Instant.now(), req.correlationId, deviceId)
        messageBusPublisher.publishSafe(msg)
      }.map(_ => createResult)
    }
  }

  private implicit val updateIdUnmarshaller = UpdateId.unmarshaller
  private implicit val deviceIdUnmarshaller = DeviceId.unmarshaller

  val route = extractNamespace { ns =>
    pathPrefix("assignments") {
      (path("devices") & parameter('mtuId.as[UpdateId]) & parameter('ids.as(CsvSeq[DeviceId]))) { (mtuId, deviceIds) =>
        val f = deviceAssignments.findAffectedDevices(ns, deviceIds, mtuId)
        complete(f)
      } ~
      pathEnd {
        post {
          entity(as[AssignUpdateRequest]) { req =>
            if(req.dryRun.contains(true)) { // Legacy API
              val f = deviceAssignments.findAffectedDevices(ns, req.devices, req.mtuId)
              complete(f)
            } else {
              val f: Future[ToResponseMarshallable] = createAssignments(ns, req).map {
                case result if result.affected.nonEmpty => StatusCodes.Created -> result
                case result if result.notAffected.nonEmpty => StatusCodes.BadRequest -> result
                case result => StatusCodes.OK -> result
              }

              complete(f)
            }
          }
        } ~
        patch {
          entity(as[Seq[DeviceId]]) { devices =>
            val a = deviceAssignments.cancel(ns, devices)
            complete(a.map(_.map(_.deviceId)))
          }
        }
      } ~
      path(DeviceId.Path) { deviceId =>
        get { //  This should be replacing /queue in /admin
          val f = deviceAssignments.findDeviceAssignments(ns, deviceId)
          complete(f)
        }
      }
    }
  }
}
