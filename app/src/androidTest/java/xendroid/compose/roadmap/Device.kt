package xendroid.compose.roadmap

import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import xendroid.compose.Utils

/**
 * Shared helpers of the device roadmap tests (docs/auditoria-s0-2026-10-01.md §4, one test class
 * per item). They run in the xendroid.compose.uitest package (build type uitest): its data,
 * settings and grants are its own, so nothing here touches the games, saves or settings of the
 * .debug app installed on the same phone. Run with `./gradlew :app:connectedUitestAndroidTest`.
 */
object Device {
    val instrumentation: Instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context: Context get() = instrumentation.targetContext

    /** Stops a test before it writes anything outside the test package. */
    fun requireTestPackage() {
        check(context.packageName.endsWith(".uitest")) {
            "Roadmap tests change app data; they run only in the .uitest package, not ${context.packageName}"
        }
    }

    /** Runs [command] as the shell user (appops, pidof) and returns what it printed. */
    fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().toString(Charsets.UTF_8) }

    /** All Files Access for the test package, as a player grants it once in system settings. */
    fun grantAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        shell("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        waitUntil("All Files Access granted") { Environment.isExternalStorageManager() }
    }

    /** The emulator's user data of the test package (Android/data/<package>/files/compose). */
    val storageRoot: File get() = File(Utils.get_storage_root_path())

    fun waitUntil(what: String, timeoutMs: Long = 10_000, check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!check()) {
            if (SystemClock.uptimeMillis() > end) throw AssertionError("Timed out waiting for: $what")
            SystemClock.sleep(50)
        }
    }

    /** The first value of [flow] that [matches], or a failure naming [what] after [timeoutMs]. */
    fun <T> await(flow: Flow<T>, what: String, timeoutMs: Long = 30_000, matches: (T) -> Boolean): T = runBlocking {
        try {
            withTimeout(timeoutMs) { flow.first(matches) }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("Timed out waiting for: $what")
        }
    }

    /** A file handed over as a content:// Uri with its display name, as the system picker does
     *  (through the app's own FileProvider). */
    fun pickedFile(name: String, bytes: ByteArray): Pair<File, Uri> {
        val dir = File(context.cacheDir, "shared-logs/roadmap").apply { mkdirs() }
        val file = File(dir, name).apply { writeBytes(bytes) }
        return file to FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    }

    fun string(id: Int, vararg args: Any): String = context.getString(id, *args)
    fun plural(id: Int, count: Int, vararg args: Any): String = context.resources.getQuantityString(id, count, *args)

    /** The app's texts in [tag] ("pt-BR", "en"), for what a test expects in that language. */
    fun resourcesIn(tag: String): Resources = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }).resources
}

/** Composes [content] with the app's texts in [tag], as with the phone set to that language. */
@Composable
fun InLanguage(tag: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = remember(tag) {
        Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
    }
    val localized = remember(tag) {
        val resources = base.createConfigurationContext(configuration).resources
        object : ContextWrapper(base) {
            override fun getResources(): Resources = resources
        }
    }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration) { content() }
}

/**
 * The app's own language (Android 13+: Settings → Apps → XenDroid → Language), for the test
 * package; [set] null goes back to the phone's language. Activities started afterwards use it.
 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
object AppLanguage {
    fun set(tag: String?) {
        val manager = Device.context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        val wanted = tag?.let { Locale.forLanguageTag(it).language } ?: Resources.getSystem().configuration.locales[0].language
        Device.waitUntil("the app in language $wanted") { Device.context.resources.configuration.locales[0].language == wanted }
    }
}

/**
 * Texts drawn cut right now: each should fit or wrap. Cut means ellipsized, past its maxLines or
 * clipped in height. Not `hasVisualOverflow`: for a plain Text, the layout the semantics action
 * hands back is laid out again at the container's whole width (Compose's "slow" layout result),
 * so every text narrower than its container read as overflowing in width (the first run on a
 * phone flagged "+", "GPU" and every tab name). Line count and height are the same in both
 * layouts, so these checks hold.
 */
fun SemanticsNodeInteractionsProvider.cutTexts(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes().mapNotNull { node ->
            val layouts = ArrayList<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.firstOrNull()?.takeIf { it.isCut() }?.layoutInput?.text?.text
        }

/** Ellipsized, past its maxLines, or taller than the space it was given (by more than a pixel). */
fun TextLayoutResult.isCut(): Boolean =
    multiParagraph.didExceedMaxLines || size.height + 1f < multiParagraph.height ||
        (0 until lineCount).any { isLineEllipsized(it) }
