package xendroid.compose.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4 "Start with…": what a launch's options become, and what the host lets through. */
class LaunchOptionsTest {
    private val slots = listOf("AAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBBBB", null, null)

    @Test fun nothing_chosen_is_no_argument() {
        assertTrue(LaunchOptions().isEmpty)
        assertEquals(emptyList<String>(), LaunchOptions().toArgs(slots))
        assertTrue(LaunchOptions(launchModule = "  ", extraCommandLine = "").isEmpty)
    }

    @Test fun options_become_command_line_cvars() {
        val args = LaunchOptions(driverPath = "", launchModule = "menu.xex", noPatches = true, extraCommandLine = " -lang 2 ").toArgs(slots)
        assertEquals(listOf("--vulkan_lib_path=", "--launch_module=menu.xex", "--apply_patches=false", "--cl=-lang 2"), args)
    }

    @Test fun another_profile_plays_as_p1_and_leaves_its_old_slot() {
        // B plays as P2 now: as P1 it leaves P2 (one profile, one slot), A stays where it was not.
        assertEquals(listOf("--logged_profile_slot_0_xuid=BBBBBBBBBBBBBBBB", "--logged_profile_slot_1_xuid="),
            LaunchOptions(profileXuid = "bbbbbbbbbbbbbbbb").toArgs(slots))
        // Already P1: nothing to change.
        assertEquals(emptyList<String>(), LaunchOptions(profileXuid = "AAAAAAAAAAAAAAAA").toArgs(slots))
        // Not an XUID: ignored rather than handed to the core.
        assertEquals(emptyList<String>(), LaunchOptions(profileXuid = "nobody").toArgs(slots))
    }

    @Test fun ignoring_the_game_settings_hands_the_global_values_in_the_core_shapes() {
        val args = LaunchOptions(ignoreGameSettings = mapOf(
            "GPU|framerate_limit" to "60",
            "Console|widescreen" to "true",
            "GPU|draw_resolution_scale_x" to "1.000000",
            "Display|postprocess_scaling_and_sharpening" to "fsr",
            "Nope|unknown" to "1",
            "Logging|dump_session_logs" to "x",
        )).toArgs(slots)
        assertEquals(setOf("--framerate_limit=60", "--widescreen=true", "--draw_resolution_scale_x=1", "--postprocess_scaling_and_sharpening=fsr"),
            args.toSet())
    }

    @Test fun the_chosen_driver_wins_over_the_global_one() {
        val args = LaunchOptions(driverPath = "", ignoreGameSettings = mapOf("Vulkan|vulkan_lib_path" to "/x/turnip.so")).toArgs(slots)
        assertEquals(listOf("--vulkan_lib_path="), args)
    }

    @Test fun sanitize_keeps_what_toArgs_makes() {
        val root = Files.createTempDirectory("drivers").toFile()
        val lib = File(root, "abc/libvulkan_freedreno.so").apply { parentFile.mkdirs(); writeText("elf") }
        val made = LaunchOptions(profileXuid = "BBBBBBBBBBBBBBBB", driverPath = lib.absolutePath, launchModule = "a.xex", noPatches = true,
            ignoreGameSettings = mapOf("GPU|framerate_limit" to "30", "Console|widescreen" to "false"), extraCommandLine = "x").toArgs(slots)
        assertEquals(made, LaunchOptions.sanitize(made.toTypedArray(), root))
    }

    @Test fun sanitize_drops_what_could_hurt_or_crash_the_core() {
        val root = Files.createTempDirectory("drivers").toFile()
        val outside = Files.createTempFile("evil", ".so").toFile()
        val dropped = arrayOf(
            "--storage_root=/sdcard", "--config=/sdcard/x.toml", "--log_file=/sdcard/l", "--target=/x.iso",
            "--vulkan_lib_path=${outside.absolutePath}", "--vulkan_lib_path=${root.absolutePath}/../${outside.name}",
            "--framerate_limit=abc", "--framerate_limit=7", "--mute=yes", "--draw_resolution_scale_x=9",
            "--logged_profile_slot_0_xuid=nobody", "--unknown_cvar=1", "framerate_limit=30", "--cl=a\nb",
            "--dump_session_logs=1", "--launch_module=" + "x".repeat(2000),
        )
        assertEquals(emptyList<String>(), LaunchOptions.sanitize(dropped, root))
        assertEquals(emptyList<String>(), LaunchOptions.sanitize(arrayOf("--vulkan_lib_path=/x/a.so"), null))
        assertEquals(emptyList<String>(), LaunchOptions.sanitize(null, root))
    }

    @Test fun turnip_flags_pass_as_any_list_of_flags() {
        assertEquals(listOf("--turnip_debug=sysmem,nolrzfc"),
            LaunchOptions(ignoreGameSettings = mapOf("Vulkan|turnip_debug" to " sysmem, NOLRZFC")).toArgs(slots))
        assertEquals(listOf("--turnip_debug=sysmem,nolrzfc"), LaunchOptions.sanitize(arrayOf("--turnip_debug=sysmem,nolrzfc"), null))
        assertEquals(emptyList<String>(), LaunchOptions.sanitize(arrayOf("--turnip_debug=sysmem;rm -rf"), null))
    }

    @Test fun sanitize_takes_each_name_once() {
        assertEquals(listOf("--widescreen=true"), LaunchOptions.sanitize(arrayOf("--widescreen=true", "--mute=false"), null))
    }

    @Test fun installed_driver_must_be_a_library_inside_the_folder() {
        val root = Files.createTempDirectory("drivers").toFile()
        val lib = File(root, "p/lib.so").apply { parentFile.mkdirs(); writeText("elf") }
        assertTrue(LaunchOptions.isInstalledDriver(lib.absolutePath, root))
        assertFalse(LaunchOptions.isInstalledDriver(File(root, "p/missing.so").absolutePath, root))
        assertFalse(LaunchOptions.isInstalledDriver(File(root, "p").absolutePath, root))
        assertFalse(LaunchOptions.isInstalledDriver(lib.absolutePath, null))
    }
}
