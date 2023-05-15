package com.advancedtelematic.ota.deviceregistry.data

import java.time.Instant

import com.advancedtelematic.libats.data.DataType.{CampaignId, CorrelationId, MultiTargetUpdateId, Namespace, ResultCode, ResultDescription}
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.data.EcuIdentifier.validatedEcuIdentifier
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuInstallationReport, InstallationResult}
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateCompleted, EcuAndHardwareId, EcuReplaced, EcuReplacement, EcuReplacementFailed}
import org.scalacheck.Gen

import scala.util.{Success, Try}

trait InstallationReportGenerators extends DeviceGenerators {

  val genCorrelationId: Gen[CorrelationId] =
    Gen.uuid.flatMap(uuid => Gen.oneOf(CampaignId(uuid), MultiTargetUpdateId(uuid)))

  val genEcuIdentifier: Gen[EcuIdentifier] =
    Gen.listOfN(64, Gen.alphaNumChar).map(_.mkString("")).map(validatedEcuIdentifier.from(_).right.get)

  private def genInstallationResult(resultCode: ResultCode, resultDescription: Option[ResultDescription] = None): Gen[InstallationResult] = {
    val success = Try(resultCode.value.toInt == 0).orElse(Success(false))
    val description = resultDescription.getOrElse(Gen.alphaStr.map(ResultDescription).sample.get)
    InstallationResult(success.get, resultCode, description)
  }

  private def genEcuReports(resultCode: ResultCode, n: Int = 1): Gen[Map[EcuIdentifier, EcuInstallationReport]] =
    Gen.listOfN(n, genEcuReportTuple(resultCode)).map(_.toMap)

  private def genEcuReportTuple(resultCode: ResultCode): Gen[(EcuIdentifier, EcuInstallationReport)] =
    for {
      ecuId  <- genEcuIdentifier
      report <- genEcuInstallationReport(resultCode)
    } yield ecuId -> report

  private def genEcuInstallationReport(resultCode: ResultCode): Gen[EcuInstallationReport] =
    for {
      result <- genInstallationResult(resultCode)
      target <- Gen.listOfN(1, Gen.alphaStr)
    } yield EcuInstallationReport(result, target, None)

  def genDeviceUpdateCompleted(correlationId: CorrelationId,
                               resultCode: ResultCode,
                               deviceId: DeviceId = genDeviceUUID.sample.get,
                               resultDescription: Option[ResultDescription] = None,
                               receivedAt: Instant = Instant.ofEpochMilli(0)): Gen[DeviceUpdateCompleted] =
    for {
      result     <- genInstallationResult(resultCode, resultDescription)
      ecuReports <- genEcuReports(resultCode)
      namespace = Namespace("default")
    } yield DeviceUpdateCompleted(namespace, receivedAt, correlationId, deviceId, result, ecuReports, rawReport = None)

  private def genEcuAndHardwareId: Gen[EcuAndHardwareId] =
    for {
      ecuId <- genEcuIdentifier
      hwdId <- Gen.listOfN(100, Gen.alphaNumChar).map(_.mkString(""))
    } yield EcuAndHardwareId(ecuId, hwdId)

  private def genEcuReplaced(deviceId: DeviceId, eventTime: Instant): Gen[EcuReplaced] =
    for {
      former <- genEcuAndHardwareId
      current <- genEcuAndHardwareId
    } yield EcuReplaced(deviceId, former, current, eventTime)

  def genEcuReplacement(deviceId: DeviceId, eventTime: Instant, success: Boolean): Gen[EcuReplacement] = {
    if (success) genEcuReplaced(deviceId, eventTime)
    else Gen.const(EcuReplacementFailed(deviceId, eventTime))
  }
}
