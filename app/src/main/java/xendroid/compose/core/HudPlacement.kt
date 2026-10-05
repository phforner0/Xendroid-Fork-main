package xendroid.compose.core

import java.util.Locale

/** 15g: how the HUD's text stands out: a translucent box, an outline around the letters, or plain. */
enum class HudLook(val key: String) {
    BOX("box"), OUTLINE("outline"), PLAIN("plain");

    fun next(): HudLook = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(key: String?): HudLook = entries.firstOrNull { it.key == key } ?: BOX
    }
}

/** Where the HUD sits, how big it is and how it looks. */
data class HudPlacement(val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f, val look: HudLook = HudLook.BOX)

/**
 * 15g (Bannerlator's per-game HUD): each game remembers where the player put the HUD, its size
 * and its look; a game without its own starts from the last one set anywhere. The keys live in
 * the HUD's preferences next to the old global ones ("x", "y", "scale"), which stay the
 * "last set anywhere" value, so nothing saved before moves. Reading and writing go through
 * [Store] so the rules are tested on the JVM.
 */
object HudPlacements {
    interface Store {
        fun float(key: String): Float?
        fun string(key: String): String?
        fun put(values: Map<String, Any>)
    }

    private val TITLE = Regex("[0-9A-F]{8}")

    private fun suffix(titleId: String?): String? =
        titleId?.uppercase(Locale.ROOT)?.takeIf { TITLE.matches(it) && it != "00000000" }?.let { "@$it" }

    fun read(store: Store, titleId: String?): HudPlacement {
        val own = suffix(titleId)
        fun float(name: String) = own?.let { store.float(name + it) } ?: store.float(name)
        fun string(name: String) = own?.let { store.string(name + it) } ?: store.string(name)
        return HudPlacement(
            x = float("x")?.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f,
            y = float("y")?.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f,
            scale = float("scale")?.takeIf { it.isFinite() }?.coerceIn(MIN_SCALE, MAX_SCALE) ?: 1f,
            look = HudLook.parse(string("look")),
        )
    }

    /** Saves for [titleId] (when there is one) and as the last set anywhere. */
    fun write(store: Store, titleId: String?, placement: HudPlacement) {
        val values = mapOf<String, Any>("x" to placement.x, "y" to placement.y, "scale" to placement.scale, "look" to placement.look.key)
        val own = suffix(titleId)
        store.put(values + (own?.let { s -> values.mapKeys { it.key + s } } ?: emptyMap()))
    }

    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.5f
}

/** 15g (DroidDeck `0cc3fe6`): GPU memory as KGSL counts it, for every app (the driver's total). */
object KgslMemory {
    const val PATH = "/sys/class/kgsl/kgsl/page_alloc"

    /** Bytes from the file's text ("123456789"); null when it is not a count. */
    fun parse(text: String?): Long? = text?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull()?.takeIf { it >= 0 }

    /** "GPU mem 1.25 GB (all apps)". */
    fun line(bytes: Long?): String = bytes?.let {
        if (it >= 1L shl 30) String.format(Locale.US, "GPU mem %.2f GB (all apps)", it / 1_073_741_824.0)
        else String.format(Locale.US, "GPU mem %.0f MB (all apps)", it / 1_048_576.0)
    } ?: "GPU mem N/A"
}

/** Round 2: the HUD as a box beside the game that the fingers move and size (vertical), or as a bar
 *  along the top or the bottom edge, every metric in a line (horizontal). */
enum class HudLayout(val key: String) {
    VERTICAL("vertical"), HORIZONTAL("horizontal");

    companion object {
        fun parse(key: String?): HudLayout = entries.firstOrNull { it.key == key } ?: VERTICAL
    }
}

/** Round 2: the edge the horizontal HUD sits on. */
enum class HudEdge(val key: String) {
    TOP("top"), BOTTOM("bottom");

    companion object {
        fun parse(key: String?): HudEdge = entries.firstOrNull { it.key == key } ?: TOP
    }
}

/**
 * Round 2: how the HUD is drawn, every game's: its layout and edge, the background's opacity, how
 * strongly labels and the graph are coloured (0 = white), and whether the FPS graph shows.
 */
data class HudStyle(
    val layout: HudLayout = HudLayout.VERTICAL,
    val edge: HudEdge = HudEdge.TOP,
    val opacity: Float = DEFAULT_OPACITY,
    val colors: Float = 1f,
    val graph: Boolean = true,
) {
    companion object {
        const val DEFAULT_OPACITY = 0.58f

        fun read(store: HudPlacements.Store): HudStyle = HudStyle(
            layout = HudLayout.parse(store.string("layout")),
            edge = HudEdge.parse(store.string("edge")),
            opacity = store.float("opacity")?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: DEFAULT_OPACITY,
            colors = store.float("colors")?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f,
            graph = store.string("graph") != "off",
        )

        fun write(store: HudPlacements.Store, style: HudStyle) = store.put(mapOf(
            "layout" to style.layout.key, "edge" to style.edge.key, "opacity" to style.opacity, "colors" to style.colors,
            "graph" to if (style.graph) "on" else "off",
        ))
    }
}
