package xendroid.compose.gamepad

import kotlinx.serialization.Serializable

@Serializable
data class ControlLayoutDto(
    val id: String,            // ControlId.name
    val x: Float, val y: Float,
    val scale: Float = 1f, val visible: Boolean = true,
)

@Serializable
data class OrientationLayoutDto(val controls: List<ControlLayoutDto> = emptyList())

@Serializable
data class GamepadGlobalsDto(
    val enabled: Boolean = true,
    val opacity: Float = 0.65f,            // ~65% default (spec)
    val autoHideSeconds: Float = 8f,       // 0 disables auto-hide
    val hapticsEnabled: Boolean = false,   // mirrors legacy enable_vibrator default
)

/** A game's own layout (U06); an orientation it has none for uses the shared one. */
@Serializable
data class TitleLayoutDto(
    val portrait: OrientationLayoutDto? = null,
    val landscape: OrientationLayoutDto? = null,
)

@Serializable
data class GamepadConfigDto(
    val version: Int = 1,
    val globals: GamepadGlobalsDto = GamepadGlobalsDto(),
    val portrait: OrientationLayoutDto = OrientationLayoutDto(),
    val landscape: OrientationLayoutDto = OrientationLayoutDto(),
    /** Per-game layouts by Title ID (upper-case hex). */
    val titles: Map<String, TitleLayoutDto> = emptyMap(),
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
        byId[c.id.name]?.let { c.withLayout(it.x, it.y, it.scale.coerceIn(0.5f, 3f), it.visible) } ?: c
    }
}
fun List<OnScreenControl>.toDto() =
    OrientationLayoutDto(map { ControlLayoutDto(it.id.name, it.xFraction, it.yFraction, it.scale, it.visible) })
