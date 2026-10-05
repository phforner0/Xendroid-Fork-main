package xendroid.compose.ui.about

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.format.Formatter
import xendroid.compose.BuildConfig
import xendroid.compose.Emulator
import xendroid.compose.R
import xendroid.compose.core.EmulatorRuntime

/** What a problem report needs to know about this phone, as rows (label, value) and as text to copy. */
object DeviceInfo {
    @Volatile private var coreText: String? = null
    private val coreLock = Any()

    /**
     * The core's own report (JNI; it creates a Vulkan instance to ask), read once per process and
     * off the main thread; null while the core is not loaded. [CoreReport] reads it for people.
     */
    fun coreReportText(): String? {
        coreText?.let { return it }
        // One core call at a time: About asks for the device rows and the full report together,
        // and the second caller gets the first one's report instead of asking the core again.
        synchronized(coreLock) {
            coreText?.let { return it }
            return runCatching { Emulator.get?.simple_device_info() }.getOrNull()?.ifBlank { null }?.also { coreText = it }
        }
    }

    fun coreReport(): CoreReport? = CoreReport.parse(coreReportText())

    /** Screen tests: the next read asks the core again. */
    @androidx.annotation.VisibleForTesting
    internal fun forgetCoreReport() { coreText = null }

    fun rows(context: Context): List<Pair<Int, String>> {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ").ifBlank { null }
        } else null
        val memory = runCatching {
            ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem
        }.getOrNull()?.takeIf { it > 0 }
        // The core's report summed up (hundreds of lines in full: About shows those on request).
        val core = coreReport()
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return listOfNotNull(
            R.string.xd_ab_k_device to if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "${Build.MODEL} ($maker)",
            soc?.let { R.string.xd_ab_k_soc to it },
            R.string.xd_ab_k_gpu to (EmulatorRuntime.gpuDeviceName ?: core?.gpu ?: "—"),
            core?.coresLine?.let { R.string.xd_ab_k_cpu to it + (core.isa?.let { isa -> " · $isa" } ?: "") },
            core?.takeIf { it.cpuFeatures.isNotEmpty() }?.let { r ->
                R.string.xd_ab_k_cpu_features to listOf("${r.cpuFeatures.size}", r.notableFeatures.take(4).joinToString(", ")).filter { it.isNotEmpty() }.joinToString(" · ")
            },
            core?.vulkan?.let { R.string.xd_ab_k_vulkan to context.resources.getQuantityString(R.plurals.xd_ab_v_vulkan, core.extensions.size, it, core.extensions.size) },
        ) + core?.notes.orEmpty().map { R.string.xd_ab_k_core_note to it } + listOfNotNull(
            R.string.xd_ab_k_android to context.getString(R.string.xd_ab_v_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            memory?.let { R.string.xd_ab_k_memory to Formatter.formatShortFileSize(context, it) },
            R.string.xd_ab_k_abi to Build.SUPPORTED_ABIS.joinToString(", "),
        )
    }

    /** Version and device, one line each, for a problem report; the core's full report last. */
    fun text(context: Context): String =
        context.getString(R.string.app_name) + " ${BuildConfig.VERSION_NAME}\n" + rows(context).joinToString("\n") { (label, value) -> context.getString(label) + ": " + value } +
            coreReportText()?.let { "\n\n" + it.trimEnd() }.orEmpty()

    fun copy(context: Context) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text(context)))
    }
}
