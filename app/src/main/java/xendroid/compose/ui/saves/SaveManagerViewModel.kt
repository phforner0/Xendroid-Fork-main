package xendroid.compose.ui.saves

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.core.StorageAccess
import xendroid.compose.saves.PreparedSaveRestore
import xendroid.compose.saves.SaveProfile
import xendroid.compose.saves.BackupSync
import xendroid.compose.saves.ProfileTrash
import xendroid.compose.saves.SaveEntry
import xendroid.compose.saves.SaveHeaders
import xendroid.compose.core.ContentPaths
import xendroid.compose.core.EmulatorRuntime

class SaveManagerViewModel(context: Context, val titleId: String) : ViewModel() {
    private val context = context.applicationContext
    private val store get() = StorageAccess.saveStore()
    private val _profiles = MutableStateFlow<List<SaveProfile>>(emptyList())
    val profiles = _profiles.asStateFlow()
    /** Who a XUID with saves is: its gamertag and avatar, or a profile in the trash (no name kept). */
    data class Owner(val gamertag: String?, val hasAvatar: Boolean, val inTrash: Boolean)
    private val _owners = MutableStateFlow<Map<String, Owner>>(emptyMap())
    val owners = _owners.asStateFlow()
    /** Each profile's saves of this game, by the names the game gave them. */
    private val _saves = MutableStateFlow<Map<String, List<SaveEntry>>>(emptyMap())
    val saves = _saves.asStateFlow()
    sealed interface Operation {
        data object Idle : Operation
        data class Busy(val message: String) : Operation
        data class Review(val backup: PreparedSaveRestore) : Operation
        data class Result(val message: String, val success: Boolean) : Operation
    }
    private val _operation = MutableStateFlow<Operation>(Operation.Idle)
    val operation = _operation.asStateFlow()
    private var prepared: PreparedSaveRestore? = null

    init { refresh() }
    fun refresh() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { store.profiles(titleId) } }
            .onSuccess { list ->
                _profiles.value = list
                _saves.value = withContext(Dispatchers.IO) {
                    val root = ContentPaths.contentRoot()
                    list.associate { it.xuid to runCatching { SaveHeaders.list(root, it.xuid, titleId) }.getOrDefault(emptyList()) }
                }
                _owners.value = withContext(Dispatchers.IO) { runCatching { owners() }.getOrDefault(emptyMap()) }
            }.onFailure { error(it) }
    }

    private fun owners(): Map<String, Owner> {
        val root = ContentPaths.contentRoot()
        EmulatorRuntime.ensureLoaded()
        val listed = runCatching { EmulatorRuntime.emulator?.list_profiles(root.absolutePath) }.getOrNull().orEmpty()
            .associate { it.xuid.uppercase() to Owner(it.gamertag?.ifBlank { null }, it.hasAvatar, inTrash = false) }
        val trashed = runCatching { ProfileTrash(root).list() }.getOrDefault(emptyList())
            .map { it.xuid.uppercase() }.filter { it !in listed }.associateWith { Owner(null, false, inTrash = true) }
        return trashed + listed
    }
    fun synchronize() {
        if (_operation.value is Operation.Busy) return
        _operation.value = Operation.Busy(context.getString(xendroid.compose.R.string.sv_publishing))
        viewModelScope.launch {
            try {
                val name = BackupSync.publish(context, titleId)
                _operation.value = Operation.Result(context.getString(xendroid.compose.R.string.sv_published, name), true)
            } catch (e: Exception) { error(e) }
        }
    }
    private fun error(e: Throwable) {
        if (e is CancellationException) throw e
        _operation.value = Operation.Result(e.message ?: context.getString(xendroid.compose.R.string.sv_failed), false)
    }
    fun dismiss() {
        val preview = prepared
        prepared = null
        if (preview != null) viewModelScope.launch(Dispatchers.IO) { store.discard(preview) }
        _operation.value = Operation.Idle
    }

    fun export(uri: Uri, xuids: List<String>, includeProfiles: Boolean) {
        if (_operation.value is Operation.Busy) return
        _operation.value = Operation.Busy(context.getString(xendroid.compose.R.string.sv_backing_up))
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    val temporary = File.createTempFile("save-backup-", ".zip", context.cacheDir)
                    try {
                        store.export(titleId, xuids, temporary, includeProfiles) { coroutine.ensureActive() }
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            temporary.inputStream().use { it.copyTo(output) }
                        } ?: error(context.getString(xendroid.compose.R.string.sv_write_failed))
                    } finally { temporary.delete() }
                }
                _operation.value = Operation.Result(context.getString(xendroid.compose.R.string.sv_backed_up), true)
            } catch (e: Exception) { error(e) }
        }
    }

    fun import(uri: Uri) {
        if (_operation.value is Operation.Busy) return
        _operation.value = Operation.Busy(context.getString(xendroid.compose.R.string.sv_validating))
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    val temporary = File.createTempFile("save-import-", ".zip", context.cacheDir)
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytes = 0L
                            while (true) {
                                coroutine.ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                bytes += n
                                require(bytes <= 512L * 1024 * 1024) { "Backup archive too large" }
                                output.write(buffer, 0, n)
                            }
                        } } ?: error(context.getString(xendroid.compose.R.string.sv_read_failed))
                        store.prepare(temporary, titleId) { coroutine.ensureActive() }
                    } finally { temporary.delete() }
                }
                prepared = result
                _operation.value = Operation.Review(result)
            } catch (e: Exception) { error(e) }
        }
    }

    fun restore(overwriteProfiles: Boolean) {
        val preview = prepared ?: return
        if (_operation.value is Operation.Busy) return
        _operation.value = Operation.Busy(context.getString(xendroid.compose.R.string.sv_restoring))
        viewModelScope.launch {
            try {
                val backup = withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    store.restore(preview, overwrite = true, overwriteProfiles = overwriteProfiles) { coroutine.ensureActive() }
                }
                prepared = null
                _operation.value = Operation.Result(context.getString(xendroid.compose.R.string.sv_restored, backup.name), true)
                refresh()
            } catch (e: Exception) { prepared = null; error(e) }
        }
    }

    override fun onCleared() {
        val preview = prepared ?: return
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanup.launch { try { store.discard(preview) } finally { cleanup.cancel() } }
    }
}
