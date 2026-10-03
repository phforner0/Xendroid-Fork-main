package xendroid.compose.ui.content

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.Emulator
import xendroid.compose.core.ContentPaths
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.core.GameMetadataSource
import xendroid.compose.core.StorageAccess
import xendroid.compose.saves.ContentTrash
import xendroid.compose.saves.TrashFullException
import xendroid.compose.saves.TrashedContent
import java.io.File

class ContentManagerViewModel(
    private val appContext: Context,
    private val metadata: GameMetadataSource,
    private val titleId: String,
) : ViewModel() {

    data class ContentEntry(
        val pkgDir: String,
        val displayName: String,
        val size: Long,
        val contentType: Int,
    )

    sealed interface ListState {
        data object Loading : ListState
        /** [trashed]: this game's packages in the trash (L12); [trashUsed] counts every game's. */
        data class Loaded(
            val dlc: List<ContentEntry>,
            val updates: List<ContentEntry>,
            val trashed: List<TrashedContent> = emptyList(),
            val trashUsed: Long = 0,
            val trashQuota: Long = ContentTrash.DEFAULT_QUOTA,
        ) : ListState
        data class Error(val message: String) : ListState
    }

    /** L12: removing moves a package to the trash; restore or delete it for good from there. */
    private val trash by lazy { ContentTrash(ContentPaths.contentRoot()) }

    private val _listState = MutableStateFlow<ListState>(ListState.Loading)
    val listState: StateFlow<ListState> = _listState.asStateFlow()

    /** Install/overwrite/progress/result reuse the shared ContentInstallState. */
    private val _state = MutableStateFlow<ContentInstallState>(ContentInstallState.Idle)
    val state: StateFlow<ContentInstallState> = _state.asStateFlow()

    /** Delete confirmation stays per-game (separate from the shared install flow). */
    sealed interface DeleteState {
        data object Idle : DeleteState
        data class Confirm(val item: ContentEntry) : DeleteState
        /** The trash has no room for [item]: empty it, or delete [item] for good. */
        data class TrashFull(val item: ContentEntry, val used: Long, val quota: Long) : DeleteState
        data class ConfirmPurge(val entry: TrashedContent) : DeleteState
        data object ConfirmEmptyTrash : DeleteState
    }

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    init { refresh() }

    fun dismiss() {
        _state.value = ContentInstallState.Idle
        _deleteState.value = DeleteState.Idle
    }

    fun refresh() = viewModelScope.launch {
        _listState.value = ListState.Loading
        _listState.value = withContext(Dispatchers.IO) {
            EmulatorRuntime.ensureLoaded()
            val emu = EmulatorRuntime.emulator
                ?: return@withContext ListState.Error(appContext.getString(xendroid.compose.R.string.pf_no_emulator))
            // Settle a removal or restore a killed process left half done, before listing.
            runCatching { StorageAccess.acquire().use { trash.recover(it) } }
            val root = ContentPaths.contentRoot().absolutePath
            try {
                val dlc = emu.list_content(root, titleId, ContentPaths.DLC_CONTENT_TYPE)
                    ?: return@withContext ListState.Error(appContext.getString(xendroid.compose.R.string.cm_read_failed))
                val updates = emu.list_content(root, titleId, ContentPaths.TU_CONTENT_TYPE)
                    ?: return@withContext ListState.Error(appContext.getString(xendroid.compose.R.string.cm_read_failed))
                val trashed = runCatching { trash.list() }.getOrDefault(emptyList())
                ListState.Loaded(
                    dlc = dlc.toEntries(ContentPaths.DLC_CONTENT_TYPE),
                    updates = updates.toEntries(ContentPaths.TU_CONTENT_TYPE),
                    trashed = trashed.filter { it.titleId.equals(titleId, ignoreCase = true) },
                    trashUsed = trashed.sumOf { it.bytes },
                    trashQuota = trash.quotaBytes,
                )
            } catch (t: RuntimeException) {
                ListState.Error(t.message ?: appContext.getString(xendroid.compose.R.string.cm_read_failed))
            }
        }
    }

    private fun Array<Emulator.ContentItem>.toEntries(contentType: Int) =
        map { ContentEntry(it.pkgDir, it.displayName ?: it.pkgDir, it.size, contentType) }
            .sortedBy { it.displayName.lowercase() }

    /** [srcPath] = absolute host path to the picked package. */
    fun install(srcPath: String) = viewModelScope.launch {
        _state.value = ContentInstallState.Busy(appContext.getString(xendroid.compose.R.string.cm_preparing))
        val pre = withContext(Dispatchers.IO) { validate(srcPath) }
        when (pre) {
            is PreCheck.Reject -> _state.value = ContentInstallState.Failed(pre.message)
            is PreCheck.Overwrite ->
                _state.value = ContentInstallState.ConfirmOverwrite(srcPath, pre.name)
            is PreCheck.Ok -> runInstall(srcPath, pre.name)
        }
    }

    /** User confirmed overwrite of an existing package dir (OD3). */
    fun confirmOverwrite(srcPath: String, name: String) =
        viewModelScope.launch { runInstall(srcPath, name) }

    fun requestDelete(item: ContentEntry) { _deleteState.value = DeleteState.Confirm(item) }

    /** L12: to the trash (restorable). A full trash asks what to do instead of deleting. */
    fun delete(item: ContentEntry) = viewModelScope.launch {
        _deleteState.value = DeleteState.Idle
        _state.value = ContentInstallState.Busy(appContext.getString(xendroid.compose.R.string.cm_trashing))
        val result = withContext(Dispatchers.IO) {
            runCatching {
                StorageAccess.acquire().use { trash.moveToTrash(it, titleId, item.contentType, item.pkgDir, item.displayName) }
            }
        }
        result.onSuccess {
            _state.value = ContentInstallState.Done(appContext.getString(xendroid.compose.R.string.cm_trashed, item.displayName))
            refresh()
        }.onFailure { e ->
            if (e is TrashFullException) {
                _state.value = ContentInstallState.Idle
                _deleteState.value = DeleteState.TrashFull(item, e.usedBytes, e.quotaBytes)
            } else {
                _state.value = ContentInstallState.Failed(
                    if (e is xendroid.compose.archive.ContentBusyException) appContext.getString(xendroid.compose.R.string.cm_close_game) else appContext.getString(xendroid.compose.R.string.cm_trash_failed, e.message))
            }
        }
    }

    fun restore(entry: TrashedContent) = viewModelScope.launch {
        _state.value = ContentInstallState.Busy(appContext.getString(xendroid.compose.R.string.cm_restoring))
        val result = withContext(Dispatchers.IO) {
            runCatching { StorageAccess.acquire().use { trash.restore(it, entry.id) } }
        }
        result.onSuccess {
            _state.value = ContentInstallState.Done(appContext.getString(xendroid.compose.R.string.cm_restored, entry.displayName))
            refresh()
        }.onFailure { _state.value = ContentInstallState.Failed(it.message ?: appContext.getString(xendroid.compose.R.string.cm_restore_failed)) }
    }

    fun requestPurge(entry: TrashedContent) { _deleteState.value = DeleteState.ConfirmPurge(entry) }
    fun requestEmptyTrash() { _deleteState.value = DeleteState.ConfirmEmptyTrash }

    /** Deletes trashed packages for good: one, or ([entry] null) the whole trash, every game's. */
    fun purge(entry: TrashedContent?) = viewModelScope.launch {
        _deleteState.value = DeleteState.Idle
        _state.value = ContentInstallState.Busy(appContext.getString(xendroid.compose.R.string.cm_deleting))
        val result = withContext(Dispatchers.IO) {
            runCatching {
                StorageAccess.acquire().use { lease ->
                    if (entry == null) trash.purgeAll(lease) else { trash.purge(lease, entry.id); 1 }
                }
            }
        }
        result.onSuccess { count ->
            _state.value = ContentInstallState.Done(if (entry == null) appContext.resources.getQuantityString(xendroid.compose.R.plurals.cm_emptied, count, count) else appContext.getString(xendroid.compose.R.string.cm_deleted, entry.displayName))
            refresh()
        }.onFailure { _state.value = ContentInstallState.Failed(it.message ?: appContext.getString(xendroid.compose.R.string.cm_delete_failed)) }
    }

    /** Skips the trash: the package's files are deleted now (asked when the trash is full). */
    fun deleteForGood(item: ContentEntry) = viewModelScope.launch {
        _deleteState.value = DeleteState.Idle
        _state.value = ContentInstallState.Busy(appContext.getString(xendroid.compose.R.string.cm_removing))
        val status = withContext(Dispatchers.IO) {
            val emu = EmulatorRuntime.emulator ?: return@withContext -1
            runCatching { StorageAccess.acquire().use { emu.delete_content(
                ContentPaths.contentRoot().absolutePath, titleId,
                item.contentType, item.pkgDir) } }.getOrDefault(0xC0000022.toInt())
        }
        if (status == 0) {
            _state.value = ContentInstallState.Done(appContext.getString(xendroid.compose.R.string.cm_removed, item.displayName))
            refresh()
        } else {
            _state.value = ContentInstallState.Failed(deleteReasonFor(status))
        }
    }

    private sealed interface PreCheck {
        data class Ok(val name: String) : PreCheck
        data class Overwrite(val name: String) : PreCheck
        data class Reject(val message: String) : PreCheck
    }

    private suspend fun validate(srcPath: String): PreCheck {
        EmulatorRuntime.ensureLoaded()
        if (!File(srcPath).isFile) return PreCheck.Reject(appContext.getString(xendroid.compose.R.string.ci_open_failed))
        val meta = metadata.readContentHeader(srcPath)
            ?: return PreCheck.Reject(appContext.getString(xendroid.compose.R.string.ci_not_package))
        if (meta.contentType != ContentPaths.DLC_CONTENT_TYPE &&
            meta.contentType != ContentPaths.TU_CONTENT_TYPE)
            return PreCheck.Reject(
                appContext.getString(xendroid.compose.R.string.cm_wrong_type, meta.contentType.toUInt().toString(16)))
        if (meta.titleId == null || !meta.titleId.equals(titleId, ignoreCase = true))
            return PreCheck.Reject(
                appContext.getString(xendroid.compose.R.string.cm_wrong_title, meta.titleId ?: "?", titleId))
        val name = meta.displayName.ifBlank { File(srcPath).name }
        storageShortfall(appContext, meta.contentSize)?.let { return PreCheck.Reject(it) }
        val pkgDir = File(ContentPaths.contentDir(titleId, meta.contentType), File(srcPath).name)
        return if (pkgDir.exists()) PreCheck.Overwrite(name) else PreCheck.Ok(name)
    }

    private suspend fun runInstall(srcPath: String, name: String) {
        val installing = appContext.getString(xendroid.compose.R.string.ci_installing)
        _state.value = ContentInstallState.Busy(installing, 0f)
        // Poll native install progress while the VFS walk blocks an IO thread (the
        // getter reads file-static atomics, so concurrent reads are safe).
        val poll = viewModelScope.launch {
            while (isActive) {
                val p = EmulatorRuntime.emulator?.installProgress() ?: 0f
                (_state.value as? ContentInstallState.Busy)
                    ?.takeIf { it.message == installing }
                    ?.let { _state.value = it.copy(progress = p.coerceIn(0f, 1f)) }
                delay(200)
            }
        }
        val status = withContext(Dispatchers.IO) {
            val emu = EmulatorRuntime.emulator ?: return@withContext -1
            runCatching { StorageAccess.acquire().use {
                emu.install_content(srcPath, ContentPaths.contentRoot().absolutePath)
            } }.getOrDefault(0xC0000022.toInt())
        }
        poll.cancel()
        if (status == 0) {
            _state.value = ContentInstallState.Done(
                appContext.getString(xendroid.compose.R.string.cm_installed, name))
            refresh()
        } else {
            _state.value = ContentInstallState.Failed(installReasonFor(appContext, status))
        }
    }

    private fun deleteReasonFor(status: Int): String = when (status) {
        -1 -> appContext.getString(xendroid.compose.R.string.pf_no_emulator)
        0xC000000D.toInt() -> appContext.getString(xendroid.compose.R.string.cm_bad_name)            // X_STATUS_INVALID_PARAMETER
        0xC0000034.toInt() -> appContext.getString(xendroid.compose.R.string.cm_already_removed)     // X_STATUS_OBJECT_NAME_NOT_FOUND
        0xC0000022.toInt() -> appContext.getString(xendroid.compose.R.string.cm_delete_files_failed) // X_STATUS_ACCESS_DENIED
        else -> appContext.getString(xendroid.compose.R.string.cm_delete_failed_code, status.toUInt().toString(16))
    }
}
