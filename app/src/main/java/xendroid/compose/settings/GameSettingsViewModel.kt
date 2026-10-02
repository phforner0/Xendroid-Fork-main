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

/**
 * Drives the per-game override editor. Implements [SettingsHost] so it reuses the same
 * SettingRow composables as the global screen; layered on top is the override toggle
 * ([setOverride]) + the inherited-value display. The repo keeps the override set sparse
 * and patches just edited keys on [flush], preserving unknown TOML entries.
 */
class GameSettingsViewModel(private val repo: GameSettingsRepository) : ViewModel(), SettingsHost {

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

    /** Re-read after a pause; the override set is already in memory so this is cheap. */
    fun onResume() = load()

    override fun onCleared() {
        saveScope.launch {
            try { runCatching { repo.flush() }.onFailure { fail(it) } }
            finally { saveScope.cancel() }
        }
    }
}
