package xendroid.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xendroid.compose.core.EmulatorRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xendroid.compose.compatibility.AppliedProfile
import xendroid.compose.compatibility.DeviceFacts
import xendroid.compose.compatibility.LoadedProfile
import xendroid.compose.compatibility.SettingsProfileStore
import xendroid.compose.compatibility.SettingsProfiles

/**
 * Drives the per-game override editor. Implements [SettingsHost] so it reuses the same
 * SettingRow composables as the global screen; layered on top is the override toggle
 * ([setOverride]) + the inherited-value display. The repo keeps the override set sparse
 * and patches just edited keys on [flush], preserving unknown TOML entries.
 */
class GameSettingsViewModel(
    private val repo: GameSettingsRepository,
    private val profiles: SettingsProfileStore? = null,
    private val facts: () -> DeviceFacts? = { null },
) : ViewModel(), SettingsHost {

    val categories: List<SettingsCategory> = SettingsSchema.categories
    override val isCustomDriverSupported get() = repo.isCustomDriverSupported
    override val persistenceKey get() = repo.persistenceKey

    /** key -> raw override value (overridden keys only); containsKey == overridden. Value-carrying
     *  so a value-only edit changes the map and the StateFlow emits (a Boolean flag map would be
     *  equals-equal and MutableStateFlow would dedupe -> no recompose). */
    private val _overrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val overrides: StateFlow<Map<String, String>> = _overrides.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    // Dispose/onCleared can run after viewModelScope was cancelled. Pending writes
    // need a finite, independent final flush rather than launching on a cancelled scope.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init { load() }

    /** Off-main load (ensureLoaded() can sleep + System.loadLibrary on delay-load devices). */
    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                EmulatorRuntime.ensureLoaded()
                repo.reload()
                reloadAll()
                _ready.value = true
                _error.value = null
                loadProfiles()
            }.onFailure { _ready.value = false; fail(it) }
        }
    }

    private fun reloadAll() {
        _overrides.value = SettingsSchema.allSettings
            .mapNotNull { s -> repo.rawOverride(s)?.let { s.key to it } }
            .toMap()
    }

    private fun refreshKey(s: Setting) {
        _overrides.value = _overrides.value.toMutableMap().apply {
            val raw = repo.rawOverride(s)
            if (raw != null) put(s.key, raw) else remove(s.key)
        }
    }

    fun isOverridden(s: Setting) = repo.isOverridden(s)
    fun inheritedLabel(s: Setting) = repo.inheritedLabel(s)
    private fun change(s: Setting, edit: () -> Unit) {
        runCatching { edit(); refreshKey(s) }.onFailure { fail(it) }
    }
    fun setOverride(s: Setting, on: Boolean) = change(s) { repo.setOverride(s, on) }

    override fun currentBool(s: Setting.Bool) = repo.boolOf(s)
    override fun onBoolChanged(s: Setting.Bool, v: Boolean) = change(s) { repo.setBool(s, v) }
    override fun currentInt(s: Setting.IntRange) = repo.intOf(s)
    override fun onIntChanged(s: Setting.IntRange, v: Int) = change(s) { repo.setInt(s, v) }
    override fun currentListValue(s: Setting.ListChoice) = repo.listValueOf(s)
    override fun onListChanged(s: Setting.ListChoice, v: String) = change(s) { repo.setListValue(s, v) }
    override fun currentDriverPath(s: Setting.Action) = repo.driverPathOf(s)
    override fun onDriverPathChanged(s: Setting.Action, v: String) = change(s) { repo.setDriverPath(s, v) }

    private fun fail(cause: Throwable) {
        Log.w("GameSettingsViewModel", "Config edit failed; keeping previous file", cause)
        _error.value = "Could not read or save this game's configuration. The existing file was kept. Fix invalid TOML or storage access, then retry."
    }

    fun clearError() { _error.value = null }

    fun flush() {
        saveScope.launch { runCatching { repo.flush() }.onFailure { fail(it) } }
    }

    // ---- C05: recommended settings profiles ----

    /** Profiles for this game: [offered] here, [notHere] with why not, files [skipped] with why. */
    data class ProfilesState(
        val offered: List<LoadedProfile> = emptyList(),
        val notHere: List<Pair<LoadedProfile, List<String>>> = emptyList(),
        val skipped: List<String> = emptyList(),
        val applied: AppliedProfile? = null,
        val facts: DeviceFacts? = null,
    ) {
        val isEmpty: Boolean get() = offered.isEmpty() && notHere.isEmpty() && skipped.isEmpty() && applied == null
    }

    /** A plan waiting for the player's confirmation. */
    sealed interface ProfilePreview {
        val plan: SettingsProfiles.Plan
        data class Apply(val profile: LoadedProfile, override val plan: SettingsProfiles.Plan) : ProfilePreview
        data class Restore(val record: AppliedProfile, override val plan: SettingsProfiles.Plan) : ProfilePreview
    }

    private val _profiles = MutableStateFlow(ProfilesState())
    val profilesState: StateFlow<ProfilesState> = _profiles.asStateFlow()
    private val _preview = MutableStateFlow<ProfilePreview?>(null)
    val profilePreview: StateFlow<ProfilePreview?> = _preview.asStateFlow()
    /** What a profile action did, for a dialog: the screen says it in its language (U02);
     *  [english] for logs and tests. */
    sealed interface ProfileMessage {
        val english: String
        data object RestoreFirst : ProfileMessage {
            override val english: String get() = "Restore the settings from before the applied profile first."
        }
        data class Applied(val name: String, val count: Int) : ProfileMessage {
            override val english: String get() = "Applied “$name”: $count ${if (count == 1) "setting" else "settings"} for this game. " +
                "They take effect the next time it starts."
        }
        /** [changedSince]: some of what the profile wrote was changed by the player since, and stays. */
        data class Restored(val name: String, val changedSince: Boolean) : ProfileMessage {
            override val english: String get() = "Put back the settings from before “$name”" +
                (if (changedSince) "; the ones you changed since stay." else ".")
        }
        data object Stale : ProfileMessage {
            override val english: String get() =
                "This game's settings changed since the preview, so nothing was written. Review it again."
        }
    }

    private val _profileMessage = MutableStateFlow<ProfileMessage?>(null)
    val profileMessage: StateFlow<ProfileMessage?> = _profileMessage.asStateFlow()

    private fun loadProfiles() {
        val store = profiles ?: return
        runCatching {
            val parsed = store.load()
            val device = facts()
            val (here, elsewhere) = store.forTitle(parsed.profiles, repo.titleId)
                .map { p -> p to (device?.let { SettingsProfiles.unmet(p.profile.requires, it) } ?: listOf("this phone is not known yet")) }
                .partition { it.second.isEmpty() }
            _profiles.value = ProfilesState(here.map { it.first }, elsewhere, parsed.skipped, store.applied(repo.titleId), device)
        }.onFailure { Log.w("GameSettingsViewModel", "Loading settings profiles failed", it) }
    }

    /** Shows what applying [profile] would change; the player's own settings stay. */
    fun previewProfile(profile: LoadedProfile) {
        if (_profiles.value.applied != null) {
            _profileMessage.value = ProfileMessage.RestoreFirst
            return
        }
        saveScope.launch {
            runCatching {
                _preview.value = ProfilePreview.Apply(profile,
                    SettingsProfiles.plan(profile.profile, repo.overrideValues(), repo.inheritedValues()))
            }.onFailure { fail(it) }
        }
    }

    /** Shows what "Restore previous" would put back. */
    fun previewRestore() {
        val record = _profiles.value.applied ?: return
        saveScope.launch {
            runCatching {
                _preview.value = ProfilePreview.Restore(record,
                    SettingsProfiles.restorePlan(record, repo.overrideValues(), repo.inheritedValues()))
            }.onFailure { fail(it) }
        }
    }

    fun dismissPreview() { _preview.value = null }
    fun clearProfileMessage() { _profileMessage.value = null }

    /** Writes the previewed plan, exactly; refused when this game's settings changed meanwhile. */
    fun confirmPreview() {
        val preview = _preview.value ?: return
        _preview.value = null
        val store = profiles ?: return
        saveScope.launch {
            runCatching {
                when (preview) {
                    is ProfilePreview.Apply -> {
                        if (preview.plan.writes.isEmpty()) return@runCatching
                        // Recorded first: if the write never lands, restoring finds nothing of it to undo.
                        store.recordApplied(repo.titleId, preview.profile, preview.plan)
                        try {
                            repo.applyPlan(preview.plan.expected, preview.plan.writes)
                        } catch (t: Throwable) {
                            runCatching { store.clearApplied(repo.titleId) }
                            throw t
                        }
                        _profileMessage.value = ProfileMessage.Applied(preview.profile.profile.name, preview.plan.writes.size)
                    }
                    is ProfilePreview.Restore -> {
                        if (preview.plan.writes.isNotEmpty()) repo.applyPlan(preview.plan.expected, preview.plan.writes)
                        store.clearApplied(repo.titleId)
                        _profileMessage.value = ProfileMessage.Restored(preview.record.profileName,
                            changedSince = preview.plan.writes.size < preview.record.written.size)
                    }
                }
                reloadAll()
                loadProfiles()
            }.onFailure {
                if (it is GameSettingsRepository.StalePlanException) {
                    _profileMessage.value = ProfileMessage.Stale
                } else fail(it)
            }
        }
    }

    /** Re-read after a pause; the override set is already in memory so this is cheap. */
    fun onResume() = load()

    override fun onCleared() {
        saveScope.launch {
            try { runCatching { repo.flush() }.onFailure { fail(it) } }
            finally { saveScope.cancel() }
        }
    }
}
