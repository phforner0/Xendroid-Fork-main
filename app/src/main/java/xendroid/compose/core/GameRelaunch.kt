package xendroid.compose.core

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.core.content.getSystemService
import kotlinx.coroutines.delay

/**
 * 15e: "Try again" after a failed start. The core boots once per :emu process, so the main
 * process starts the game again only once the old :emu has ended (it exits as its activity
 * finishes); one still there after [timeoutMs] is killed, as [EmuProcessLink.killStaleEmu]
 * does before every launch from the library.
 */
object GameRelaunch {
    suspend fun awaitEmuGone(context: Context, timeoutMs: Long = 3_000) {
        val am = context.getSystemService<ActivityManager>() ?: return
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (emuRunning(am) && SystemClock.elapsedRealtime() < deadline) delay(100)
        if (emuRunning(am)) {
            EmuProcessLink.killStaleEmu(context)
            delay(300)
        }
    }

    private fun emuRunning(am: ActivityManager): Boolean = runCatching {
        am.runningAppProcesses.orEmpty().any { it.processName.endsWith(":emu") && it.pid != Process.myPid() }
    }.getOrDefault(false)
}
