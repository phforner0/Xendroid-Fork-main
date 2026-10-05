package xendroid.compose.screens

import android.os.Environment
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.AppContainer
import xendroid.compose.Utils
import xendroid.compose.core.ContentPaths
import xendroid.compose.core.SessionLogs
import xendroid.compose.core.StorageAccess
import xendroid.compose.saves.ContentTrash
import xendroid.compose.sessions.NativeBacktrace
import xendroid.compose.sessions.ProcessFate
import xendroid.compose.sessions.RunPerformance
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionRunStore
import xendroid.compose.shots.FakeCore
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.SampleArt
import xendroid.compose.shots.SampleLibrary
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.screen
import xendroid.compose.shots.shot
import xendroid.compose.ui.benchmark.BenchmarkLinks
import xendroid.compose.ui.benchmark.BenchmarkScreen
import xendroid.compose.ui.content.ContentLinks
import xendroid.compose.ui.content.ContentManagerViewModel
import xendroid.compose.ui.content.ContentScreen
import xendroid.compose.ui.content.ContentSections
import xendroid.compose.ui.content.InstallContentViewModel
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.diagnostics.DiagnosticsLinks
import xendroid.compose.ui.diagnostics.DiagnosticsScreen

/** Batch 5 "depois": Content (every game's and one game's), Diagnostics and Compare runs. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Lote5Shots {
    @get:Rule val compose = createComposeRule()

    private val mb = 1024L * 1024
    private val gameName: (String) -> String? = { id -> SampleLibrary.games.firstOrNull { it.titleId == id }?.name }

    /** Each sample game's cover as a file the screens show. */
    private val gameArt: (String) -> Any? = { id ->
        SampleLibrary.games.firstOrNull { it.titleId == id }?.let { g ->
            File(Fixture.context.cacheDir, "cover-$id.png").apply { if (!isFile) writeBytes(SampleArt.coverPng(g)) }
        }
    }

    // ------------------------------------------------------------------ content

    /** A package folder of [kb] KB, as the core leaves it under the machine's content. */
    private fun pkg(title: String, type: Int, dir: String, kb: Int) {
        File(ContentPaths.contentDir(title, type), dir).apply { mkdirs() }.let { File(it, "data").writeBytes(ByteArray(kb * 1024)) }
    }

    /** A file of [bytes] (sparse) starting with [magic], in Downloads. */
    private fun download(name: String, magic: String, bytes: Long) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        RandomAccessFile(File(dir, name), "rw").use { it.setLength(bytes); it.seek(0); it.write(magic.toByteArray(Charsets.ISO_8859_1)) }
    }

    /** DLC and title updates of three games, two packages in the trash, four files in Downloads. */
    private fun contentWorld() {
        val dlc = ContentPaths.DLC_CONTENT_TYPE
        val tu = ContentPaths.TU_CONTENT_TYPE
        FakeCore.content["4D5307E6:$dlc"] = listOf(FakeCore.item("HEROIC", "Pacote Heroico", 392 * mb), FakeCore.item("LEGENDARY", "Pacote Lendário", 370 * mb))
        FakeCore.content["4D5307E6:$tu"] = listOf(FakeCore.item("TU12", "Title Update 12", 31 * mb))
        FakeCore.content["4D5309C9:$dlc"] = listOf(FakeCore.item("RALLY", "Rally Expansion", 1210 * mb))
        FakeCore.content["4D5309C9:$tu"] = listOf(FakeCore.item("TU3", "Title Update 3", 18 * mb))
        FakeCore.content["5454082B:$dlc"] = listOf(FakeCore.item("UNDEAD", "Undead Nightmare", 1126 * mb), FakeCore.item("LIARS", "Liars and Cheats", 402 * mb))
        listOf("4D5307E6" to dlc, "4D5307E6" to tu, "4D5309C9" to dlc, "4D5309C9" to tu, "5454082B" to dlc).forEach { (t, type) ->
            ContentPaths.contentDir(t, type).mkdirs()
        }
        // Removed earlier: in the trash with their size and the day they went.
        val now = System.currentTimeMillis()
        pkg("4D5307E6", tu, "TU11", 30 * 1024)
        pkg("5454082B", dlc, "OUTLAWS", 61 * 1024)
        StorageAccess.acquire().use { lease ->
            ContentTrash(ContentPaths.contentRoot(), clock = { now - 3 * 86_400_000L }).moveToTrash(lease, "4D5307E6", tu, "TU11", "Title Update 11")
            ContentTrash(ContentPaths.contentRoot(), clock = { now - 86_400_000L }).moveToTrash(lease, "5454082B", dlc, "OUTLAWS", "Outlaws to the End")
        }
        download("Halo 3 - Mythic Map Pack", "CON ", 880 * mb)
        download("TU13_4D5307E6", "LIVE", 31 * mb)
        download("Forza Horizon - Pacote de carros", "PIRS", 512 * mb)
        download("Geometry Wars 2 (XBLA)", "LIVE", 46 * mb)
        download("saves-halo3.zip", "PK\u0003\u0004", 3 * mb)
        FakeCore.headers["Halo 3 - Mythic Map Pack"] = FakeCore.header("4D5307E6", dlc, "Pacote Mítico", 880 * mb)
        FakeCore.headers["TU13_4D5307E6"] = FakeCore.header("4D5307E6", tu, "Title Update 13", 31 * mb)
        FakeCore.headers["Forza Horizon - Pacote de carros"] = FakeCore.header("4D5309C9", dlc, "Pacote de carros Alpinestars", 512 * mb)
        FakeCore.headers["Geometry Wars 2 (XBLA)"] = FakeCore.header("584108FF", ContentPaths.ARCADE_TITLE_CONTENT_TYPE, "Geometry Wars: Retro Evolved 2", 46 * mb)
        Fixture.settle(3)
    }

    private fun content(mode: InputMode, section: String? = null, titleId: String? = null) {
        contentWorld()
        val container = AppContainer(Fixture.context)
        val vm = container.gameContentManagerViewModelFactory(titleId).create(ContentManagerViewModel::class.java)
        val installer = if (titleId == null) container.installContentViewModelFactory().create(InstallContentViewModel::class.java) else null
        Fixture.settleUntil { vm.listState.value is ContentManagerViewModel.ListState.Loaded }
        compose.app(mode) {
            ContentScreen(vm, onBack = {}, installer = installer, titleId = titleId, gameName = titleId?.let { gameName(it) }.orEmpty(),
                art = titleId?.let(gameArt), links = ContentLinks(gameName, gameArt), initialSection = section)
        }
        Fixture.settle(10)
        if (section == ContentSections.INSTALL) Fixture.settleUntil { vm.found.value != null }
        Fixture.settle(10)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun contentAll() { content(InputMode.TOUCH); compose.shot("lote5/depois-conteudo") }
    @Config(qualifiers = Phone.PORT) @Test fun contentAllPort() { content(InputMode.TOUCH); compose.shot("lote5/depois-conteudo-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun contentAllController() { content(InputMode.CONTROLLER); compose.shot("lote5/depois-conteudo-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun contentTrash() { content(InputMode.TOUCH, ContentSections.TRASH); compose.shot("lote5/depois-conteudo-lixeira") }
    @Config(qualifiers = Phone.LAND) @Test fun contentInstall() { content(InputMode.TOUCH, ContentSections.INSTALL); compose.shot("lote5/depois-instalar-conteudo") }
    @Config(qualifiers = Phone.PORT) @Test fun contentInstallPort() { content(InputMode.TOUCH, ContentSections.INSTALL); compose.shot("lote5/depois-instalar-conteudo-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun gameContent() { content(InputMode.TOUCH, titleId = "4D5307E6"); compose.shot("lote5/depois-conteudo-do-jogo") }
    @Config(qualifiers = Phone.LAND) @Test fun gameContentUpdates() {
        content(InputMode.TOUCH, ContentSections.UPDATES, titleId = "4D5307E6"); compose.shot("lote5/depois-conteudo-do-jogo-atualizacoes")
    }
    @Config(qualifiers = Phone.LAND) @Test fun gameContentController() {
        content(InputMode.CONTROLLER, titleId = "4D5307E6"); compose.shot("lote5/depois-conteudo-do-jogo-controle")
    }
    @Config(qualifiers = Phone.LAND) @Test fun removeContent() {
        content(InputMode.TOUCH, titleId = "4D5307E6")
        compose.onAllNodes(hasText("Remover") and hasClickAction())[0].performClick()
        Fixture.settle(10)
        compose.screen("lote5/depois-remover-conteudo")
    }

    // ------------------------------------------------------------------ diagnostics and runs

    private val driver = Fixture.driver
    private val r2 = driver.copy(driverInfo = "25.3.0 r2", uuid = "c0ffee2")

    /** Seconds by FPS around [target]: most at it, a few just under. */
    private fun fps(target: Int, seconds: Int): List<Int> = List(target + 31) { f ->
        when (f) {
            target -> seconds * 70 / 100
            target - 1 -> seconds * 14 / 100
            target - 2 -> seconds * 8 / 100
            in target - 6..target - 3 -> seconds * 2 / 100
            else -> 0
        }
    }

    private fun frameTimes(target: Int, frames: Long): List<Long> {
        val budget = 1000 / target
        return List(RunPerformance.FRAME_TIME_OPEN_BUCKET + 1) { ms ->
            when (ms) {
                budget -> frames * 70 / 100
                budget - 1, budget + 1 -> frames * 13 / 100
                in budget + 2..budget + 12 -> frames / 300
                else -> 0
            }
        }
    }

    private fun perf(target: Int, seconds: Int, startC: Float, limit: Int = 30) = RunPerformance(
        fpsHistogram = fps(target, seconds), presentSubmissions = seconds * target.toLong(),
        batteryStartC = startC, batteryMaxC = startC + 7.5f, batteryEndC = startC + 6f,
        frameTimeHistogramMs = frameTimes(target, seconds * target.toLong()), pipelineCreations = 1830, pipelineCreationMs = 6400,
        firstFrameSeconds = 7, fpsLimits = listOf(limit), displayHz = listOf(120), guestRefreshCap = true)

    private val clock = longArrayOf(0)
    private val store by lazy { SessionRunStore(File(xendroid.compose.Application.get_internal_data_dir(), "session-runs"), clock = { clock[0] }) }

    /** A run of [title] started [minutesAgo] ago, lasting [minutes]; finished as [state] ([reason]). */
    private fun run(title: String, minutesAgo: Long, minutes: Long, state: RunState, reason: String, perf: RunPerformance?,
                    d: xendroid.compose.driver.DriverIdentity = driver, crash: NativeBacktrace? = null, markers: Int = 0) {
        val now = System.currentTimeMillis()
        clock[0] = now - minutesAgo * 60_000
        val path = SampleLibrary.byId(title).path
        val begun = store.begin("library", path, "v412", 4242)
        clock[0] += 8_000
        store.running(begun.runId, title, d, Fixture.P1)
        clock[0] += minutes * 60_000
        if (state == RunState.ENDED) store.finish(begun.runId, RunState.ENDED, reason, perf)
        else {
            store.heartbeat(begun.runId, perf)
            store.reconcile { ProcessFate(alive = false, crashed = state == RunState.FAILED, reason = reason, backtrace = crash) }
        }
        if (markers > 0) store.saveEvents(begun.runId, xendroid.compose.sessions.RunEventLog(events =
            List(markers) { xendroid.compose.sessions.RunEvent(60_000L * (it + 1), "marker", "scene") }))
    }

    private fun logcatLine(millis: Long) = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(millis)) +
        "  4242  4242 I SessionLogs: capture started\n"

    /** A shelved session's archive ending [hoursAgo] ago, begun [spanHours] before. */
    private fun shelved(stamp: String, hoursAgo: Double, spanHours: Double, titles: List<String>, kb: Int) {
        val dir = File(File(Utils.get_log_file_path()).parentFile, "logs").apply { mkdirs() }
        val end = System.currentTimeMillis() - (hoursAgo * 3_600_000).toLong()
        val zip = File(dir, "session_$stamp.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            // Random bytes stand for the logs: they do not compress, so the archive has the size given.
            val noise = ByteArray(kb * 1024).also { java.util.Random(kb.toLong()).nextBytes(it) }
            z.putNextEntry(ZipEntry("logcat.txt")); z.write(logcatLine(end - (spanHours * 3_600_000).toLong()).toByteArray())
            z.write(noise, 0, noise.size / 2); z.closeEntry()
            z.putNextEntry(ZipEntry("xe.log")); z.write(noise, noise.size / 2, noise.size / 2); z.closeEntry()
            z.putNextEntry(ZipEntry("context.json")); z.write("""{"titleIds":[${titles.joinToString { "\"$it\"" }}],"appVersion":"v412"}""".toByteArray()); z.closeEntry()
        }
        zip.setLastModified(end)
    }

    /** Four kept sessions: a normal one, one Android ended, one with a crash and a retry, the current one. */
    private fun diagnosticsWorld() {
        FakeCore.profiles = listOf(FakeCore.profile(Fixture.P1, "ChefeMaster117"))
        val sigsegv = NativeBacktrace(thread = "GPU Commands (tid 4321)", signal = "SIGSEGV (SEGV_MAPERR) at 0x0000000000000010",
            cause = "null pointer dereference", frames = listOf(
                "#00 pc 00000000012af40  libxenia-app.so (xe::gpu::vulkan::VulkanCommandProcessor::IssueDraw+412)",
                "#01 pc 0000000001190c8  libxenia-app.so (xe::gpu::CommandProcessor::ExecutePacketType3_DRAW_INDX+96)",
                "#02 pc 000000000118d1c  libxenia-app.so (xe::gpu::CommandProcessor::ExecutePacket+188)"))
        // Oldest first, so each run lands in its session.
        run("4D5309C9", minutesAgo = (3 * 24 + 2) * 60L, minutes = 41, state = RunState.ENDED, reason = "exited from the menu", perf = perf(30, 41 * 60, 31f))
        shelved("20261001-200000", hoursAgo = 3 * 24 + 1.0, spanHours = 1.5, titles = listOf("4D5309C9"), kb = 820)
        run("5454082B", minutesAgo = (24 + 3) * 60L, minutes = 22, state = RunState.INTERRUPTED, reason = "killed by Android (low memory)", perf = perf(30, 22 * 60, 33f))
        shelved("20261003-060000", hoursAgo = 24 + 2.0, spanHours = 1.5, titles = listOf("5454082B"), kb = 1460)
        run("4D5307E6", minutesAgo = 5 * 60L, minutes = 2, state = RunState.FAILED, reason = "native crash (SIGSEGV)", perf = null, crash = sigsegv)
        run("4D5307E6", minutesAgo = 4 * 60L + 40, minutes = 53, state = RunState.ENDED, reason = "exited from the menu", perf = perf(30, 53 * 60, 32.5f))
        shelved("20261004-050000", hoursAgo = 3.0, spanHours = 2.5, titles = listOf("4D5307E6"), kb = 2310)
        run("4D5307E6", minutesAgo = 140L, minutes = 53, state = RunState.ENDED, reason = "exited from the menu", perf = perf(30, 53 * 60, 32.5f))
        // The current session: still being written.
        val log = File(Utils.get_log_file_path()).apply { parentFile?.mkdirs(); writeBytes(ByteArray(640 * 1024)) }
        File(log.parentFile, "logs/${SessionLogs.CAPTURE_NAME}").writeText(logcatLine(System.currentTimeMillis() - 2 * 3_600_000L - 40 * 60_000L))
        Fixture.settle(3)
    }

    private fun diagnostics(mode: InputMode, titleId: String? = null) {
        diagnosticsWorld()
        compose.app(mode) { DiagnosticsScreen(titleId, onBack = {}, links = DiagnosticsLinks(gameName, gameArt)) }
        Fixture.settle(30)
        compose.waitForIdle()
        Fixture.settle(10)
    }

    @Config(qualifiers = Phone.LAND) @Test fun diagnosticsAll() { diagnostics(InputMode.TOUCH); compose.shot("lote5/depois-diagnostico") }
    @Config(qualifiers = Phone.PORT) @Test fun diagnosticsPort() { diagnostics(InputMode.TOUCH); compose.shot("lote5/depois-diagnostico-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun diagnosticsController() { diagnostics(InputMode.CONTROLLER); compose.shot("lote5/depois-diagnostico-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun diagnosticsGame() { diagnostics(InputMode.TOUCH, "4D5307E6"); compose.shot("lote5/depois-diagnostico-do-jogo") }
    @Config(qualifiers = Phone.LAND) @Test fun diagnosticsSummary() {
        diagnostics(InputMode.TOUCH, "4D5307E6")
        // The session with the crash: the second "Resumo" (the current one is first).
        compose.onAllNodes(hasText("Resumo") and hasClickAction())[1].performClick()
        Fixture.settle(10)
        compose.screen("lote5/depois-diagnostico-resumo")
    }

    /** Halo 3 measured five times (one short run left out, then A B B A with a driver change), Forza twice. */
    private fun benchWorld(): Map<String, Char> {
        run("4D5307E6", minutesAgo = 300, minutes = 1, state = RunState.ENDED, reason = "exited from the menu", perf = perf(38, 42, 30f, limit = 0), markers = 1)
        run("4D5307E6", minutesAgo = 280, minutes = 3, state = RunState.ENDED, reason = "exited from the menu", perf = perf(41, 150, 31f, limit = 0), markers = 1)
        run("4D5307E6", minutesAgo = 260, minutes = 3, state = RunState.ENDED, reason = "exited from the menu", perf = perf(46, 142, 32f, limit = 0), d = r2, markers = 1)
        run("4D5307E6", minutesAgo = 240, minutes = 3, state = RunState.ENDED, reason = "exited from the menu", perf = perf(45, 160, 33f, limit = 0), d = r2, markers = 1)
        run("4D5307E6", minutesAgo = 220, minutes = 3, state = RunState.ENDED, reason = "exited from the menu", perf = perf(39, 155, 32.5f, limit = 0), markers = 1)
        run("4D5309C9", minutesAgo = 26 * 60, minutes = 41, state = RunState.ENDED, reason = "exited from the menu", perf = perf(30, 41 * 60, 31f))
        run("4D5309C9", minutesAgo = 25 * 60, minutes = 12, state = RunState.ENDED, reason = "exited from the menu", perf = perf(30, 12 * 60, 34f))
        val halo = store.runs().filter { it.titleId == "4D5307E6" }.sortedBy { it.startedAt }.map { it.runId }
        return mapOf(halo[1] to 'A', halo[2] to 'B', halo[3] to 'B', halo[4] to 'A')
    }

    private fun compare(mode: InputMode, marked: Boolean = true) {
        val sides = benchWorld()
        val labels = mutableStateMapOf<String, Char>().apply { if (marked) putAll(sides) }
        compose.app(mode) { BenchmarkScreen(onBack = {}, titleId = "4D5307E6", links = BenchmarkLinks(gameName), labels = labels) }
        Fixture.settle(30)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun compareRuns() { compare(InputMode.TOUCH); compose.shot("lote5/depois-comparar-execucoes") }
    @Config(qualifiers = Phone.PORT) @Test fun compareRunsPort() { compare(InputMode.TOUCH); compose.shot("lote5/depois-comparar-execucoes-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun compareRunsController() { compare(InputMode.CONTROLLER); compose.shot("lote5/depois-comparar-execucoes-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun compareRunsUnmarked() { compare(InputMode.TOUCH, marked = false); compose.shot("lote5/depois-comparar-sem-marcar") }
    @Config(qualifiers = Phone.LAND) @Test fun compareHow() {
        compare(InputMode.TOUCH)
        compose.onAllNodes(hasText("Como medir") and hasClickAction())[0].performClick()
        Fixture.settle(10)
        compose.screen("lote5/depois-como-medir")
    }
}
