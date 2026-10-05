package xendroid.compose.ui.advice

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.ConfigValueShape
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionAdvice
import xendroid.compose.sessions.SessionRun
import xendroid.compose.ui.design.BadgeTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdBadge
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdLink
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdText

/** Round 2: what the end-of-session suggestions remember: when they show, what is muted, what was seen. */
object SessionAdvicePrefs {
    private const val PREFS = "session_advice"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): SessionAdvice.Mode =
        runCatching { SessionAdvice.Mode.valueOf(prefs(context).getString("mode", null) ?: "ALWAYS") }.getOrDefault(SessionAdvice.Mode.ALWAYS)

    fun setMode(context: Context, mode: SessionAdvice.Mode) = prefs(context).edit { putString("mode", mode.name) }

    /** The newest run already looked at: a session is offered once. */
    fun reviewed(context: Context): String? = prefs(context).getString("reviewed", null)
    fun setReviewed(context: Context, runId: String) = prefs(context).edit { putString("reviewed", runId) }

    /** Rule ids, "rule@TITLE" and "game@TITLE" that do not show again. */
    fun muted(context: Context): Set<String> = prefs(context).getStringSet("muted", null).orEmpty().toSet()
    fun mute(context: Context, key: String) = prefs(context).edit { putStringSet("muted", muted(context) + key) }

    fun lastPerformanceShown(context: Context, titleId: String): Long = prefs(context).getLong("perf@${titleId.uppercase()}", 0L)
    fun performanceShown(context: Context, titleId: String, at: Long) = prefs(context).edit { putLong("perf@${titleId.uppercase()}", at) }

    /** "Not until the app opens again": this process only. */
    @Volatile var quietUntilRestart = false
}

/** A session worth suggesting something for: its run, the game and what is left to show. */
class SessionReview(val run: SessionRun, val titleId: String, val name: String, val advice: List<SessionAdvice.Advice>)

/**
 * Round 2: back in the app after a session (closed, failed or crashed), "How did <game> go": up
 * to three suggestions from its log and numbers, each applied to the game right there (with
 * Undo). It shows only when there is something to do, once per session; a suggestion, the game
 * or every sheet can be told not to come back, and Settings → Interface sets when they show.
 */
@Composable
fun SessionAdviceHost(gameName: suspend (titleId: String?, path: String) -> Pair<String?, String?>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var review by remember { mutableStateOf<SessionReview?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && review == null) scope.launch {
                review = withContext(Dispatchers.IO) { runCatching { SessionReviews.look(context, gameName) }.getOrNull() }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    review?.let { r -> SessionAdviceSheet(r, onDismiss = { review = null }) }
}

/** Looking for a session to review, and applying a suggestion to its game. */
object SessionReviews {
    /** The newest finished session, if it is new, worth a look and has something left to say. */
    suspend fun look(context: Context, gameName: suspend (titleId: String?, path: String) -> Pair<String?, String?>): SessionReview? {
        if (SessionAdvicePrefs.quietUntilRestart) return null
        val mode = SessionAdvicePrefs.mode(context)
        if (mode == SessionAdvice.Mode.NEVER) return null
        val store = xendroid.compose.sessions.SessionRuns.store()
        // A game process that just died may have left its run open: close it first (as the
        // app does when it comes back), so its own session is the one looked at.
        runCatching { store.reconcile(xendroid.compose.sessions.SessionRuns.fates(context)) }
        val run = store.runs().firstOrNull { it.state.final } ?: return null
        if (run.runId == SessionAdvicePrefs.reviewed(context)) return null
        SessionAdvicePrefs.setReviewed(context, run.runId)
        if (!SessionAdvice.worthALook(run)) return null
        val (title, name) = gameName(run.titleId, run.gamePath)
        val titleId = (run.titleId ?: title)?.uppercase()?.takeIf { it.matches(Regex("[0-9A-F]{8}")) && it != "00000000" } ?: return null
        val all = advise(context, run, titleId)
        val now = System.currentTimeMillis()
        val shown = SessionAdvice.shown(all, titleId, mode, SessionAdvicePrefs.muted(context),
            SessionAdvicePrefs.lastPerformanceShown(context, titleId), now)
        if (shown.isEmpty()) return null
        if (shown.any { !it.rule.error }) SessionAdvicePrefs.performanceShown(context, titleId, now)
        return SessionReview(run, titleId, name ?: java.io.File(run.gamePath).nameWithoutExtension, shown)
    }

    /** The rules over [run], with the game's values as its next session would read them. */
    fun advise(context: Context, run: SessionRun, titleId: String): List<SessionAdvice.Advice> {
        val store = ConfigStore(context)
        val game = store.openGameConfig(titleId)
        val global = runCatching { store.openLiveSnapshot() }.getOrNull()
        try {
            fun read(h: xendroid.compose.settings.ConfigHandle?, key: String) =
                h?.getString(key.substringBefore('|'), key.substringAfter('|'))
            val own = SettingsSchema.allSettings.map { it.key }.filter { read(game, it) != null }.toSet()
            val events = runCatching { xendroid.compose.sessions.SessionRuns.store().events(run.runId) }.getOrNull()
            return SessionAdvice.of(run, { key -> read(game, key) ?: read(global, key) ?: SettingsSchema.byKey[key]?.let(::defaultRaw) }, own,
                events = events)
        } finally {
            game.closeDiscard()
            global?.closeDiscard()
        }
    }

    /** Writes [advice] to the game's config; returns the values it had, for Undo (null: none of its own). */
    fun apply(context: Context, titleId: String, advice: SessionAdvice.Advice): Map<String, String?> {
        val store = ConfigStore(context)
        val game = store.openGameConfig(titleId)
        val before = try {
            advice.values.keys.associateWith { key -> game.getString(key.substringBefore('|'), key.substringAfter('|')) }
        } finally { game.closeDiscard() }
        write(store, titleId, advice.values)
        return before
    }

    fun undo(context: Context, titleId: String, before: Map<String, String?>) = write(ConfigStore(context), titleId, before)

    private fun write(store: ConfigStore, titleId: String, values: Map<String, String?>) {
        store.editGameConfig(titleId) { h ->
            values.forEach { (key, value) ->
                val s = SettingsSchema.byKey[key]
                if (value == null || s == null) h.remove(key.substringBefore('|'), key.substringAfter('|')) else h.putSetting(s, value)
            }
        }
    }

    private fun defaultRaw(s: Setting): String = when (s) {
        is Setting.Bool -> ConfigValueShape.bool(s.default)
        is Setting.IntRange -> s.default.toString()
        is Setting.ListChoice -> s.default
        is Setting.Text -> s.default
        is Setting.Action -> s.default
    }
}

/** "How did <game> go": the session in a line, the suggestions, and the ways to keep them quiet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionAdviceSheet(
    review: SessionReview,
    onDismiss: () -> Unit,
    /** Writes a suggestion to the game, returning what to undo it with (tests pass their own). */
    apply: ((SessionAdvice.Advice) -> Map<String, String?>)? = null,
    undo: ((Map<String, String?>) -> Unit)? = null,
) {
    val c = Xd.colors
    val context = LocalContext.current
    val app = context.applicationContext
    val doApply = apply ?: { a -> SessionReviews.apply(app, review.titleId, a) }
    val doUndo = undo ?: { before -> SessionReviews.undo(app, review.titleId, before) }
    val scope = rememberCoroutineScope()
    var left by remember { mutableStateOf(review.advice) }
    val applied = remember { mutableStateMapOf<SessionAdvice.Rule, Map<String, String?>>() }
    val mute: (String) -> Unit = { key -> SessionAdvicePrefs.mute(context, key) }
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.adv_title, review.name), subtitle = summary(review.run),
        actions = { XdButton(stringResource(R.string.common_close), onDismiss, kind = XdButtonKind.PRIMARY) }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in left) {
                val done = applied[a.rule]
                XdCard(Modifier.fillMaxWidth(), title = stringResource(adviceTitle(a.rule)), icon = adviceIcon(a.rule)) {
                    Text(adviceReason(a), style = XdText.bodySm, color = c.fg2)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (done == null) XdButton(stringResource(R.string.adv_apply), {
                            scope.launch { applied[a.rule] = withContext(Dispatchers.IO) { doApply(a) } }
                        }, kind = XdButtonKind.PRIMARY, size = XdButtonSize.SM, icon = XdIcons.check)
                        else {
                            XdBadge(stringResource(R.string.adv_applied), tone = BadgeTone.OK, modifier = Modifier.align(Alignment.CenterVertically))
                            XdButton(stringResource(R.string.xd_undo), {
                                scope.launch { withContext(Dispatchers.IO) { doUndo(done) }; applied.remove(a.rule) }
                            }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.reset)
                        }
                        XdLink(stringResource(R.string.adv_mute_here), {
                            mute(SessionAdvice.ruleKey(a.rule, review.titleId)); left = left - a; if (left.isEmpty()) onDismiss()
                        }, Modifier.align(Alignment.CenterVertically))
                        XdLink(stringResource(R.string.adv_mute_rule), {
                            mute(a.rule.id); left = left - a; if (left.isEmpty()) onDismiss()
                        }, Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                XdLink(stringResource(R.string.adv_quiet), { SessionAdvicePrefs.quietUntilRestart = true; onDismiss() })
                XdLink(stringResource(R.string.adv_mute_game), { mute(SessionAdvice.gameKey(review.titleId)); onDismiss() })
                XdLink(stringResource(R.string.adv_never), { SessionAdvicePrefs.setMode(context, SessionAdvice.Mode.NEVER); onDismiss() })
            }
            Text(stringResource(R.string.adv_settings_hint), style = XdText.note, color = c.fg3)
        }
    }
}

/** "42 min · 30 FPS (low 24) · ended normally" or what made it fail. */
@Composable
private fun summary(run: SessionRun): String {
    val played = run.playedMs?.let { xendroid.compose.sessions.formatPlayTime(it) }
    val p = run.performance?.takeIf { it.sampledSeconds > 0 }
    val fps = p?.fpsPercentile(0.5)?.let { median -> p.fpsPercentile(0.05)?.let { low -> stringResource(R.string.adv_fps, median, low) } }
    val ending = when (run.state) {
        RunState.FAILED -> stringResource(R.string.adv_failed, run.endReason ?: "?")
        RunState.INTERRUPTED -> stringResource(R.string.adv_interrupted, run.endReason ?: "?")
        else -> stringResource(R.string.adv_ended)
    }
    return listOfNotNull(played, fps, ending).joinToString(" · ")
}

private fun adviceTitle(rule: SessionAdvice.Rule): Int = when (rule) {
    SessionAdvice.Rule.SYSTEM_DRIVER -> R.string.adv_t_system_driver
    SessionAdvice.Rule.RESET_GAME -> R.string.adv_t_reset_game
    SessionAdvice.Rule.PIPELINES -> R.string.adv_t_pipelines
    SessionAdvice.Rule.SCALE_DOWN -> R.string.adv_t_scale
    SessionAdvice.Rule.STEADY_30 -> R.string.adv_t_steady
    SessionAdvice.Rule.AUDIO_BUFFER -> R.string.adv_t_audio
    SessionAdvice.Rule.HEAT -> R.string.adv_t_heat
}

private fun adviceIcon(rule: SessionAdvice.Rule) = when (rule) {
    SessionAdvice.Rule.SYSTEM_DRIVER -> XdIcons.chip
    SessionAdvice.Rule.RESET_GAME -> XdIcons.reset
    SessionAdvice.Rule.PIPELINES -> XdIcons.layers
    SessionAdvice.Rule.SCALE_DOWN -> XdIcons.image
    SessionAdvice.Rule.STEADY_30 -> XdIcons.chart
    SessionAdvice.Rule.AUDIO_BUFFER -> XdIcons.speaker
    SessionAdvice.Rule.HEAT -> XdIcons.thermo
}

@Composable
private fun adviceReason(a: SessionAdvice.Advice): String {
    val n = a.numbers
    return when (a.rule) {
        SessionAdvice.Rule.SYSTEM_DRIVER -> stringResource(R.string.adv_r_system_driver, n.getOrElse(0) { "?" })
        SessionAdvice.Rule.RESET_GAME -> stringResource(R.string.adv_r_reset_game, n.getOrElse(0) { "?" })
        SessionAdvice.Rule.PIPELINES -> stringResource(R.string.adv_r_pipelines, n.getOrElse(0) { "?" }, n.getOrElse(1) { "?" })
        SessionAdvice.Rule.SCALE_DOWN -> stringResource(R.string.adv_r_scale, n.getOrElse(0) { "?" }, n.getOrElse(1) { "?" }, n.getOrElse(2) { "?" })
        SessionAdvice.Rule.STEADY_30 -> stringResource(R.string.adv_r_steady, n.getOrElse(0) { "?" }, n.getOrElse(1) { "?" })
        SessionAdvice.Rule.AUDIO_BUFFER -> stringResource(R.string.adv_r_audio, n.getOrElse(0) { "?" })
        SessionAdvice.Rule.HEAT -> if (n.isEmpty()) stringResource(R.string.adv_r_heat_limit) else stringResource(R.string.adv_r_heat, n[0])
    }
}
