package com.advancedtelematic.tuf.reposerver.data

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libtuf.data.ClientDataType._
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, TargetFilename}

import java.time.Instant

object RepoDataType {
  object StorageMethod {
    sealed trait StorageMethod
    object Managed extends StorageMethod
    object Unmanaged extends StorageMethod
    object CliManaged extends StorageMethod
  }

  import StorageMethod._

  case class TargetItem(repoId: RepoId, filename: TargetFilename, uri: Option[Uri], checksum: Checksum, length: Long, custom: Option[TargetCustom] = None, storageMethod: StorageMethod = Managed)

  case class AddDelegationFromRemoteRequest(uri: Uri, remoteHeaders: Option[Map[String, String]] = None, friendlyName: Option[DelegationFriendlyName] = None)

  case class DelegationInfo(lastFetched: Option[Instant], remoteUri: Option[Uri], friendlyName: Option[DelegationFriendlyName]=None)
}
