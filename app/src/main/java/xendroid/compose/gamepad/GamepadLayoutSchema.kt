package xendroid.compose.gamepad

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class ControlLayoutDto(
    val id: String,            // ControlId.name
    val x: Float, val y: Float,
    val scale: Float = 1f, val visible: Boolean = true,
    /** 15f: a d-pad's or stick's own dead zone; null = its default. */
    val deadZone: Float? = null,
    /** U06: fields of another build this one does not read, kept so saving never drops them. */
    val extra: JsonObject = JsonObject(emptyMap()),
)

/**
 * U06: JSON fields [base] does not declare go into the object's `extra` when read and back
 * to the top level when written, so a layout from a newer build survives this one.
 */
open class KeepUnknownFields<T : Any>(base: KSerializer<T>) : JsonTransformingSerializer<T>(base) {
    private val known = (0 until base.descriptor.elementsCount).map { base.descriptor.getElementName(it) }.toSet()

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val unknown = element.filterKeys { it !in known }
        if (unknown.isEmpty()) return element
        val kept = (element[EXTRA] as? JsonObject).orEmpty() + unknown
        return JsonObject(element.filterKeys { it in known } + (EXTRA to JsonObject(kept)))
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val kept = element[EXTRA] as? JsonObject ?: return element
        val own = element - EXTRA
        return JsonObject(own + kept.filterKeys { it !in own })
    }

    private companion object { const val EXTRA = "extra" }
}

object ControlLayoutKeepUnknown : KeepUnknownFields<ControlLayoutDto>(ControlLayoutDto.serializer())

@Serializable
data class OrientationLayoutDto(
    val controls: List<@Serializable(with = ControlLayoutKeepUnknown::class) ControlLayoutDto> = emptyList(),
)

/**
 * U06: the edited layout with what this build does not know of [previous] kept: controls of
 * another build (an id it has no control for) and the fields it does not read.
 */
fun OrientationLayoutDto.preserving(previous: OrientationLayoutDto): OrientationLayoutDto {
    val known = ControlId.entries.map { it.name }.toSet()
    val old = previous.controls.associateBy { it.id }
    val edited = controls.map { c -> old[c.id]?.extra?.takeIf { it.isNotEmpty() && c.extra.isEmpty() }?.let { c.copy(extra = it) } ?: c }
    val ids = edited.map { it.id }.toSet()
    return copy(controls = edited + previous.controls.filter { it.id !in known && it.id !in ids })
}

@Serializable
data class GamepadGlobalsDto(
    val enabled: Boolean = true,
    val opacity: Float = 0.65f,            // ~65% default (spec)
    val autoHideSeconds: Float = 8f,       // 0 disables auto-hide
    val hapticsEnabled: Boolean = false,   // mirrors legacy enable_vibrator default
    /** U07: touch camera speed, 0.5–2 (1 = full turn at 1.2 dp/ms of finger travel). */
    val cameraSensitivity: Float = 1f,
    /** U07: where the touch camera's area starts, as a fraction of the width (0.3–0.7). */
    val cameraAreaStart: Float = TouchCamera.DEFAULT_AREA_START,
    /** The controls step aside while a physical controller plays P1 ([TouchOverlayPresence]). */
    val hideWithController: Boolean = true,
    /** 15c: a [SplitScreenMode] key: the game in the upper part and the controls clear of it. */
    val splitScreen: String = SplitScreenMode.OFF.key,
    /** 15f: a finger slides from button to button (and onto or off the d-pad) without lifting. */
    val slideButtons: Boolean = false,
    /** 15f: a free finger sliding onto a stick takes it. */
    val slideSticks: Boolean = false,
    /** Round 2: a [ControlStyle] key: dark glass buttons (modern) or the coloured ones (classic). */
    val style: String = ControlStyle.MODERN.key,
)

/** Round 2: how the touch controls look. Modern: dark translucent glass with a thin rim and the A/B/X/Y
 *  letters in their colours; classic: the glossy coloured face buttons of before. */
enum class ControlStyle(val key: String) {
    MODERN("modern"), CLASSIC("classic");

    companion object {
        fun parse(key: String?): ControlStyle = entries.firstOrNull { it.key == key } ?: MODERN
    }
}

/** A game's own layout (U06); an orientation it has none for uses the shared one. */
@Serializable
data class TitleLayoutDto(
    val portrait: OrientationLayoutDto? = null,
    val landscape: OrientationLayoutDto? = null,
)

/** U06: a named layout the player saved or imported, for one or both orientations. */
@Serializable
data class LayoutPresetDto(
    val name: String,
    val portrait: OrientationLayoutDto? = null,
    val landscape: OrientationLayoutDto? = null,
    val savedAt: Long = 0,
    val extra: JsonObject = JsonObject(emptyMap()),
)

object LayoutPresetKeepUnknown : KeepUnknownFields<LayoutPresetDto>(LayoutPresetDto.serializer())

@Serializable
data class GamepadConfigDto(
    val version: Int = 1,
    val globals: GamepadGlobalsDto = GamepadGlobalsDto(),
    val portrait: OrientationLayoutDto = OrientationLayoutDto(),
    val landscape: OrientationLayoutDto = OrientationLayoutDto(),
    /** Per-game layouts by Title ID (upper-case hex). */
    val titles: Map<String, TitleLayoutDto> = emptyMap(),
    /** U06: named layouts, in the order saved. */
    val presets: List<@Serializable(with = LayoutPresetKeepUnknown::class) LayoutPresetDto> = emptyList(),
)

private fun titleKey(titleId: String?): String? =
    titleId?.uppercase()?.takeIf { it.matches(Regex("[0-9A-F]{8}")) && it != "00000000" }

/** The layout [titleId] plays with in one orientation: its own if it has one, else the shared one. */
fun GamepadConfigDto.layoutFor(titleId: String?, landscape: Boolean): OrientationLayoutDto {
    val own = titleKey(titleId)?.let { titles[it] }?.let { if (landscape) it.landscape else it.portrait }
    return own ?: if (landscape) this.landscape else portrait
}

fun GamepadConfigDto.hasOwnLayout(titleId: String?, landscape: Boolean): Boolean =
    titleKey(titleId)?.let { titles[it] }?.let { if (landscape) it.landscape else it.portrait } != null

/** Stores [layout] for one orientation: as [titleId]'s own when given, else as the shared layout. */
fun GamepadConfigDto.withLayout(titleId: String?, landscape: Boolean, layout: OrientationLayoutDto): GamepadConfigDto {
    val key = titleKey(titleId) ?: return if (landscape) copy(landscape = layout) else copy(portrait = layout)
    val current = titles[key] ?: TitleLayoutDto()
    val updated = if (landscape) current.copy(landscape = layout) else current.copy(portrait = layout)
    return copy(titles = titles + (key to updated))
}

/** [titleId] goes back to the shared layout in one orientation; an empty entry is dropped. */
fun GamepadConfigDto.withoutOwnLayout(titleId: String?, landscape: Boolean): GamepadConfigDto {
    val key = titleKey(titleId) ?: return this
    val current = titles[key] ?: return this
    val updated = if (landscape) current.copy(landscape = null) else current.copy(portrait = null)
    return copy(titles = if (updated.portrait == null && updated.landscape == null) titles - key else titles + (key to updated))
}

fun OrientationLayoutDto.applyTo(base: List<OnScreenControl>): List<OnScreenControl> {
    val byId = controls.associateBy { it.id }
    return base.map { c ->
        byId[c.id.name]?.let { c.withLayout(it.x, it.y, it.scale.coerceIn(0.5f, 3f), it.visible, it.deadZone?.takeIf(Float::isFinite)) } ?: c
    }
}
fun List<OnScreenControl>.toDto() =
    OrientationLayoutDto(map { ControlLayoutDto(it.id.name, it.xFraction, it.yFraction, it.scale, it.visible, it.ownDeadZone()) })

/** 15f: written only when it differs from the control's default, so layouts stay as they were. */
private fun OnScreenControl.ownDeadZone(): Float? = when (this) {
    is OnScreenControl.Dpad -> deadZone.takeIf { it != OnScreenControl.Dpad.DEFAULT_DEAD_ZONE }
    is OnScreenControl.AnalogStick -> deadZone.takeIf { it != 0f }
    is OnScreenControl.Button -> null
}
