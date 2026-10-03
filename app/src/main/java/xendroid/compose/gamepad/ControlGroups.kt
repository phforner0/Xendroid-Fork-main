package xendroid.compose.gamepad

/**
 * 15r (Bannerlator `9dd238bb`, `a20fca5a`; Eden `76be55bc`): controls that belong together on a
 * controller move and resize together in the touch editor while "Group" is on: the face buttons,
 * each side's trigger and bumper, Back and Start, and each stick with its click. Moving keeps the
 * members' offsets and stops where one would leave the screen; resizing scales the members and
 * their spacing about the group's center, so the cluster keeps its shape. Pure, tested on the JVM.
 */
object ControlGroups {
    val GROUPS: List<Set<ControlId>> = listOf(
        setOf(ControlId.A, ControlId.B, ControlId.X, ControlId.Y),
        setOf(ControlId.LT, ControlId.LB),
        setOf(ControlId.RT, ControlId.RB),
        setOf(ControlId.BACK, ControlId.START),
        setOf(ControlId.LEFT_STICK, ControlId.LS_CLICK),
        setOf(ControlId.RIGHT_STICK, ControlId.RS_CLICK),
    )

    /** [id]'s group (just [id] for the d-pad). */
    fun of(id: ControlId): Set<ControlId> = GROUPS.firstOrNull { id in it } ?: setOf(id)

    const val MIN = 0.02f
    const val MAX = 0.98f

    /**
     * [controls] with [id]'s group moved so that [id] goes to ([x], [y]) (fractions), the others
     * by the same amount; the move stops where a member would leave [MIN]..[MAX].
     */
    fun move(controls: List<OnScreenControl>, id: ControlId, x: Float, y: Float): List<OnScreenControl> {
        val group = of(id)
        val members = controls.filter { it.id in group }
        val anchor = members.firstOrNull { it.id == id } ?: return controls
        val dx = (x - anchor.xFraction).coerceIn(MIN - members.minOf { it.xFraction }, MAX - members.maxOf { it.xFraction })
        val dy = (y - anchor.yFraction).coerceIn(MIN - members.minOf { it.yFraction }, MAX - members.maxOf { it.yFraction })
        return controls.map { if (it.id in group) it.withLayout(x = it.xFraction + dx, y = it.yFraction + dy) else it }
    }

    /**
     * [controls] with [id]'s group resized so that [id] gets [scale]: every member's size and its
     * distance from the group's center change by the same factor, held so no member leaves the
     * 0.5-3 size range.
     */
    fun resize(controls: List<OnScreenControl>, id: ControlId, scale: Float): List<OnScreenControl> {
        val group = of(id)
        val members = controls.filter { it.id in group }
        val anchor = members.firstOrNull { it.id == id } ?: return controls
        if (anchor.scale <= 0f) return controls
        val lowest = members.minOf { it.scale }
        val highest = members.maxOf { it.scale }
        val factor = (scale.coerceIn(SCALES.start, SCALES.endInclusive) / anchor.scale)
            .coerceIn(SCALES.start / lowest, SCALES.endInclusive / highest)
        val cx = members.map { it.xFraction }.average().toFloat()
        val cy = members.map { it.yFraction }.average().toFloat()
        return controls.map {
            if (it.id !in group) it
            else it.withLayout(
                x = (cx + (it.xFraction - cx) * factor).coerceIn(MIN, MAX),
                y = (cy + (it.yFraction - cy) * factor).coerceIn(MIN, MAX),
                s = (it.scale * factor).coerceIn(SCALES.start, SCALES.endInclusive),
            )
        }
    }

    /** The editor's size range for a control. */
    val SCALES = 0.5f..3f
}
