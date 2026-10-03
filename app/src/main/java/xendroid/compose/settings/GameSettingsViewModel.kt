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
import xendroid.compose.community.CommunityConfigs
import xendroid.compose.community.CommunityException
import xendroid.compose.community.CommunityService
import xendroid.compose.community.OwnUpload
import xendroid.compose.compatibility.AppliedProfile
import xendroid.compose.compatibility.CompatStatus
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
    /** 15b: null unless the build names a community server. */
    private val community: CommunityService? = null,
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
                loadCommunity()
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
        /** 15b: this game's settings went to the community server. */
        data class Shared(val name: String) : ProfileMessage {
            override val english: String get() = "Shared “$name”. Only this phone can delete it: it keeps the delete token."
        }
        data object Deleted : ProfileMessage {
            override val english: String get() = "Deleted from the community server."
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

    // ---- 15b: community configs (only when the build names a server) ----

    /** The game's community list as last searched; [busy]: a call is under way; [error]: the
     *  last call's failure, until the next call. */
    data class CommunityState(
        val server: String,
        val busy: Boolean = false,
        val fetchedAt: Long? = null,
        val entries: List<CommunityConfigs.Entry> = emptyList(),
        val hidden: Int = 0,
        val skipped: List<String> = emptyList(),
        val myVotes: Map<String, Int> = emptyMap(),
        val mine: Map<String, OwnUpload> = emptyMap(),
        val error: CommunityException? = null,
    )

    private val _community = MutableStateFlow(community?.let { CommunityState(it.server) })
    val communityState: StateFlow<CommunityState?> = _community.asStateFlow()

    /** The share dialog's start: this game's own settings and the phone, read when it opens. */
    data class ShareStart(val overrides: Map<String, String>, val facts: DeviceFacts?)

    private val _share = MutableStateFlow<ShareStart?>(null)
    val shareStart: StateFlow<ShareStart?> = _share.asStateFlow()

    /** The list kept from the last search; opening the screen asks the server nothing. */
    private fun loadCommunity() {
        val service = community ?: return
        runCatching { show(service.cached(repo.titleId, facts())) }
            .onFailure { Log.w("GameSettingsViewModel", "Reading the community list failed", it) }
    }

    private fun show(view: CommunityService.View) {
        _community.value = _community.value?.copy(fetchedAt = view.fetchedAt, entries = view.listing.entries,
            hidden = view.listing.hidden, skipped = view.listing.skipped, myVotes = view.myVotes, mine = view.mine)
    }

    /** One call at a time, off the main thread; its failure is shown on the card. */
    private fun communityCall(block: (CommunityService) -> Unit) {
        val service = community ?: return
        val state = _community.value ?: return
        if (state.busy) return
        _community.value = state.copy(busy = true, error = null)
        saveScope.launch {
            try {
                block(service)
            } catch (e: CommunityException) {
                _community.value = _community.value?.copy(error = e)
            } catch (e: Exception) {
                Log.w("GameSettingsViewModel", "Community call failed", e)
                _community.value = _community.value?.copy(error = CommunityException(CommunityException.Kind.NETWORK, cause = e))
            } finally {
                _community.value = _community.value?.copy(busy = false)
            }
        }
    }

    /** Asks the server for this game's list (sends its Title ID). */
    fun searchCommunity() = communityCall { show(it.refresh(repo.titleId, facts())) }

    /** [vote] 1 helped, -1 did not; the same vote again takes it back. */
    fun voteCommunity(configId: String, vote: Int) = communityCall { service ->
        val sent = if (_community.value?.myVotes?.get(configId) == vote) 0 else vote
        val votes = service.vote(configId, sent)
        _community.value = _community.value?.let { state ->
            state.copy(
                entries = state.entries.map { e ->
                    if (e.config.id != configId) e else e.copy(config = e.config.copy(votesUp = votes.up, votesDown = votes.down))
                },
                myVotes = if (sent == 0) state.myVotes - configId else state.myVotes + (configId to sent),
            )
        }
    }

    /** Deletes a config this phone shared, then lists again (the kept list if that fails). */
    fun deleteShared(configId: String) = communityCall { service ->
        service.delete(configId)
        _profileMessage.value = ProfileMessage.Deleted
        relist(service)
    }

    private fun relist(service: CommunityService) {
        runCatching { show(service.refresh(repo.titleId, facts())) }
            .onFailure { show(service.cached(repo.titleId, facts())) }
    }

    fun prepareShare() {
        if (community == null) return
        saveScope.launch {
            runCatching { _share.value = ShareStart(repo.overrideValues(), facts()) }.onFailure { fail(it) }
        }
    }

    fun dismissShare() { _share.value = null }

    /** What sharing would send with these answers; pure, so the dialog checks it as it is typed. */
    fun draftShare(name: String, note: String, result: CompatStatus?): CommunityConfigs.Draft? {
        val start = _share.value ?: return null
        return community?.draft(repo.titleId, name, note, result, start.overrides, start.facts)
    }

    /** Sends exactly what the dialog listed. */
    fun share(name: String, note: String, result: CompatStatus?) {
        val upload = draftShare(name, note, result)?.upload ?: return
        _share.value = null
        communityCall { service ->
            service.share(upload)
            _profileMessage.value = ProfileMessage.Shared(upload.name)
            relist(service)
        }
    }

    // Last in the class: the load runs on another thread and reads the state flows above, which
    // must be initialized before it starts.
    init { load() }

    /** Re-read after a pause; the override set is already in memory so this is cheap. */
    fun onResume() = load()

    override fun onCleared() {
        saveScope.launch {
            try { runCatching { repo.flush() }.onFailure { fail(it) } }
            finally { saveScope.cancel() }
        }
    }
}
