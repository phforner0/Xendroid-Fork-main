package xendroid.compose.ui.profile

import org.junit.Assert.*
import org.junit.Test

class AvatarPolicyTest {
    @Test fun subsamplingKeepsTheShorterSideAboveTheTarget() {
        assertEquals(1, AvatarPolicy.sampleSize(300, 400))
        assertEquals(2, AvatarPolicy.sampleSize(512, 512))
        assertEquals(16, AvatarPolicy.sampleSize(4096, 6000))
        // 12000 / 32 = 375 >= 256, 12000 / 64 = 187 < 256.
        assertEquals(32, AvatarPolicy.sampleSize(12000, 16000))
    }

    @Test fun absurdOrMissingDimensionsAreRejectedBeforeDecoding() {
        assertThrows(IllegalArgumentException::class.java) { AvatarPolicy.sampleSize(-1, -1) }
        assertThrows(IllegalArgumentException::class.java) { AvatarPolicy.sampleSize(0, 100) }
        assertThrows(IllegalArgumentException::class.java) { AvatarPolicy.sampleSize(20000, 100) }
    }
}
