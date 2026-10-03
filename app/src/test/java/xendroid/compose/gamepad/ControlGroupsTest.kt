package xendroid.compose.gamepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlGroupsTest {
    private val layout = defaultLayout(true)
    private fun List<OnScreenControl>.at(id: ControlId) = single { it.id == id }

    @Test fun everyControlIsInOneGroupAtMost() {
        val grouped = ControlGroups.GROUPS.flatten()
        assertEquals(grouped.size, grouped.toSet().size)
        assertEquals(setOf(ControlId.A, ControlId.B, ControlId.X, ControlId.Y), ControlGroups.of(ControlId.B))
        assertEquals(setOf(ControlId.DPAD), ControlGroups.of(ControlId.DPAD))
        assertEquals(setOf(ControlId.LEFT_STICK, ControlId.LS_CLICK), ControlGroups.of(ControlId.LS_CLICK))
    }

    @Test fun movingOneMovesItsGroupAndNothingElse() {
        val a = layout.at(ControlId.A)
        val moved = ControlGroups.move(layout, ControlId.A, a.xFraction - 0.2f, a.yFraction + 0.1f)
        for (id in ControlGroups.of(ControlId.A)) {
            assertEquals(layout.at(id).xFraction - 0.2f, moved.at(id).xFraction, 1e-5f)
            assertEquals(layout.at(id).yFraction + 0.1f, moved.at(id).yFraction, 1e-5f)
        }
        assertEquals(layout.at(ControlId.DPAD), moved.at(ControlId.DPAD))
        assertEquals(layout.at(ControlId.RB), moved.at(ControlId.RB))
    }

    @Test fun theGroupStopsAtTheEdgeWithItsShape() {
        // B is the rightmost face button: pushing A far right stops when B reaches the edge.
        val moved = ControlGroups.move(layout, ControlId.A, 2f, layout.at(ControlId.A).yFraction)
        assertEquals(ControlGroups.MAX, moved.at(ControlId.B).xFraction, 1e-5f)
        val gap = layout.at(ControlId.B).xFraction - layout.at(ControlId.X).xFraction
        assertEquals(gap, moved.at(ControlId.B).xFraction - moved.at(ControlId.X).xFraction, 1e-5f)
        assertTrue(moved.all { it.xFraction in ControlGroups.MIN..ControlGroups.MAX && it.yFraction in ControlGroups.MIN..ControlGroups.MAX })
    }

    @Test fun resizingKeepsTheClusterShape() {
        val bigger = ControlGroups.resize(layout, ControlId.A, layout.at(ControlId.A).scale * 1.5f)
        val face = ControlGroups.of(ControlId.A)
        for (id in face) assertEquals(layout.at(id).scale * 1.5f, bigger.at(id).scale, 1e-5f)
        // Spacing grows by the same factor about the center.
        val before = layout.at(ControlId.B).xFraction - layout.at(ControlId.X).xFraction
        val after = bigger.at(ControlId.B).xFraction - bigger.at(ControlId.X).xFraction
        assertEquals(before * 1.5f, after, 1e-4f)
        val cx = face.map { layout.at(it).xFraction }.average()
        assertEquals(cx, face.map { bigger.at(it).xFraction }.average(), 1e-4)
        assertEquals(layout.at(ControlId.DPAD), bigger.at(ControlId.DPAD))
    }

    @Test fun resizingStopsWhereAMemberWouldLeaveTheRange() {
        // The stick is 0.8, its click 1.0: the click reaches 3 first.
        val huge = ControlGroups.resize(layout, ControlId.LEFT_STICK, 9f)
        assertEquals(3f, huge.at(ControlId.LS_CLICK).scale, 1e-5f)
        assertEquals(0.8f * 3f, huge.at(ControlId.LEFT_STICK).scale, 1e-4f)
        val tiny = ControlGroups.resize(layout, ControlId.LS_CLICK, 0.1f)
        assertEquals(0.5f, tiny.at(ControlId.LEFT_STICK).scale, 1e-5f)
    }
}
