package xendroid.compose.ui.disc

import org.junit.Assert.assertEquals
import org.junit.Test
import xendroid.compose.Emulator

class RequestedDiscTest {
    private fun request(number: Int, vararg labels: String) = Emulator.DiscSwapRequest().apply {
        discNumber = number
        discLabels = arrayOf(*labels)
        discPaths = Array(labels.size) { "/storage/emulated/0/Games/d$it.iso" }
    }

    @Test fun theDiscTheGameAsksForIsChosenFirst() {
        assertEquals(1, requestedDiscIndex(request(2, "Disco 1 de 3", "Disco 2 de 3", "Disco 3 de 3")))
        // Disc 2 was not found: the third disc is the second option.
        assertEquals(1, requestedDiscIndex(request(3, "Disc 1", "Disc 3")))
    }

    @Test fun withoutAMatchTheFirstOne() {
        assertEquals(0, requestedDiscIndex(request(2, "Blue Dragon", "Blue Dragon")))
        assertEquals(0, requestedDiscIndex(request(0, "Disc 1", "Disc 2")))
        assertEquals(0, requestedDiscIndex(request(2)))
    }
}
