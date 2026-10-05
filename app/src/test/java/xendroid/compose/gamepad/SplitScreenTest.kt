package xendroid.compose.gamepad

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitScreenTest {
    @Test fun offNeverSplits() {
        assertNull(SplitScreen.layout(2176, 1812, SplitScreenMode.OFF, Hinge(896, 916)))
        assertNull(SplitScreen.layout(0, 1812, SplitScreenMode.ALWAYS, null))
    }

    @Test fun anUnfoldedFoldableOnTheTableSplitsAtTheFold() {
        val split = SplitScreen.layout(2176, 1812, SplitScreenMode.TABLETOP, Hinge(896, 916))!!
        assertEquals(PxRect(0, 0, 2176, 896), split.game)
        // Below the fold, laid out as a phone held sideways (wider than tall).
        assertEquals(PxRect(0, 916, 2176, 1812), split.controls)
    }

    @Test fun aFlipPhoneInFlexModeKeepsTheControlsOnTheWholeScreen() {
        // Portrait: the portrait layout keeps its controls in its lower half already.
        val split = SplitScreen.layout(1080, 2640, SplitScreenMode.TABLETOP, Hinge(1320, 1320))!!
        assertEquals(PxRect(0, 0, 1080, 1320), split.game)
        assertEquals(PxRect(0, 0, 1080, 2640), split.controls)
    }

    @Test fun tabletopNeedsAFold() {
        assertNull(SplitScreen.layout(1080, 2400, SplitScreenMode.TABLETOP, null))
        assertNull(SplitScreen.layout(2176, 1812, SplitScreenMode.TABLETOP, null))
    }

    @Test fun alwaysSplitsTallAndSquareScreensInTheMiddle() {
        assertEquals(SplitLayout(PxRect(0, 0, 1080, 1200), PxRect(0, 0, 1080, 2400)),
            SplitScreen.layout(1080, 2400, SplitScreenMode.ALWAYS, null))
        assertEquals(SplitLayout(PxRect(0, 0, 2176, 906), PxRect(0, 906, 2176, 1812)),
            SplitScreen.layout(2176, 1812, SplitScreenMode.ALWAYS, null))
        // A tablet at 16:10 still qualifies; a phone held sideways does not.
        assertEquals(PxRect(0, 0, 2560, 800), SplitScreen.layout(2560, 1600, SplitScreenMode.ALWAYS, null)!!.game)
        assertNull(SplitScreen.layout(2400, 1080, SplitScreenMode.ALWAYS, null))
        // A fold wins over the middle.
        assertEquals(PxRect(0, 0, 2176, 880), SplitScreen.layout(2176, 1812, SplitScreenMode.ALWAYS, Hinge(880, 930))!!.game)
    }

    @Test fun aFoldNearAnEdgeIsIgnored() {
        assertNull(SplitScreen.layout(2176, 1812, SplitScreenMode.TABLETOP, Hinge(200, 220)))
        assertNull(SplitScreen.layout(2176, 1812, SplitScreenMode.TABLETOP, Hinge(1700, 1720)))
        assertNull(SplitScreen.layout(2176, 1812, SplitScreenMode.TABLETOP, Hinge(930, 900)))
        // ...and "always" falls back to the middle.
        assertEquals(PxRect(0, 0, 2176, 906), SplitScreen.layout(2176, 1812, SplitScreenMode.ALWAYS, Hinge(200, 220))!!.game)
    }

    @Test fun theFoldInTheLayoutsCoordinates() {
        assertEquals(Hinge(860, 880), SplitScreen.hingeInLayout(896, 916, layoutTop = 36, layoutHeight = 1776))
        assertNull(SplitScreen.hingeInLayout(0, 10, layoutTop = 36, layoutHeight = 1776))
        assertNull(SplitScreen.hingeInLayout(1900, 1910, layoutTop = 0, layoutHeight = 1812))
    }

    @Test fun theModeIsSavedWithTheTouchControls() {
        assertEquals(SplitScreenMode.OFF, SplitScreenMode.parse(GamepadGlobalsDto().splitScreen))
        assertEquals(SplitScreenMode.OFF, SplitScreenMode.parse("hinge-v2"))
        assertEquals(listOf(SplitScreenMode.TABLETOP, SplitScreenMode.ALWAYS, SplitScreenMode.OFF),
            SplitScreenMode.entries.map { it.next() })
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val saved = json.encodeToString(GamepadGlobalsDto.serializer(), GamepadGlobalsDto(splitScreen = SplitScreenMode.TABLETOP.key))
        assertEquals(SplitScreenMode.TABLETOP, SplitScreenMode.parse(json.decodeFromString(GamepadGlobalsDto.serializer(), saved).splitScreen))
        // A file from before 15c has no such field.
        assertEquals(SplitScreenMode.OFF, SplitScreenMode.parse(json.decodeFromString(GamepadGlobalsDto.serializer(), "{}").splitScreen))
    }
}
