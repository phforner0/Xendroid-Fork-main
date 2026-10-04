package xendroid.compose.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The launch token: made once, kept, and the only thing that lets launch options through. */
class LaunchTokenTest {
    @Test fun made_once_and_kept() {
        val dir = Files.createTempDirectory("files").toFile()
        val token = LaunchToken.get(dir)
        assertTrue(token.matches(Regex("[0-9a-f]{64}")))
        assertEquals(token, LaunchToken.get(dir))
        assertTrue(LaunchToken.matches(dir, token))
    }

    @Test fun anything_else_does_not_match() {
        val dir = Files.createTempDirectory("files").toFile()
        assertFalse(LaunchToken.matches(dir, "a".repeat(64)))   // no token yet
        val token = LaunchToken.get(dir)
        assertFalse(LaunchToken.matches(dir, null))
        assertFalse(LaunchToken.matches(dir, ""))
        assertFalse(LaunchToken.matches(dir, token.reversed()))
        assertFalse(LaunchToken.matches(dir, token + "0"))
    }

    @Test fun a_damaged_token_file_is_made_again() {
        val dir = Files.createTempDirectory("files").toFile()
        File(dir, "launch.token").writeText("not a token")
        val token = LaunchToken.get(dir)
        assertTrue(token.matches(Regex("[0-9a-f]{64}")))
        assertTrue(LaunchToken.matches(dir, token))
    }
}
