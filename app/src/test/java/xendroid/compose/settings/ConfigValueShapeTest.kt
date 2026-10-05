package xendroid.compose.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.settings.ConfigValueShape

/** The native `save_config_entry` re-infers the TOML type from the string shape;
 *  these tests pin the canonical shapes (JNI-free) so the contract can't regress. */
class ConfigValueShapeTest {

    @Test fun bool_shapes() {
        assertEquals("true", ConfigValueShape.bool(true))
        assertEquals("false", ConfigValueShape.bool(false))
    }

    @Test fun int_shapes() {
        assertEquals("5", ConfigValueShape.int(5))
        assertEquals("-3", ConfigValueShape.int(-3))
    }

    @Test fun double_always_carries_exactly_one_dot() {
        assertEquals(1, ConfigValueShape.double(1.5).count { it == '.' })
        assertEquals("2.0", ConfigValueShape.double(2.0))   // never "2"
        assertTrue(ConfigValueShape.double(2.0).contains('.'))
    }

    /** Doubles come back from the native side as std::to_string ("0.100000"); a list
     *  must still show the option the value came from. */
    @Test fun listOption_maps_a_round_tripped_number_to_its_option() {
        val options = listOf("0.0", "0.05", "0.1", "0.15")
        assertEquals("0.1", ConfigValueShape.listOption(options, "0.100000"))
        assertEquals("0.0", ConfigValueShape.listOption(options, "0.000000"))
        assertEquals("0.05", ConfigValueShape.listOption(options, "0.05"))
        assertEquals("0.7", ConfigValueShape.listOption(options, "0.7"))   // unknown stays
        assertEquals("fsr", ConfigValueShape.listOption(listOf("bilinear", "fsr"), "fsr"))
        assertEquals("-1", ConfigValueShape.listOption(listOf("-1", "0", "5"), "-1"))
        assertEquals(null, ConfigValueShape.listOption(options, null))
    }

    @Test fun parseBool_round_trips_and_defaults() {
        assertTrue(ConfigValueShape.parseBool("true", false))
        assertFalse(ConfigValueShape.parseBool("false", true))
        assertTrue(ConfigValueShape.parseBool(null, true))       // null -> default
        assertFalse(ConfigValueShape.parseBool("garbage", false)) // garbage -> default
    }

    @Test fun parseInt_round_trips_with_double_tolerance() {
        assertEquals(5, ConfigValueShape.parseInt("5", -1))
        assertEquals(8, ConfigValueShape.parseInt("8.0", -1))   // double round-trip tolerance
        assertEquals(-1, ConfigValueShape.parseInt(null, -1))   // null -> default
        assertEquals(-1, ConfigValueShape.parseInt("xyz", -1))  // garbage -> default
    }
}
