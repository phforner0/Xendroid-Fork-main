package xendroid.compose.roadmap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.data.CoverPolicy
import xendroid.compose.data.CoverStore

/**
 * Roadmap item 19 (L05): a game's cover from a picked photo, on a game of the phone's `gameDir`
 * (the game file is never touched; covers live in the test package's files/covers). A big photo
 * (4000×3000) is kept at most 512 px on the long side; a phone photo stored sideways with an EXIF
 * turn comes out upright; a file that is not an image, or a damaged PNG, gives "not a supported
 * image" and changes nothing; clearing the app's cache keeps the cover; every disc of the title
 * shows it; "Use the game's own icon" removes it.
 *
 * Left for the phone: renaming or moving the game's file (it is the user's), the shortcut (the
 * launcher asks), and looking at the tile.
 */
@RunWith(AndroidJUnit4::class)
class Item19CoversTest {
    private val context = Device.context

    private fun photo(name: String, width: Int, height: Int, exifOrientation: Int? = null): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply { eraseColor(0xFF2266AA.toInt()) }
        val (file, uri) = Device.pickedFile(name, ByteArray(0))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        exifOrientation?.let { turn ->
            ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "$turn"); saveAttributes() }
        }
        return uri
    }

    private fun size(file: File): Pair<Int, Int> = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(file.path, it) }.let { it.outWidth to it.outHeight }

    @Test fun aPickedPhotoBecomesTheCoverOfEveryDisc() {
        Library(listOf(GameRun.gameDir())).use { library ->
            val vm = library.viewModel()
            val game = vm.rescan("the games folder").games.firstOrNull { CoverStore.normalize(it.titleId) != null }
            assumeTrue("no game with a Title ID in the games folder", game != null)
            val custom = File(File(context.filesDir, "covers"), "${CoverStore.normalize(game!!.titleId)}.custom.png")
            try {
                assertTrue(runBlocking { vm.setCustomCover(game, photo("big.jpg", 4000, 3000)) }.isSuccess)
                assertEquals(CoverPolicy.TARGET_SIDE to CoverPolicy.TARGET_SIDE * 3 / 4, size(custom))

                // Taken in portrait, stored sideways with an EXIF turn: upright.
                assertTrue(runBlocking { vm.setCustomCover(game, photo("portrait.jpg", 4000, 3000, ExifInterface.ORIENTATION_ROTATE_90)) }.isSuccess)
                assertEquals(CoverPolicy.TARGET_SIDE * 3 / 4 to CoverPolicy.TARGET_SIDE, size(custom))

                // Not an image, or a damaged PNG: the message, and the cover stays as it was.
                val before = custom.readBytes()
                val notImage = runBlocking { vm.setCustomCover(game, Device.pickedFile("notes.png", "not an image".toByteArray()).second) }
                assertEquals(Device.string(R.string.lib_not_an_image), notImage.exceptionOrNull()?.message)
                val damaged = runBlocking { vm.setCustomCover(game, Device.pickedFile("damaged.png", damagedPng()).second) }
                assertTrue(damaged.isFailure)
                assertArrayEquals(before, custom.readBytes())

                // Clearing the app's cache (system settings) keeps it; every disc of the title shows it.
                context.cacheDir.listFiles().orEmpty().filter { it.name != "shared-logs" }.forEach { it.deleteRecursively() }
                assertEquals(custom, vm.iconFileOrFallback(game))
                vm.discsOfTitle(game).forEach { disc -> assertEquals(disc.name, custom, vm.iconFileOrFallback(disc)) }
                GameRun.note(19, "cover of ${game.titleId}: ${vm.discsOfTitle(game).size} disc(s) share it")

                // "Use the game's own icon".
                runBlocking { vm.clearCustomCover(game) }
                assertFalse(custom.exists())
                assertFalse(vm.hasCustomCover(game))
            } finally {
                custom.delete()
            }
        }
    }

    /** A PNG header for a 100×100 image followed by garbage instead of image data. */
    private fun damagedPng(): ByteArray {
        val ihdr = ByteBuffer.allocate(17).put("IHDR".toByteArray()).putInt(100).putInt(100).put(8).put(2).put(0).put(0).put(0).array()
        val crc = CRC32().apply { update(ihdr) }.value.toInt()
        return ByteBuffer.allocate(8 + 4 + 17 + 4 + 64)
            .put(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
            .putInt(13).put(ihdr).putInt(crc).put(ByteArray(64) { 0x5A }).array()
    }
}
