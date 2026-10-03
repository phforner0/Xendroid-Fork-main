package xendroid.compose.roadmap

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.core.ContentPaths
import xendroid.compose.core.ProfilePaths
import xendroid.compose.saves.ProfileTrash
import xendroid.compose.settings.ConfigStore
import xendroid.compose.ui.profile.AvatarPolicy
import xendroid.compose.ui.profile.ProfileManagerViewModel
import xendroid.compose.ui.profile.ProfileManagerViewModel.ListState
import xendroid.compose.ui.profile.ProfileManagerViewModel.OpState
import xendroid.compose.ui.profile.ProfilesScreen
import xendroid.compose.ui.theme.xendroidTheme

/**
 * Roadmap item 4: profiles through the real profile code (native profile files under the test
 * package's content folder). Deleting a profile first shows what goes with it (its games'
 * saves) on the Profiles screen, then moves it to the trash; restoring brings it back with the
 * saves; removing it permanently deletes it. A huge image or a file that is not an image as
 * the avatar gives a message and creates nothing; a big camera-sized photo works.
 */
@RunWith(AndroidJUnit4::class)
class Item04ProfilesTest {
    @get:Rule val compose = createComposeRule()

    private val context = Device.context
    private val content get() = ContentPaths.contentRoot()
    private val title = "4D5309C9"
    private lateinit var vm: ProfileManagerViewModel
    private val gamertags = listOf("RoadmapA", "RoadmapBig", "RoadmapBad", "RoadmapPic")

    @Before fun setUp() {
        Device.requireTestPackage()
        vm = ProfileManagerViewModel(context, ConfigStore(context))
        loaded()
        removeTestProfiles()
    }

    @After fun tearDown() = removeTestProfiles()

    /** Test profiles (by gamertag) out of the list and the trash, files and all. */
    private fun removeTestProfiles() {
        val listed = (vm.listState.value as? ListState.Loaded)?.profiles.orEmpty().filter { it.gamertag in gamertags }
        listed.forEach { File(content, it.xuid).deleteRecursively() }
        val xuids = listed.map { it.xuid }.toSet()
        ProfileTrash(content).list().filter { it.xuid in xuids }
            .forEach { File(content, ".xendroid-trash/profiles/${it.id}").deleteRecursively() }
        if (listed.isNotEmpty()) { vm.refresh(); loaded() }
    }

    private fun loaded(what: String = "profiles listed", matches: (ListState.Loaded) -> Boolean = { true }): ListState.Loaded =
        Device.await(vm.listState, what) { it is ListState.Loaded && matches(it) } as ListState.Loaded

    /** Runs [action] from Idle and returns where it ends (done, failed or a confirmation). */
    private fun op(what: String, action: () -> Unit): OpState {
        vm.dismiss()
        action()
        return Device.await(vm.opState, what) { it is OpState.Done || it is OpState.Failed || it is OpState.ConfirmDelete }
    }

    private fun create(gamertag: String, avatar: Uri? = null): OpState = op("creating $gamertag") { vm.create(gamertag, 1, 103, avatar) }   // English, United States

    @Test fun deletePreviewTrashRestoreAndRemove() {
        assertEquals(OpState.Done(Device.string(R.string.pf_created, "RoadmapA")), create("RoadmapA"))
        val entry = loaded("RoadmapA listed") { s -> s.profiles.any { it.gamertag == "RoadmapA" } }.profiles.single { it.gamertag == "RoadmapA" }
        val save = File(content, "${entry.xuid}/$title/00000001/save.dat").apply { parentFile!!.mkdirs(); writeText("a save") }

        // The preview on the Profiles screen names the game whose saves go with the profile.
        compose.setContent { xendroidTheme { ProfilesScreen(vm, onBack = {}) } }
        val confirm = op("delete preview") { vm.requestDelete(entry) } as OpState.ConfirmDelete
        assertEquals(listOf(title), confirm.summary.gameTitles.map { it.titleId })
        assertEquals(1, confirm.summary.gameTitles.single().files)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title, substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(Device.string(R.string.pf_del_name, "RoadmapA"), substring = true).assertExists()
        compose.onNodeWithText(Device.string(R.string.pf_trash)).performClick()
        assertEquals(OpState.Done(Device.string(R.string.pf_trashed)),
            Device.await(vm.opState, "moved to the trash") { it is OpState.Done || it is OpState.Failed })
        loaded("RoadmapA gone from the list") { s -> s.profiles.none { it.gamertag == "RoadmapA" } }
        val trashed = Device.await(vm.trash, "RoadmapA in the trash") { t -> t.any { it.xuid == entry.xuid } }.single { it.xuid == entry.xuid }
        assertFalse(save.exists())

        assertEquals(OpState.Done(Device.string(R.string.pf_restored)), op("restore") { vm.restore(trashed.id) })
        loaded("RoadmapA back") { s -> s.profiles.any { it.xuid == entry.xuid } }
        assertEquals("a save", save.readText())

        assertEquals(OpState.Done(Device.string(R.string.pf_trashed)), op("trash again") { vm.delete(entry.xuid) })
        val again = Device.await(vm.trash, "trashed again") { t -> t.any { it.xuid == entry.xuid } }.single { it.xuid == entry.xuid }
        assertEquals(OpState.Done(Device.string(R.string.pf_purged)), op("remove permanently") { vm.purge(again.id) })
        Device.await(vm.trash, "trash empty of RoadmapA") { t -> t.none { it.xuid == entry.xuid } }
        assertFalse(File(content, entry.xuid).exists())
        assertFalse(File(content, ".xendroid-trash/profiles/${again.id}").exists())
    }

    @Test fun aHugeImageOrNotAnImageGivesAMessageAndCreatesNothing() {
        val huge = Device.pickedFile("huge.png", pngHeader(20_000, 20_000)).second
        val tooLarge = Device.string(R.string.pf_image_too_large, 20_000, 20_000, AvatarPolicy.MAX_SIDE)
        assertEquals(OpState.Failed(Device.string(R.string.pf_reason, Device.string(R.string.pf_create_failed), tooLarge)),
            create("RoadmapBig", huge))

        val text = Device.pickedFile("notes.png", "not an image at all".toByteArray()).second
        assertEquals(OpState.Failed(Device.string(R.string.pf_reason, Device.string(R.string.pf_create_failed),
            Device.string(R.string.lib_not_an_image))), create("RoadmapBad", text))
        vm.refresh()
        val listed = loaded().profiles.map { it.gamertag }
        assertFalse(listed.toString(), "RoadmapBig" in listed || "RoadmapBad" in listed)
    }

    @Test fun aCameraSizedPhotoBecomesTheAvatar() {
        val photo = Bitmap.createBitmap(4000, 3000, Bitmap.Config.RGB_565).apply { eraseColor(0xFF3366CC.toInt()) }
        val bytes = ByteArrayOutputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 80, it); it.toByteArray() }
        photo.recycle()
        assertEquals(OpState.Done(Device.string(R.string.pf_created, "RoadmapPic")), create("RoadmapPic", Device.pickedFile("photo.jpg", bytes).second))
        val entry = loaded("RoadmapPic listed") { s -> s.profiles.any { it.gamertag == "RoadmapPic" } }.profiles.single { it.gamertag == "RoadmapPic" }
        assertTrue(entry.hasAvatar)
        assertTrue(File(ProfilePaths.profileDir(entry.xuid), "tile_64.png").length() > 0)
    }

    /** A PNG that declares [width]×[height] (a valid header and nothing else). */
    private fun pngHeader(width: Int, height: Int): ByteArray {
        val ihdr = ByteBuffer.allocate(17).put("IHDR".toByteArray()).putInt(width).putInt(height)
            .put(8).put(2).put(0).put(0).put(0).array()
        val crc = CRC32().apply { update(ihdr) }.value.toInt()
        return ByteBuffer.allocate(8 + 4 + 17 + 4)
            .put(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
            .putInt(13).put(ihdr).putInt(crc).array()
    }
}
