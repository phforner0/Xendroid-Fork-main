package xendroid.compose.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.archive.ContentBusyException
import xendroid.compose.core.ContentPaths
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.core.Gamertag
import xendroid.compose.core.ProfilePaths
import xendroid.compose.core.StorageAccess
import xendroid.compose.data.ProfileSlots
import xendroid.compose.saves.ProfileContentSummary
import xendroid.compose.saves.ProfileTrash
import xendroid.compose.saves.TrashedProfile
import xendroid.compose.settings.ConfigStore
import java.io.ByteArrayOutputStream
import java.io.File

class ProfileManagerViewModel(
    private val appContext: Context,
    private val configStore: ConfigStore,
) : ViewModel() {

    data class ProfileEntry(
        val xuid: String,
        val gamertag: String,
        val language: Int,
        val country: Int,
        val hasAvatar: Boolean,
        val isActive: Boolean,
    )

    sealed interface ListState {
        data object Loading : ListState
        /** [slots]: the profile (XUID) each player P1–P4 signs in with; null = nobody. */
        data class Loaded(val profiles: List<ProfileEntry>, val slots: List<String?> = List(ProfileSlots.COUNT) { null }) : ListState
        data class Error(val message: String) : ListState
    }

    private val _listState = MutableStateFlow<ListState>(ListState.Loading)
    val listState: StateFlow<ListState> = _listState.asStateFlow()

    /** Profiles removed from the list but still restorable (with every save). */
    private val _trash = MutableStateFlow<List<TrashedProfile>>(emptyList())
    val trash: StateFlow<List<TrashedProfile>> = _trash.asStateFlow()

    sealed interface OpState {
        data object Idle : OpState
        data class Busy(val message: String) : OpState
        data class Done(val message: String) : OpState
        data class Failed(val message: String) : OpState
        /** What a delete would take away, shown before the user confirms it. */
        data class ConfirmDelete(val entry: ProfileEntry, val summary: ProfileContentSummary) : OpState
    }

    private val _opState = MutableStateFlow<OpState>(OpState.Idle)
    val opState: StateFlow<OpState> = _opState.asStateFlow()

    private val profileTrash get() = ProfileTrash(ContentPaths.contentRoot())

    init { refresh() }

    fun dismiss() { _opState.value = OpState.Idle }

    fun refresh() = viewModelScope.launch {
        _listState.value = ListState.Loading
        _listState.value = withContext(Dispatchers.IO) {
            EmulatorRuntime.ensureLoaded()
            val emu = EmulatorRuntime.emulator
                ?: return@withContext ListState.Error("Emulator not loaded.")
            val root = ContentPaths.contentRoot().absolutePath
            try {
                val listed = emu.list_profiles(root)
                // U11: a player slot naming a profile that is gone (or twice) signs in nobody.
                val before = readSlots()
                val slots = if (listed == null) before else ProfileSlots.reconcile(before, listed.map { it.xuid })
                runCatching { writeSlots(before, slots) }.onFailure { Log.w(TAG, "Clearing stale player slots failed", it) }
                val active = slots[0].orEmpty()
                val profiles = listed?.map {
                    ProfileEntry(
                        xuid = it.xuid,
                        gamertag = it.gamertag ?: "",
                        language = it.language,
                        country = it.country,
                        hasAvatar = it.hasAvatar,
                        isActive = it.xuid.equals(active, ignoreCase = true),
                    )
                }?.sortedBy { it.gamertag.lowercase() }
                    ?: return@withContext ListState.Error("Couldn't read profiles.")
                ListState.Loaded(profiles, slots)
            } catch (t: RuntimeException) {
                ListState.Error(t.message ?: "Couldn't read profiles.")
            }
        }
        _trash.value = withContext(Dispatchers.IO) { runCatching { profileTrash.list() }.getOrDefault(emptyList()) }
    }

    fun create(gamertag: String, language: Int, country: Int, avatarUri: Uri?) = viewModelScope.launch {
        if (!Gamertag.isValid(gamertag)) {
            _opState.value = OpState.Failed("Enter a valid gamertag (1-15 characters).")
            return@launch
        }
        _opState.value = OpState.Busy("Creating profile…")
        _opState.value = withContext(Dispatchers.IO) {
            try {
                // An unusable image fails here, before any profile file exists.
                val tiles = avatarUri?.let(::decodeAvatar)
                EmulatorRuntime.ensureLoaded()
                val emu = EmulatorRuntime.emulator ?: return@withContext OpState.Failed("Emulator not loaded.")
                StorageAccess.acquire().use {
                    val xuid = emu.create_profile(
                        ContentPaths.contentRoot().absolutePath, gamertag, language, country)
                        ?: return@withContext OpState.Failed("Couldn't create the profile.")
                    val avatarError = tiles?.let { runCatching { writeAvatar(xuid, it) }.exceptionOrNull() }
                    if (avatarError == null) OpState.Done("Created “$gamertag”.")
                    else OpState.Done("Created “$gamertag”, but the avatar could not be saved: ${avatarError.message}")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                OpState.Failed(reason("Couldn't create the profile", e))
            }
        }
        refresh()
    }

    fun rename(xuid: String, gamertag: String, language: Int, country: Int, avatarUri: Uri?) =
        viewModelScope.launch {
            if (!Gamertag.isValid(gamertag)) {
                _opState.value = OpState.Failed("Enter a valid gamertag (1-15 characters).")
                return@launch
            }
            _opState.value = OpState.Busy("Saving…")
            _opState.value = withContext(Dispatchers.IO) {
                try {
                    val tiles = avatarUri?.let(::decodeAvatar)
                    EmulatorRuntime.ensureLoaded()
                    val emu = EmulatorRuntime.emulator ?: return@withContext OpState.Failed("Emulator not loaded.")
                    StorageAccess.acquire().use {
                        val status = emu.rename_profile(
                            ContentPaths.contentRoot().absolutePath, xuid, gamertag, language, country)
                        if (status != 0) return@withContext OpState.Failed(renameReasonFor(status))
                        val avatarError = tiles?.let { runCatching { writeAvatar(xuid, it) }.exceptionOrNull() }
                        if (avatarError == null) OpState.Done("Saved “$gamertag”.")
                        else OpState.Done("Saved “$gamertag”, but the avatar could not be saved: ${avatarError.message}")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    OpState.Failed(reason("Couldn't save the profile", e))
                }
            }
            refresh()
        }

    fun setActive(xuid: String) = viewModelScope.launch {
        _opState.value = runCatching { withContext(Dispatchers.IO) { writeActiveXuid(xuid.uppercase()) } }
            .fold({ OpState.Done("Active profile set. Applies on next game launch.") }, {
                if (it is CancellationException) throw it
                Log.w(TAG, "Setting the active profile failed", it)
                OpState.Failed(reason("Couldn't set the active profile; the configuration was kept", it))
            })
        refresh()
    }

    /** U11: the profile player [slot] + 1 (P2–P4) signs in with; null = nobody. P1's profile
     *  is refused: P1 is chosen before each game. */
    fun setPlayer(slot: Int, xuid: String?) = viewModelScope.launch {
        require(slot in 1 until ProfileSlots.COUNT) { "P1 is set with the active profile" }
        _opState.value = runCatching {
            withContext(Dispatchers.IO) {
                val before = readSlots()
                if (xuid != null && before[0].equals(xuid, ignoreCase = true)) {
                    return@withContext OpState.Failed("That profile plays as P1. Pick another one, or make another profile active first.")
                }
                writeSlots(before, ProfileSlots.assign(before, slot, xuid))
                OpState.Done(if (xuid == null) "P${slot + 1} will not sign in. Applies on next game launch."
                    else "P${slot + 1} profile set. Applies on next game launch.")
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            Log.w(TAG, "Setting a player's profile failed", it)
            OpState.Failed(reason("Couldn't set that player's profile; the configuration was kept", it))
        }
        refresh()
    }

    /** First step of a delete: measure what content/<XUID> holds so the dialog can say it. */
    fun requestDelete(entry: ProfileEntry) = viewModelScope.launch {
        _opState.value = OpState.Busy("Checking the profile's saved data…")
        _opState.value = withContext(Dispatchers.IO) {
            runCatching { OpState.ConfirmDelete(entry, profileTrash.summarize(entry.xuid)) }
                .getOrElse { OpState.Failed(reason("Couldn't read the profile's data", it)) }
        }
    }

    /** Moves the whole profile folder (account + every game's saves) to the trash. */
    fun delete(xuid: String) = viewModelScope.launch {
        _opState.value = OpState.Busy("Moving the profile to the trash…")
        _opState.value = withContext(Dispatchers.IO) {
            try {
                StorageAccess.acquire().use { lease ->
                    profileTrash.moveToTrash(lease, xuid)
                    if (activeXuid().equals(xuid, ignoreCase = true)) writeActiveXuid("")
                }
                OpState.Done("Profile moved to the trash. Restore it from this screen, or remove it permanently.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                OpState.Failed(reason("Couldn't remove the profile; nothing was deleted", e))
            }
        }
        refresh()
    }

    fun restore(trashId: String) = viewModelScope.launch {
        _opState.value = OpState.Busy("Restoring the profile…")
        _opState.value = withContext(Dispatchers.IO) {
            runCatching { StorageAccess.acquire().use { profileTrash.restore(it, trashId) } }
                .fold({ OpState.Done("Profile restored with its saves.") },
                    { OpState.Failed(reason("Couldn't restore the profile", it)) })
        }
        refresh()
    }

    fun purge(trashId: String) = viewModelScope.launch {
        _opState.value = OpState.Busy("Removing permanently…")
        _opState.value = withContext(Dispatchers.IO) {
            runCatching { StorageAccess.acquire().use { profileTrash.purge(it, trashId) } }
                .fold({ OpState.Done("Removed permanently.") },
                    { OpState.Failed(reason("Couldn't remove the trashed profile", it)) })
        }
        refresh()
    }

    private fun reason(action: String, e: Throwable): String = when (e) {
        is ContentBusyException -> "$action: a game or another save/content operation is running. Close it and try again."
        else -> "$action: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun activeXuid(): String = readSlots()[0].orEmpty()

    /** P1 signs in with [xuid] ("" = nobody); another player slot that had it is freed. */
    private fun writeActiveXuid(xuid: String) {
        val before = readSlots()
        writeSlots(before, ProfileSlots.assign(before, 0, xuid.ifBlank { null }))
    }

    private fun readSlots(): List<String?> {
        val h = configStore.openLiveSnapshot()
        return try {
            ProfileSlots.normalize(List(ProfileSlots.COUNT) { slot -> h.getString(ProfileSlots.SECTION, ProfileSlots.key(slot)) })
        } finally {
            h.closeDiscard()
        }
    }

    private fun writeSlots(before: List<String?>, after: List<String?>) {
        val changes = ProfileSlots.changes(before, after)
        if (changes.isEmpty()) return
        configStore.editLiveConfig { h ->
            changes.forEach { (slot, xuid) -> h.putString(ProfileSlots.SECTION, ProfileSlots.key(slot), xuid) }
        }
    }

    private class AvatarTiles(val tile64: ByteArray, val tile32: ByteArray)

    /** Bounded read + bounds-only decode first: a huge or bogus image cannot exhaust memory. */
    private fun decodeAvatar(uri: Uri): AvatarTiles {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use {
            ArchiveFiles.readBounded(it, AvatarPolicy.MAX_INPUT_BYTES)
        } ?: error("Cannot read the selected image")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = AvatarPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("The selected file is not a supported image")
        try {
            val square = centerCropSquare(src)
            try {
                return AvatarTiles(png(square, 64), png(square, 32))
            } finally {
                if (square !== src) square.recycle()
            }
        } finally {
            src.recycle()
        }
    }

    private fun writeAvatar(xuid: String, tiles: AvatarTiles) {
        val dir = ProfilePaths.profileDir(xuid)
        ArchiveFiles.atomicBytes(File(dir, "tile_64.png"), tiles.tile64)
        ArchiveFiles.atomicBytes(File(dir, "tile_32.png"), tiles.tile32)
    }

    private fun centerCropSquare(bmp: Bitmap): Bitmap {
        val side = minOf(bmp.width, bmp.height)
        if (side == bmp.width && side == bmp.height) return bmp
        val x = (bmp.width - side) / 2
        val y = (bmp.height - side) / 2
        return Bitmap.createBitmap(bmp, x, y, side, side)
    }

    private fun png(square: Bitmap, size: Int): ByteArray {
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        try {
            return ByteArrayOutputStream().use {
                check(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Avatar encoding failed" }
                it.toByteArray()
            }
        } finally {
            if (scaled !== square) scaled.recycle()
        }
    }

    private fun renameReasonFor(status: Int): String = when (status) {
        -1 -> "Emulator not loaded."
        0xC000000D.toInt() -> "Enter a valid gamertag (1-15 characters)."
        0xC0000034.toInt() -> "That profile no longer exists."
        0xC0000022.toInt() -> "Couldn't write the profile files."
        else -> "Save failed (0x${status.toUInt().toString(16)})."
    }

    private companion object { const val TAG = "ProfileManager" }
}
