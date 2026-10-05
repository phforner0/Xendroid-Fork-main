package xendroid.compose.gamepad

/** When the game takes one half of the screen and the touch controls the other (15c). */
enum class SplitScreenMode(val key: String) {
    OFF("off"),
    /** A foldable half open with a horizontal fold (tabletop / flex posture): the game above it. */
    TABLETOP("tabletop"),
    /** Also without a fold, on a screen in portrait or close to square (1.6:1 or narrower). */
    ALWAYS("always");

    fun next(): SplitScreenMode = entries[(ordinal + 1) % entries.size]

    companion object {
        /** A value this build does not know (a newer one's) reads as off. */
        fun parse(key: String?): SplitScreenMode = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** A rectangle in the layout's pixels: [left, right) x [top, bottom). */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** A horizontal fold, half open, in the layout's pixels: the band [top, bottom) it covers. */
data class Hinge(val top: Int, val bottom: Int)

/** Where the game's picture and the touch controls go when the screen is split. */
data class SplitLayout(val game: PxRect, val controls: PxRect)

/**
 * 15c (Bannerlator `440d3dc4`): the game's picture in the upper part of the screen and the touch
 * controls clear of it. The surface the game draws on is resized to [SplitLayout.game], so the
 * presenter fits the picture there as it does after a rotation. On a landscape-shaped screen
 * (an unfolded foldable, a tablet) the controls take the part below the fold or the middle, laid
 * out there as on a phone held sideways; on a portrait one they keep the whole screen, since the
 * portrait layout already keeps them in its lower half. The rules are pure, so they are tested
 * on the JVM.
 */
object SplitScreen {
    /** [SplitScreenMode.ALWAYS] without a fold: only on screens up to this wide for their height. */
    const val MAX_ASPECT_WITHOUT_FOLD = 1.6f
    /** A fold closer than this to an edge (a fraction of the height) leaves too little on one side. */
    const val MIN_PART = 0.25f

    /** Converts a fold's band from window coordinates to the layout's, given where the layout's
     *  top is in the window; null when it does not cross the layout. */
    fun hingeInLayout(windowTop: Int, windowBottom: Int, layoutTop: Int, layoutHeight: Int): Hinge? {
        val top = windowTop - layoutTop
        val bottom = windowBottom - layoutTop
        return if (bottom < top || top <= 0 || bottom >= layoutHeight) null else Hinge(top, bottom)
    }

    /** The split for a [width] x [height] layout; null = no split (the whole screen for both). */
    fun layout(width: Int, height: Int, mode: SplitScreenMode, hinge: Hinge?): SplitLayout? {
        if (width <= 0 || height <= 0 || mode == SplitScreenMode.OFF) return null
        val fold = hinge?.takeIf {
            it.top <= it.bottom && it.top >= height * MIN_PART && it.bottom <= height * (1f - MIN_PART)
        }
        val (splitTop, splitBottom) = when {
            fold != null -> fold.top to fold.bottom
            mode == SplitScreenMode.ALWAYS && width <= height * MAX_ASPECT_WITHOUT_FOLD -> (height / 2) to (height / 2)
            else -> return null
        }
        val game = PxRect(0, 0, width, splitTop)
        val controls = if (width > height) PxRect(0, splitBottom, width, height) else PxRect(0, 0, width, height)
        return SplitLayout(game, controls)
    }
}
