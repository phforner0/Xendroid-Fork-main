package xendroid.compose.core

import android.app.GameManager
import android.app.GameState
import android.content.Context
import android.os.Build
import android.util.Log

/** Where the session stands, as Android's GameManager hears it. */
enum class GamePhase(val label: String) {
    LOADING("loading"),
    PLAYING("playing"),
    /** The in-game menu or a pause: gameplay that may be interrupted. */
    PAUSED("paused"),
    NONE("not playing"),
}

/** Loading until the first guest frame, playing while it runs, paused under the menu or a pause,
 *  nothing in the background. */
fun gamePhase(foreground: Boolean, firstFrame: Boolean, menuOrPaused: Boolean): GamePhase = when {
    !foreground -> GamePhase.NONE
    !firstFrame -> GamePhase.LOADING
    menuOrPaused -> GamePhase.PAUSED
    else -> GamePhase.PLAYING
}

/**
 * Tells the platform what the session is doing (Android 13+ GameManager.setGameState), so the
 * device's own game booster engages during gameplay and backs off under the menu (Bannerlator
 * 35517de9); the manifest's appCategory="game" is what makes the platform listen. Below
 * Android 13 it only answers [systemMode]. Never throws: a hint must not touch a game's launch.
 */
class GameModeSignal(context: Context) {
    private val manager: GameManager? =
        if (Build.VERSION.SDK_INT >= 31) runCatching { context.getSystemService(GameManager::class.java) }.getOrNull() else null
    private var last: GamePhase? = null

    /** Sends [phase] when it changed; true when it did (the caller records it). */
    fun update(phase: GamePhase): Boolean {
        if (phase == last) return false
        last = phase
        if (Build.VERSION.SDK_INT >= 33) {
            val mode = when (phase) {
                GamePhase.LOADING, GamePhase.PLAYING -> GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE
                GamePhase.PAUSED -> GameState.MODE_GAMEPLAY_INTERRUPTIBLE
                GamePhase.NONE -> GameState.MODE_NONE
            }
            runCatching { manager?.setGameState(GameState(phase == GamePhase.LOADING, mode)) }
                .onFailure { Log.w("GameModeSignal", "setGameState failed", it) }
        }
        return true
    }

    /** The mode the user picked for XenDroid in the system's game settings (Android 12+). */
    fun systemMode(): String {
        if (Build.VERSION.SDK_INT < 31) return "unavailable before Android 12"
        val mode = runCatching { manager?.gameMode }.getOrNull() ?: return "unavailable"
        return when (mode) {
            GameManager.GAME_MODE_STANDARD -> "standard"
            GameManager.GAME_MODE_PERFORMANCE -> "performance"
            GameManager.GAME_MODE_BATTERY -> "battery"
            GameManager.GAME_MODE_UNSUPPORTED -> "unsupported"
            else -> "mode $mode"
        }
    }
}
