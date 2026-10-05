package xendroid.compose.gamepad

import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * U06: named touch layouts. Saved from the layout being edited (both orientations), applied
 * to it after a preview of what moves, and exported/imported as a versioned file that keeps
 * what this build does not know (controls of other builds, fields it does not read). Pure,
 * so every rule is tested on the JVM.
 */
object LayoutPresets {
    const val FORMAT = "xendroid-touch-layout"
    const val VERSION = 1
    const val MAX_PRESETS = 30
    const val MAX_NAME = 40
    const val MAX_FILE_BYTES = 256 * 1024
    const val MAX_CONTROLS = 64

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Spaces collapsed; 1–[MAX_NAME] characters without control characters, else null. */
    fun cleanName(raw: String): String? =
        raw.replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME && it.none(Char::isISOControl) }

    fun find(cfg: GamepadConfigDto, name: String): LayoutPresetDto? = cfg.presets.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /** [wanted], or "wanted (2)"… when a layout already has that name. */
    fun uniqueName(cfg: GamepadConfigDto, wanted: String): String {
        val base = cleanName(wanted) ?: "Layout"
        if (find(cfg, base) == null) return base
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = base.take(MAX_NAME - suffix.length) + suffix
            if (find(cfg, candidate) == null) return candidate
            n++
        }
    }

    /** Why a layout is refused (a file to import, or a save): [english] for logs and tests, the
     *  enum for the screen to say it in its language (U02); `%s` is the refusal's detail. */
    enum class Refusal(val english: String) {
        TOO_LARGE("The file is larger than 256 KB"),
        NOT_JSON("Not a layout file"),
        NOT_XENDROID("Not a XenDroid touch layout"),
        NO_VERSION("The file has no format version"),
        NEWER("Made by a newer XenDroid (layout version %s); update to import it"),
        UNKNOWN_VERSION("Unknown layout version %s"),
        DAMAGED("The layout is damaged"),
        BAD_NAME("A layout needs a name of 1-%s characters"),
        EMPTY("The file has no layout"),
        TOO_MANY_CONTROLS("More than %s controls"),
        DUPLICATE_CONTROL("A control appears twice"),
        UNNAMED_CONTROL("A control has no valid name"),
        OFF_SCREEN("%s is off the screen"),
        BAD_SIZE("%s has a size out of range"),
        NO_ORIENTATION("A layout needs at least one orientation"),
        FULL("At most %s layouts; delete one first"),
        CANNOT_OPEN("The file could not be opened"),
        CANNOT_READ("The file could not be read (%s)"),
    }

    class RefusedException(val why: Refusal, val detail: String = "") : IllegalArgumentException(why.english.format(detail))

    /** Saves [preset] under its name: the layout with that name (any case) is replaced. */
    fun save(cfg: GamepadConfigDto, preset: LayoutPresetDto): GamepadConfigDto {
        val name = cleanName(preset.name) ?: throw RefusedException(Refusal.BAD_NAME, "$MAX_NAME")
        if (preset.portrait == null && preset.landscape == null) throw RefusedException(Refusal.NO_ORIENTATION)
        val existing = find(cfg, name)
        if (existing == null && cfg.presets.size >= MAX_PRESETS) throw RefusedException(Refusal.FULL, "$MAX_PRESETS")
        val named = preset.copy(name = name)
        return cfg.copy(presets = if (existing == null) cfg.presets + named else cfg.presets.map { if (it === existing) named else it })
    }

    /** The layout [titleId] edits (the shared one when null), both orientations, as [name]. */
    fun capture(cfg: GamepadConfigDto, titleId: String?, name: String, now: Long): LayoutPresetDto =
        LayoutPresetDto(name, portrait = cfg.layoutFor(titleId, landscape = false), landscape = cfg.layoutFor(titleId, landscape = true), savedAt = now)

    fun delete(cfg: GamepadConfigDto, name: String): GamepadConfigDto =
        cfg.copy(presets = cfg.presets.filterNot { it.name.equals(name, ignoreCase = true) })

    /** [preset] becomes the layout [titleId] edits (shared when null) in the orientations it has. */
    fun apply(cfg: GamepadConfigDto, preset: LayoutPresetDto, titleId: String?): GamepadConfigDto {
        var next = cfg
        preset.portrait?.let { next = next.withLayout(titleId, landscape = false, it) }
        preset.landscape?.let { next = next.withLayout(titleId, landscape = true, it) }
        return next
    }

    /** What applying a layout changes on screen, control by control. */
    data class Diff(val moved: Int, val resized: Int, val shown: Int, val hidden: Int) {
        val isEmpty: Boolean get() = moved == 0 && resized == 0 && shown == 0 && hidden == 0
        /** "Controls: 3 moved, 1 resized, 1 hidden", or "Nothing changes". */
        val summary: String
            get() = listOfNotNull(
                moved.takeIf { it > 0 }?.let { "$it moved" },
                resized.takeIf { it > 0 }?.let { "$it resized" },
                shown.takeIf { it > 0 }?.let { "$it shown" },
                hidden.takeIf { it > 0 }?.let { "$it hidden" },
            ).joinToString(", ").let { if (it.isEmpty()) "Nothing changes" else "Controls: $it" }
    }

    /** [current] → [next] over the controls of this build ([base]: the defaults they apply to). */
    fun diff(current: OrientationLayoutDto, next: OrientationLayoutDto, base: List<OnScreenControl>): Diff {
        val before = current.applyTo(base).associateBy { it.id }
        var moved = 0; var resized = 0; var shown = 0; var hidden = 0
        for (after in next.applyTo(base)) {
            val was = before[after.id] ?: continue
            if (abs(was.xFraction - after.xFraction) > 0.005f || abs(was.yFraction - after.yFraction) > 0.005f) moved++
            if (abs(was.scale - after.scale) > 0.01f) resized++
            if (!was.visible && after.visible) shown++
            if (was.visible && !after.visible) hidden++
        }
        return Diff(moved, resized, shown, hidden)
    }

    /** The exported file: format and version first, then the layout as stored. */
    fun encodeFile(preset: LayoutPresetDto): String {
        val body = json.encodeToJsonElement(LayoutPresetKeepUnknown, preset).jsonObject
        return json.encodeToString(JsonObject.serializer(),
            JsonObject(mapOf("format" to JsonPrimitive(FORMAT), "version" to JsonPrimitive(VERSION)) + body))
    }

    sealed interface Decoded {
        data class Ok(val preset: LayoutPresetDto) : Decoded
        data class Refused(val why: Refusal, val detail: String = "") : Decoded {
            val reason: String get() = why.english.format(detail)
        }
    }

    /** Reads an exported layout; anything off the screen or out of range refuses the file. */
    fun decodeFile(text: String): Decoded {
        if (text.length > MAX_FILE_BYTES) return Decoded.Refused(Refusal.TOO_LARGE)
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { return Decoded.Refused(Refusal.NOT_JSON) }
        if ((root["format"] as? JsonPrimitive)?.content != FORMAT) return Decoded.Refused(Refusal.NOT_XENDROID)
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: return Decoded.Refused(Refusal.NO_VERSION)
        if (version > VERSION) return Decoded.Refused(Refusal.NEWER, "$version")
        if (version < 1) return Decoded.Refused(Refusal.UNKNOWN_VERSION, "$version")
        val preset = runCatching { json.decodeFromJsonElement(LayoutPresetKeepUnknown, JsonObject(root - "format" - "version")) }
            .getOrElse { return Decoded.Refused(Refusal.DAMAGED) }
        val name = cleanName(preset.name) ?: return Decoded.Refused(Refusal.BAD_NAME, "$MAX_NAME")
        if (preset.portrait == null && preset.landscape == null) return Decoded.Refused(Refusal.EMPTY)
        listOfNotNull(preset.portrait, preset.landscape).forEach { layout -> problem(layout)?.let { return it } }
        return Decoded.Ok(preset.copy(name = name))
    }

    private fun problem(layout: OrientationLayoutDto): Decoded.Refused? {
        if (layout.controls.size > MAX_CONTROLS) return Decoded.Refused(Refusal.TOO_MANY_CONTROLS, "$MAX_CONTROLS")
        if (layout.controls.map { it.id }.toSet().size != layout.controls.size) return Decoded.Refused(Refusal.DUPLICATE_CONTROL)
        layout.controls.forEach { c ->
            if (c.id.isBlank() || c.id.length > 32) return Decoded.Refused(Refusal.UNNAMED_CONTROL)
            if (!c.x.isFinite() || !c.y.isFinite() || c.x !in 0f..1f || c.y !in 0f..1f) return Decoded.Refused(Refusal.OFF_SCREEN, c.id)
            if (!c.scale.isFinite() || c.scale !in 0.5f..3f) return Decoded.Refused(Refusal.BAD_SIZE, c.id)
        }
        return null
    }
}
