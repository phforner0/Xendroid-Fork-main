package xendroid.compose.roadmap

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import xendroid.compose.AppContainer
import xendroid.compose.data.PreferencesStore
import xendroid.compose.ui.library.FirstRunStore
import xendroid.compose.ui.library.GameLibraryViewModel
import xendroid.compose.ui.library.LibraryUiState

/**
 * What the items that need games (groups B and C) are given, as instrumentation arguments —
 * paths on the phone, never in git:
 *
 *     ./gradlew :app:connectedUitestAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.gameDir=/sdcard/Games
 *
 * - `gameDir`: a folder with games. Only listed and read (scans, covers, collections), never changed.
 * - `scratchDir`: a folder the tests may change (rename a game away and back, rename the folder),
 *   holding a COPY of at least one small game; never the real collection.
 * - `dlcPackage`: a DLC or title update package of a game in `gameDir`, installed into the test
 *   package's own content (never the .debug app's).
 * - `scanWholeStorage=true`: also scans the whole internal storage (item 22d; slow).
 *
 * Without an argument the tests that need it are skipped (an assumption), not failed.
 */
object GameRun {
    private fun arg(name: String): String? = InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }

    /** A folder with games, read only. */
    fun gameDir(): File {
        Device.grantAllFilesAccess()
        val dir = arg("gameDir")?.let(::File)
        assumeTrue("needs -e gameDir <a folder with games>", dir?.isDirectory == true)
        return dir!!
    }

    /** A folder the tests may change, with a copy of a small game. */
    fun scratchDir(): File {
        Device.grantAllFilesAccess()
        val dir = arg("scratchDir")?.let(::File)
        assumeTrue("needs -e scratchDir <a folder with a COPY of a small game>", dir?.isDirectory == true)
        return dir!!
    }

    fun dlcPackage(): File {
        Device.grantAllFilesAccess()
        val file = arg("dlcPackage")?.let(::File)
        assumeTrue("needs -e dlcPackage <a DLC or title update package>", file?.isFile == true)
        return file!!
    }

    fun flag(name: String): Boolean = arg(name) == "true"

    /** The tests' own folder in shared storage (made and removed by them). */
    fun workDir(name: String): File {
        Device.grantAllFilesAccess()
        return File("/storage/emulated/0/Download/xendroid-roadmap/$name")
    }

    /** Measurements the roadmap asks to write down, kept in shared storage (the test package is
     *  uninstalled after the run) and in logcat (tag XendroidRoadmap). */
    fun note(item: Int, text: String) {
        val line = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} item $item: $text"
        Log.i("XendroidRoadmap", line)
        runCatching {
            File("/storage/emulated/0/Download/xendroid-roadmap").apply { mkdirs() }
                .resolve("results.txt").appendText(line + "\n")
        }
    }
}

/**
 * The test package's library pointed at [folders] for one test (its own library, never the
 * .debug app's); the folders it had come back afterwards. [freshHistory] also forgets the titles
 * seen before (L06), so "games no longer in the library" only counts this test's.
 */
class Library(folders: List<File>, freshHistory: Boolean = true) : AutoCloseable {
    private val context = Device.context
    private val prefs = PreferencesStore(context)
    private val saved: List<String> = runBlocking { prefs.gameDirPaths.first() }

    init {
        Device.requireTestPackage()
        FirstRunStore.markDone(context)
        if (freshHistory) File(context.filesDir, "library").deleteRecursively()
        runBlocking {
            saved.forEach { prefs.removeGameDirPath(it) }
            folders.forEach { prefs.addGameDirPath(it.path) }
        }
    }

    fun add(path: String) = runBlocking { prefs.addGameDirPath(path) }
    fun remove(path: String) = runBlocking { prefs.removeGameDirPath(path) }

    /** A library view model as the screen has it; a new one is the app opened again. */
    fun viewModel(): GameLibraryViewModel {
        var vm: GameLibraryViewModel? = null
        Device.instrumentation.runOnMainSync {
            vm = AppContainer(context).libraryViewModelFactory().create(GameLibraryViewModel::class.java)
        }
        return vm!!
    }

    override fun close() = runBlocking {
        prefs.gameDirPaths.first().forEach { prefs.removeGameDirPath(it) }
        saved.forEach { prefs.addGameDirPath(it) }
    }

    companion object {
        /** A first scan reads every game's header (a big library takes minutes). */
        const val SCAN_TIMEOUT_MS = 15 * 60 * 1000L
    }
}

/** Scans again (pull to refresh) and returns the list once the scan, and any queued one, ended. */
fun GameLibraryViewModel.rescan(what: String, timeoutMs: Long = Library.SCAN_TIMEOUT_MS): LibraryUiState.Loaded {
    Device.instrumentation.runOnMainSync { refresh() }
    Device.await(isRefreshing, "$what to finish", timeoutMs) { !it }
    return state.value as? LibraryUiState.Loaded ?: throw AssertionError("$what: ${state.value}")
}

fun LibraryUiState.Loaded.keys(): Set<String> = games.map { it.identityKey }.toSet()
