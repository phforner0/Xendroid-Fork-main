package xendroid.compose.roadmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.core.ContentPaths
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.core.GameMetadataSource
import xendroid.compose.core.StorageAccess
import xendroid.compose.ui.content.ContentInstallState
import xendroid.compose.ui.content.ContentManagerViewModel
import xendroid.compose.ui.content.ContentManagerViewModel.ListState

/**
 * Roadmap item 23 (L12), with `-e dlcPackage <a DLC or title update package>`: installed into the
 * test package's own content (never the .debug app's) through the real install, then "Move to
 * trash" (gone from its tab, in the trash with size and date), "Restore" (back), moved again and
 * installed again: "Restore" refuses with "…is installed again…" in the shown language; "Delete"
 * in the trash removes it for good. With a game running (the storage lease held), moving says
 * "Close the running game first." and nothing moves.
 *
 * Left for the phone: the game seeing or not seeing the package (group C), killing the app in the
 * middle of a move.
 */
@RunWith(AndroidJUnit4::class)
class Item23ContentTrashTest {
    private val context = Device.context

    @Test fun trashRestoreRefuseAndDelete() {
        val source = GameRun.dlcPackage()
        Device.requireTestPackage()
        EmulatorRuntime.ensureLoaded()
        val meta = GameMetadataSource().readContentHeader(source.path)
        assumeTrue("not a package the app reads: $source", meta?.titleId != null)
        val title = meta!!.titleId!!.uppercase()
        assumeTrue("only DLC and title updates go to the trash",
            meta.contentType == ContentPaths.DLC_CONTENT_TYPE || meta.contentType == ContentPaths.TU_CONTENT_TYPE)
        val vm = ContentManagerViewModel(context, GameMetadataSource(), title)

        fun outcome(what: String, action: () -> Unit): ContentInstallState {
            vm.dismiss()
            action()
            return Device.await(vm.state, what, 5 * 60_000L) {
                it is ContentInstallState.Done || it is ContentInstallState.Failed || it is ContentInstallState.ConfirmOverwrite
            }
        }
        fun loaded(what: String, matches: (ListState.Loaded) -> Boolean) =
            Device.await(vm.listState, what, 60_000) { it is ListState.Loaded && matches(it) } as ListState.Loaded
        fun installed(state: ListState.Loaded) = (if (meta.contentType == ContentPaths.DLC_CONTENT_TYPE) state.dlc else state.updates)
            .firstOrNull { it.pkgDir == source.name || it.displayName == meta.displayName }
        fun install() {
            when (val result = outcome("installing ${source.name}") { vm.install(source.path) }) {
                is ContentInstallState.ConfirmOverwrite -> assertTrue(outcome("overwriting") { vm.confirmOverwrite(source.path, result.displayName) } is ContentInstallState.Done)
                else -> assertTrue(result.toString(), result is ContentInstallState.Done)
            }
        }

        try {
            install()
            val entry = installed(loaded("the package listed") { installed(it) != null })!!

            assertEquals(ContentInstallState.Done(Device.string(R.string.cm_trashed, entry.displayName)), outcome("moving to the trash") { vm.delete(entry) })
            val trashed = loaded("the package in the trash") { it.trashed.any { t -> t.pkgDir == entry.pkgDir } && installed(it) == null }
                .trashed.single { it.pkgDir == entry.pkgDir }
            assertTrue(trashed.bytes > 0 && trashed.deletedAt > 0)

            assertEquals(ContentInstallState.Done(Device.string(R.string.cm_restored, trashed.displayName)), outcome("restoring") { vm.restore(trashed) })
            loaded("the package back") { installed(it) != null && it.trashed.none { t -> t.id == trashed.id } }

            // Trashed, installed again, then Restore: refused, nothing replaced.
            outcome("moving to the trash again") { vm.delete(entry) }
            val again = loaded("in the trash again") { it.trashed.any { t -> t.pkgDir == entry.pkgDir } }.trashed.single { it.pkgDir == entry.pkgDir }
            install()
            assertEquals(ContentInstallState.Failed(Device.string(R.string.cm_restore_installed_again, again.displayName)),
                outcome("restoring over a reinstall") { vm.restore(again) })

            assertEquals(ContentInstallState.Done(Device.string(R.string.cm_deleted, again.displayName)), outcome("deleting for good") { vm.purge(again) })
            loaded("the trash without it") { it.trashed.none { t -> t.id == again.id } }

            // A game running holds the storage lease: nothing moves.
            StorageAccess.acquire().use {
                assertEquals(ContentInstallState.Failed(Device.string(R.string.cm_close_game)), outcome("moving while a game runs") { vm.delete(entry) })
            }
            assertTrue(installed(loaded("still installed") { installed(it) != null }) != null)
        } finally {
            runCatching {
                vm.dismiss()
                vm.deleteForGood(installed(vm.listState.value as? ListState.Loaded ?: return@runCatching) ?: return@runCatching)
                Device.await(vm.state, "removing the test package", 60_000) { it is ContentInstallState.Done || it is ContentInstallState.Failed }
            }
            File(ContentPaths.contentRoot(), ".xendroid-trash/content").listFiles().orEmpty()
                .filter { it.name.startsWith("$title-") }.forEach { it.deleteRecursively() }
        }
    }
}
