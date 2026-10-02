package xendroid.compose.patches

/** One `[[patch]]` block; [index] is its 0-based ordinal among blocks (the toggle key). */
data class PatchEntry(
    val index: Int,
    val name: String,
    val desc: String?,
    val author: String?,
    val isEnabled: Boolean,
)

/** A parsed .patch.toml: header + entries. [variantLabel] groups the files of one title. */
data class PatchFile(
    val fileName: String,
    val titleName: String,
    val titleId: String,
    val hashes: List<String>,
    val variantLabel: String,
    val entries: List<PatchEntry>,
    /** L10: this file against the catalog bundled with this app version, when there is news. */
    val update: PatchUpdate? = null,
)

/**
 * L10: [pending] = this app bundles a newer catalog for the file, but the file was changed by
 * hand, so updating waits for the user (it keeps a copy to undo). Otherwise the update was
 * applied by itself (only switches had changed) and can be undone while [canUndo].
 * [keptOn]: patches still on after the update; [dropped]: ones that were on and are gone.
 */
data class PatchUpdate(
    val pending: Boolean,
    val keptOn: List<String>,
    val dropped: List<String>,
    val canUndo: Boolean,
)
