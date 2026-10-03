package xendroid.compose.ui.keymap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xendroid.compose.data.GameButton
import xendroid.compose.data.GameButtons
import xendroid.compose.data.KeymapEdits
import xendroid.compose.data.KeymapStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KeymapRow(val button: GameButton, val boundKey: Int)   // boundKey 0 = cleared

data class KeymapUiState(
    val rows: List<KeymapRow> = emptyList(),
    val vibrate: Boolean = false,
    /** 15o: buttons sharing a key with another (one of them does nothing), and changed ones. */
    val shared: Set<Int> = emptySet(),
    val changed: Set<Int> = emptySet(),
)

/** 15o: binding a key another button had traded the two: [index] took it from [from]. */
data class KeymapSwap(val index: Int, val from: Int)

class KeymapViewModel(private val store: KeymapStore) : ViewModel() {

    val state: StateFlow<KeymapUiState> =
        combine(store.bindings, store.vibrateEnabled) { bindings, vibrate ->
            KeymapUiState(
                rows = GameButtons.ALL.map { b -> KeymapRow(b, bindings[b.index] ?: b.defaultAndroidKey) },
                vibrate = vibrate,
                shared = KeymapEdits.duplicates(bindings),
                changed = KeymapEdits.changed(bindings),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeymapUiState())

    private val _swap = MutableStateFlow<KeymapSwap?>(null)
    val swap: StateFlow<KeymapSwap?> = _swap.asStateFlow()

    /** A key held by another button trades places (15o), so no key ends up on two buttons. */
    fun onKeyCaptured(index: Int, androidKeyCode: Int) = viewModelScope.launch {
        val bind = KeymapEdits.bind(store.bindings.first(), index, androidKeyCode)
        store.setBindings(bind.changes)
        _swap.value = bind.swappedWith?.let { KeymapSwap(index, it) }
    }

    fun onSwapShown() { _swap.value = null }

    fun onSwapFaceButtons() = viewModelScope.launch {
        store.setBindings(KeymapEdits.swapFaceButtons(store.bindings.first()))
    }

    fun onClear(index: Int) = viewModelScope.launch { store.clearBinding(index) }

    fun onResetDefaults() = viewModelScope.launch { store.resetToDefaults() }

    fun onVibrateChanged(enabled: Boolean) = viewModelScope.launch { store.setVibrate(enabled) }
}
