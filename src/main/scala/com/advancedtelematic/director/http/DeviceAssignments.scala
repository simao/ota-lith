package com.advancedtelematic.director.http

import java.time.Instant
import cats.implicits._
import com.advancedtelematic.director.data.AdminDataType.QueueResponse
import com.advancedtelematic.director.data.DbDataType.{Assignment, Ecu, EcuTargetId}
import com.advancedtelematic.director.data.UptaneDataType.{TargetImage, _}
import com.advancedtelematic.director.db._
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace}
import com.advancedtelematic.libats.data.{EcuIdentifier, ErrorRepresentation}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateCanceled, DeviceUpdateEvent}
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libats.http.Errors.Error

import scala.concurrent.{ExecutionContext, Future}

object DeviceAssignments {
  case class AffectedEcusResult(affected: Seq[(Ecu, EcuTargetId)], notAffected: Map[DeviceId, Map[EcuIdentifier, Error]]) {
    def addNotAffected(deviceId: DeviceId, ecuId: EcuIdentifier, error: Error) =
      copy(notAffected = this.notAffected + (deviceId -> (this.notAffected.getOrElse(deviceId, Map.empty) + (ecuId -> error))))

    def addAffected(ecu: Ecu, target: EcuTargetId) =
      copy(affected = this.affected :+ (ecu -> target))

    def notAffectedSerializable =
      notAffected.map { case (deviceId, errors) =>
        deviceId -> errors.map { case (ecuId, error) => ecuId -> ErrorRepresentation(error.code, error.getMessage) }
      }
  }

  case class AssignmentCreateResult(affected: Seq[DeviceId], notAffected: Map[DeviceId, Map[EcuIdentifier, ErrorRepresentation]])
}

class DeviceAssignments(implicit val db: Database, val ec: ExecutionContext) extends EcuRepositorySupport
  with HardwareUpdateRepositorySupport with AssignmentsRepositorySupport with EcuTargetsRepositorySupport with DeviceRepositorySupport {

  import DeviceAssignments._

  private val _log = LoggerFactory.getLogger(this.getClass)

  import scala.async.Async._

  def findDeviceAssignments(ns: Namespace, deviceId: DeviceId): Future[Vector[QueueResponse]] = async {

    val correlationIdToAssignments = await(assignmentsRepository.findBy(deviceId)).groupBy(_.correlationId)

    val deviceQueues =
      correlationIdToAssignments.map { case (correlationId, assignments) =>
        val images = assignments.map { assignment =>
          ecuTargetsRepository.find(ns, assignment.ecuTargetId).map { target =>
            assignment.ecuId -> TargetImage(Image(target.filename, FileInfo(Hashes(target.sha256), target.length)), target.uri, assignment.createdAt)
          }
        }.toList.sequence

        val queue = images.map(_.toMap).map { images =>
          val inFlight = correlationIdToAssignments.get(correlationId).exists(_.exists(_.inFlight))
          QueueResponse(correlationId, images, inFlight = inFlight)
        }

        queue
      }

    await(Future.sequence(deviceQueues)).toVector
  }

  def findAffectedDevices(ns: Namespace, deviceIds: Seq[DeviceId], mtuId: UpdateId): Future[Seq[DeviceId]] = {
    findAffectedEcus(ns, deviceIds, mtuId).map { _.affected.map(_._1.deviceId) }
  }

  import cats.syntax.option._

  private def findAffectedEcus(ns: Namespace, devices: Seq[DeviceId], mtuId: UpdateId) = async {
    val hardwareUpdates = await(hardwareUpdateRepository.findBy(ns, mtuId))

    val allTargetIds = hardwareUpdates.values.flatMap(v => List(v.toTarget.some, v.fromTarget).flatten)
    val allTargets = await(ecuTargetsRepository.findAll(ns, allTargetIds.toSeq))

    val ecusWithCompatibleHardware = await(ecuRepository.findEcuWithTargets(devices.toSet, hardwareUpdates.keys.toSet))

    val devicesWithIncompatibleHardware = devices.toSet -- ecusWithCompatibleHardware.map(_._1.deviceId).toSet
    val devicePrimaries = await(ecuRepository.findDevicePrimaryIds(ns, devicesWithIncompatibleHardware))

    val unaffectedDueToHardware = devicesWithIncompatibleHardware.foldLeft(AffectedEcusResult(Seq.empty, Map.empty)) { case (acc, deviceId) =>
      val error = Errors.DeviceNoCompatibleHardware(deviceId, mtuId)
      _log.info(error.getMessage)
      val primaryEcuId = devicePrimaries.getOrElse(deviceId, EcuIdentifier("unknown"))
      acc.addNotAffected(deviceId, primaryEcuId, error)
    }

    val ecus = ecusWithCompatibleHardware.foldLeft(unaffectedDueToHardware) { case (acc, (ecu, installedTarget)) =>

        val hwUpdate = hardwareUpdates(ecu.hardwareId)
        val updateFrom = hwUpdate.fromTarget.flatMap(allTargets.get)
        val updateTo = allTargets(hwUpdate.toTarget)

        if (hwUpdate.fromTarget.isEmpty || installedTarget.zip(updateFrom).exists { case (a, b) => a matches b }) {
          if(installedTarget.exists(_.matches(updateTo))) {
            val error = Errors.InstalledTargetIsUpdate(ecu.deviceId, ecu.ecuSerial, hwUpdate)
            _log.info(error.getMessage)
            acc.addNotAffected(ecu.deviceId, ecu.ecuSerial, error)
          } else {
            _log.info(s"${ecu.deviceId}/${ecu.ecuSerial} affected for $hwUpdate")
            acc.addAffected(ecu, hwUpdate.toTarget)
          }
        } else {
          val error = Errors.NotAffectedByMtu(ecu.deviceId, ecu.ecuSerial, mtuId)
          _log.info(error.getMessage)
          acc.addNotAffected(ecu.deviceId, ecu.ecuSerial, error)
        }
      }

    val ecuIds = ecus.affected.map { case (ecu, _) => ecu.deviceId -> ecu.ecuSerial }.toSet
    val ecusWithAssignments = await(assignmentsRepository.withAssignments(ecuIds))

    ecus.affected.foldLeft(AffectedEcusResult(Seq.empty, ecus.notAffected)) {
      case (acc, (ecu, _)) if ecusWithAssignments.contains(ecu.deviceId -> ecu.ecuSerial) =>
        val error = Errors.NotAffectedRunningAssignment(ecu.deviceId, ecu.ecuSerial)
        _log.info(error.getMessage)
        acc.addNotAffected(ecu.deviceId, ecu.ecuSerial, error)
      case (acc, (ecu, target)) =>
        acc.addAffected(ecu, target)
    }
  }

  def createForDevice(ns: Namespace, correlationId: CorrelationId, deviceId: DeviceId, mtuId: UpdateId): Future[DeviceId] = {
    createForDevices(ns, correlationId, List(deviceId), mtuId).map(_.affected.head) // TODO: This HEAD is problematic
  }

  def createForDevices(ns: Namespace, correlationId: CorrelationId, devices: Seq[DeviceId], mtuId: UpdateId): Future[AssignmentCreateResult] = async {
    val ecus = await(findAffectedEcus(ns, devices, mtuId))

    _log.debug(s"$ns $correlationId $devices $mtuId")

    if(ecus.affected.isEmpty) {
      _log.warn(s"No devices affected for this assignment: $ns, $correlationId, $devices, $mtuId")
      AssignmentCreateResult(Seq.empty, ecus.notAffectedSerializable)
    } else {
      val assignments = ecus.affected.foldLeft(List.empty[Assignment]) { case (acc, (ecu, toTargetId)) =>
        Assignment(ns, ecu.deviceId, ecu.ecuSerial, toTargetId, correlationId, inFlight = false, createdAt = Instant.now) :: acc
      }

      await(assignmentsRepository.persistMany(deviceRepository)(assignments))

      AssignmentCreateResult(assignments.map(_.deviceId), ecus.notAffectedSerializable)
    }
  }

  def cancel(namespace: Namespace, devices: Seq[DeviceId])(implicit messageBusPublisher: MessageBusPublisher): Future[Seq[Assignment]] = {
    assignmentsRepository.processCancellation(namespace, devices).flatMap { canceledAssignments =>
      Future.traverse(canceledAssignments) { canceledAssignment =>
        val ev: DeviceUpdateEvent =
          DeviceUpdateCanceled(namespace, Instant.now, canceledAssignment.correlationId, canceledAssignment.deviceId)
        messageBusPublisher.publish(ev).map(_ => canceledAssignment)
      }
    }
  }
}
