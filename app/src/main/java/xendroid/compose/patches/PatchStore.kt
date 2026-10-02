package xendroid.compose.patches

import java.io.File
import xendroid.compose.archive.ArchiveFiles

/**
 * Reads effective patch state and writes per-patch toggles. Effective state = the on-disk file
 * in [patchesDir] if present, else the bundled asset (which ships all-disabled). A toggle copies
 * the asset to disk on first edit, then flips one `is_enabled` line; the emulator reads
 * [patchesDir] on the next launch.
 *
 * L10: every write is atomic (a killed process never leaves a half-written file the emulator
 * would fail to parse), and an on-disk copy follows the catalog of newer app versions instead
 * of hiding it forever. Beside a copy, "<file>.base" is the catalog text it came from and
 * "<file>.prev" the copy before the last catalog update (to undo); neither ends in
 * ".patch.toml", so the emulator never loads them.
 *  - Only switches changed since the base: the copy moves to the new catalog by itself, with
 *    the same patches on (matched by name; see [PatchCatalog]).
 *  - Changed by hand (or toggled by a version that kept no base): the update waits for the
 *    user, with a preview; "keep mine" stops asking until the catalog changes again.
 */
class PatchStore(
    private val assets: PatchAssets,
    private val patchesDir: File,
) {
    /** Files whose 8-hex filename prefix matches [titleId] (case-insensitive). A title may have several. */
    fun patchesForTitle(titleId: String): List<PatchFile> =
        assets.list()
            .filter { it.length >= 8 && it.substring(0, 8).equals(titleId, ignoreCase = true) }
            .sorted()
            .mapNotNull { name ->
                val text = sync(name)
                PatchTomlParser.parse(name, text)?.copy(update = updateOf(name, text))
            }

    /** Brings every on-disk copy that only differs in switches to the bundled catalog (the
     *  emulator reads only the copies, so this must not wait for the patches screen). Returns
     *  how many were updated; a file that fails is left as it is. */
    fun syncAll(): Int {
        val bundled = assets.list().toHashSet()
        return patchesDir.listFiles { f -> f.isFile && f.name.endsWith(".patch.toml") && f.name in bundled }.orEmpty()
            .count { f -> runCatching { val before = f.readText(); sync(f.name) != before }.getOrDefault(false) }
    }

    /** Toggle a single `[[patch]]` entry by its 0-based ordinal. */
    fun setEnabled(fileName: String, patchIndex: Int, enabled: Boolean) {
        patchesDir.mkdirs()
        val onDisk = file(fileName)
        val current = if (onDisk.exists()) {
            onDisk.readText()
        } else {
            assets.read(fileName).also { ArchiveFiles.atomicText(base(fileName), it) }
        }
        ArchiveFiles.atomicText(onDisk, PatchTomlEditor.setEnabled(current, patchIndex, enabled))
    }

    /** Applies a pending catalog update (the user saw the preview); the old copy can be restored. */
    fun applyUpdate(fileName: String) {
        val onDisk = file(fileName).takeIf { it.isFile } ?: return
        apply(fileName, onDisk.readText(), assets.read(fileName))
    }

    /** Keeps the user's own copy; asks again only when the catalog changes again. */
    fun keepMine(fileName: String) {
        if (file(fileName).isFile) ArchiveFiles.atomicText(base(fileName), assets.read(fileName))
    }

    /** Back to the copy from before the last catalog update. */
    fun undoUpdate(fileName: String) {
        val previous = prev(fileName).takeIf { it.isFile } ?: return
        ArchiveFiles.atomicText(file(fileName), previous.readText())
        ArchiveFiles.atomicText(base(fileName), assets.read(fileName))   // do not re-apply it by itself
        previous.delete()
    }

    /** The update was seen: the copy to undo it goes. */
    fun dismissUpdate(fileName: String) {
        prev(fileName).delete()
    }

    private fun file(name: String) = File(patchesDir, name)
    private fun base(name: String) = File(patchesDir, "$name.base")
    private fun prev(name: String) = File(patchesDir, "$name.prev")

    /** The text the emulator will read, after moving a switches-only copy to the current catalog. */
    private fun sync(name: String): String {
        val catalog = assets.read(name)
        val onDisk = file(name)
        if (!onDisk.exists()) return catalog
        val current = onDisk.readText()
        val baseText = base(name).takeIf { it.isFile }?.readText()
        if (baseText == catalog) return current
        if (PatchCatalog.onlySwitchesDiffer(current, catalog)) {
            // Already the current catalog with switches (an older copy without base, or a write
            // interrupted before the base): only the base is missing.
            ArchiveFiles.atomicText(base(name), catalog)
            return current
        }
        if (baseText == null || !PatchCatalog.onlySwitchesDiffer(current, baseText)) return current
        return apply(name, current, catalog)
    }

    /** Old copy first, then the new file, then its base: a kill in between is settled by [sync]. */
    private fun apply(name: String, current: String, catalog: String): String {
        val rebased = PatchCatalog.rebase(catalog, PatchCatalog.enabledKeys(current)).text
        ArchiveFiles.atomicText(prev(name), current)
        ArchiveFiles.atomicText(file(name), rebased)
        ArchiveFiles.atomicText(base(name), catalog)
        return rebased
    }

    private fun updateOf(name: String, text: String): PatchUpdate? {
        val onDisk = file(name)
        if (!onDisk.exists()) return null
        val catalog = assets.read(name)
        prev(name).takeIf { it.isFile }?.readText()?.let { before ->
            val now = PatchCatalog.enabledKeys(text)
            return PatchUpdate(pending = false, keptOn = now.toList(),
                dropped = PatchCatalog.enabledKeys(before).filter { it !in now }, canUndo = true)
        }
        val baseText = base(name).takeIf { it.isFile }?.readText()
        if (baseText == catalog || PatchCatalog.onlySwitchesDiffer(text, catalog)) return null
        val preview = PatchCatalog.rebase(catalog, PatchCatalog.enabledKeys(text))
        return PatchUpdate(pending = true, keptOn = preview.keptOn, dropped = preview.dropped, canUndo = false)
    }
}
