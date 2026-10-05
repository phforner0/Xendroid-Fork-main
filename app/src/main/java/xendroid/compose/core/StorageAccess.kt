package xendroid.compose.core

import java.io.File
import xendroid.compose.Application
import xendroid.compose.archive.ContentLease
import xendroid.compose.saves.SaveBackupStore

/** Put the cross-process lock on internal ext4, not on emulated/FUSE storage. */
object StorageAccess {
    fun leaseDirectory(): File = File(Application.get_internal_data_dir(), "storage-lease")
    fun saveStore(): SaveBackupStore = SaveBackupStore(ContentPaths.contentRoot(), leaseDirectory())
    fun acquire(): ContentLease = ContentLease.acquire(leaseDirectory())
}
