package xendroid.compose.patches

import xendroid.compose.R
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the patches screen for one already-resolved title id, reading/writing via [PatchStore].
 * No native involvement, so it never blocks on `EmulatorRuntime`.
 */
class GamePatchesViewModel(
    private val titleId: String,
    private val store: PatchStore,
    private val appContext: Context,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        /** [conflicts]: patches on together that write the same memory (L11). */
        data class Loaded(val files: List<PatchFile>, val conflicts: List<PatchConflict> = emptyList()) : UiState
        data object Empty : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Result of adding or removing a file, for a dialog; null when there is nothing to say. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    /** L11: the user's own patch file for this game, checked before the emulator ever sees it. */
    fun importUserPatch(uri: Uri) {
        val context = appContext
        viewModelScope.launch(Dispatchers.IO) {
            _message.value = try {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "patch"
                val bytes = context.contentResolver.openInputStream(uri)?.use {
                    xendroid.compose.archive.ArchiveFiles.readBounded(it, MAX_IMPORT_BYTES)
                } ?: error(context.getString(R.string.pt_cannot_read))
                val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                val stored = store.importUserPatch(titleId, name, text)
                context.getString(R.string.pt_added, stored)
            } catch (e: java.nio.charset.CharacterCodingException) {
                context.getString(R.string.pt_not_utf8)
            } catch (e: Exception) {
                context.getString(R.string.pt_not_added, e.message ?: context.getString(R.string.pt_unreadable))
            }
            _state.value = compute()
        }
    }

    fun removeUserPatch(file: PatchFile) {
        viewModelScope.launch(Dispatchers.IO) {
            _message.value = runCatching { store.removeUserPatch(file.fileName) }
                .fold({ appContext.getString(R.string.pt_removed, file.variantLabel) },
                    { appContext.getString(R.string.pt_not_removed, it.message ?: it.javaClass.simpleName) })
            _state.value = compute()
        }
    }

    init { reload() }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) { _state.value = compute() }
    }

    /** Flip one entry, then re-read effective (on-disk) state so the UI matches what's saved. */
    fun toggle(file: PatchFile, entry: PatchEntry, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.setEnabled(file.fileName, entry.index, enabled) }
            _state.value = compute()
        }
    }

    /** L10: catalog updates of one file (see [PatchStore]). */
    enum class UpdateAction { APPLY, KEEP_MINE, UNDO, DISMISS }

    fun update(file: PatchFile, action: UpdateAction) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                when (action) {
                    UpdateAction.APPLY -> store.applyUpdate(file.fileName)
                    UpdateAction.KEEP_MINE -> store.keepMine(file.fileName)
                    UpdateAction.UNDO -> store.undoUpdate(file.fileName)
                    UpdateAction.DISMISS -> store.dismissUpdate(file.fileName)
                }
            }
            _state.value = compute()
        }
    }

    private fun compute(): UiState = runCatching {
        val texts = store.textsForTitle(titleId)
        val files = texts.map { it.first }
        // A file the check cannot read is left out of the comparison, never hidden.
        val conflicts = PatchFileCheck.conflicts(texts.mapNotNull { (file, text) ->
            runCatching { (if (file.mine) appContext.getString(R.string.pt_yours, file.variantLabel) else file.variantLabel) to PatchFileCheck.read(text) }.getOrNull()
        })
        if (files.isEmpty()) UiState.Empty else UiState.Loaded(files, conflicts)
    }.getOrElse { UiState.Error(it.message ?: appContext.getString(R.string.pt_load_failed)) }

    private companion object {
        const val MAX_IMPORT_BYTES = 1024L * 1024
    }
}
