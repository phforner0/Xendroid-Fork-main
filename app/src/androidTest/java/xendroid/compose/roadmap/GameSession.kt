package xendroid.compose.roadmap

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assume.assumeTrue
import xendroid.compose.R
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionRun
import xendroid.compose.sessions.SessionRuns
import xendroid.compose.ui.library.ACTION_LAUNCH_GAME
import xendroid.compose.ui.library.EXTRA_GAME_URI

/**
 * One game started for real in the test package's game process (:emu), with the launch the
 * library uses, then driven like a player: the menu through Back, Exit through the menu, Home.
 * What happened is read from the run record the game process writes (sessions/), the same one
 * the library's sheet shows. The game file comes from `-e game <file>` or, without it, the first
 * game of `-e gameDir`; it is only read.
 */
class GameSession(val game: File) : AutoCloseable {
    private val context = Device.context
    private val pkg = context.packageName
    val device: UiDevice = UiDevice.getInstance(Device.instrumentation)
    private val since = System.currentTimeMillis()

    fun start(extras: Intent.() -> Unit = {}) {
        Device.grantAllFilesAccess()
        context.startActivity(Intent(ACTION_LAUNCH_GAME).apply {
            setPackage(pkg)
            putExtra(EXTRA_GAME_URI, game.path)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            extras()
        })
    }

    /** This session's run: the newest one of the game started since the session began. */
    fun record(): SessionRun? = SessionRuns.store().runs()
        .filter { it.startedAt >= since - 1_000 && it.gamePath == game.path }.maxByOrNull { it.startedAt }

    fun awaitRecord(what: String, timeoutMs: Long = BOOT_TIMEOUT_MS, matches: (SessionRun) -> Boolean): SessionRun {
        var found: SessionRun? = null
        Device.waitUntil(what, timeoutMs) { record()?.takeIf(matches)?.also { found = it } != null }
        return found!!
    }

    /** The core started the title (its Title ID is known). */
    fun awaitRunning(): SessionRun = awaitRecord("the game running") { it.state == RunState.RUNNING || it.state.final }
        .also { check(it.state == RunState.RUNNING) { "The run ended before the game ran: ${it.state} ${it.endReason}" } }

    /** The first guest frame was shown (written with the next 30 s heartbeat). */
    fun awaitFirstFrame(): SessionRun = awaitRecord("the first frame") { it.performance?.firstFrameSeconds != null || it.state.final }
        .also { check(!it.state.final) { "The run ended before its first frame: ${it.state} ${it.endReason}" } }

    fun play(seconds: Int) = SystemClock.sleep(seconds * 1000L)

    fun emuPid(): String = Device.shell("pidof $pkg:emu").trim()

    fun text(id: Int, vararg args: Any): String = Device.string(id, *args)

    fun find(text: String, timeoutMs: Long = 15_000): UiObject2 =
        device.wait(Until.findObject(By.text(text)), timeoutMs) ?: throw AssertionError("\"$text\" not on screen")

    fun has(text: String, timeoutMs: Long = 10_000): Boolean = device.wait(Until.hasObject(By.text(text)), timeoutMs) == true

    /**
     * An option of the open in-game menu, scrolled into view first when needed: the menu shows
     * what fits the screen, and on a phone in landscape the lower options of a tab are below
     * it (the first run on a phone missed Win-FG and the driver line there). The options are
     * the tallest scrollable list (the tabs scroll sideways above them).
     */
    fun findInMenu(selector: BySelector, timeoutMs: Long = 15_000): UiObject2 {
        device.wait(Until.findObject(selector), 2_000)?.let { return it }
        val list = device.wait(Until.findObjects(By.scrollable(true)), timeoutMs)
            ?.maxByOrNull { it.visibleBounds.height() } ?: throw AssertionError("no scrollable menu for $selector")
        list.scrollUntil(Direction.DOWN, Until.findObject(selector))?.let { return it }
        list.scrollUntil(Direction.UP, Until.findObject(selector))?.let { return it }
        throw AssertionError("$selector not in the menu")
    }

    fun findInMenu(text: String): UiObject2 = findInMenu(By.text(text))

    /** Back opens the in-game menu (its footer has Continue and Exit game). */
    fun openMenu() {
        device.pressBack()
        find(text(R.string.menu_continue))
    }

    fun closeMenu() = find(text(R.string.menu_continue)).click()

    fun page(tab: Int) = find(text(tab)).click()

    /** Exit through the menu and its confirmation; the game process ends. */
    fun exitByMenu() {
        openMenu()
        find(text(R.string.menu_exit_game)).click()
        find(text(R.string.menu_exit_question))
        find(text(R.string.menu_exit_game)).click()
        Device.waitUntil("the game process to end", 60_000) { emuPid().isEmpty() }
    }

    /** A signal to the game process, as the system or a crash would end it (debuggable build). */
    fun signal(name: String) {
        val pid = emuPid()
        check(pid.isNotEmpty()) { "no game process" }
        Device.shell("run-as $pkg kill -$name $pid")
        Device.waitUntil("the game process to end after SIG$name", 30_000) { emuPid().isEmpty() }
    }

    override fun close() {
        if (emuPid().isEmpty()) return
        runCatching { exitByMenu() }.onFailure { runCatching { signal("KILL") } }
    }

    companion object {
        /** A first boot compiles the title's pipelines (minutes on a cold cache). */
        const val BOOT_TIMEOUT_MS = 10 * 60 * 1000L

        /** The game to start: `-e game`, else the first game the library finds in `-e gameDir`. */
        fun game(): File {
            InstrumentationRegistry.getArguments().getString("game")?.takeIf { it.isNotBlank() }?.let { path ->
                Device.grantAllFilesAccess()
                val file = File(path)
                assumeTrue("-e game: $path not found", file.exists())
                return file
            }
            return Library(listOf(GameRun.gameDir())).use { library ->
                val games = library.viewModel().rescan("the games folder").games
                assumeTrue("no game in the games folder", games.isNotEmpty())
                File(games.sortedBy { it.name.lowercase() }.first().launchUri)
            }
        }
    }
}
