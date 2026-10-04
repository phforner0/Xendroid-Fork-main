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
    fun rows(context: Context): List<Pair<Int, String>> {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ").ifBlank { null }
        } else null
        val memory = runCatching {
            ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem
        }.getOrNull()?.takeIf { it > 0 }
        // simple_device_info() is a JNI method; the core may not be loaded yet.
        val core = runCatching { Emulator.get?.simple_device_info() }.getOrNull()?.ifBlank { null }
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return listOfNotNull(
            R.string.xd_ab_k_device to if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "${Build.MODEL} ($maker)",
            soc?.let { R.string.xd_ab_k_soc to it },
            R.string.xd_ab_k_gpu to (EmulatorRuntime.gpuDeviceName ?: "—"),
            core?.let { R.string.xd_ab_k_core to it },
            R.string.xd_ab_k_android to context.getString(R.string.xd_ab_v_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            memory?.let { R.string.xd_ab_k_memory to Formatter.formatShortFileSize(context, it) },
            R.string.xd_ab_k_abi to Build.SUPPORTED_ABIS.joinToString(", "),
        )
    }

    /** Version and device, one line each, for a problem report. */
    fun text(context: Context): String =
        "XenDroid ${BuildConfig.VERSION_NAME}\n" + rows(context).joinToString("\n") { (label, value) -> context.getString(label) + ": " + value }

    fun copy(context: Context) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("XenDroid", text(context)))
    }
}
