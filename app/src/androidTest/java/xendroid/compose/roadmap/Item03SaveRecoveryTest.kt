package xendroid.compose.roadmap

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.core.ContentPaths
import xendroid.compose.core.StorageAccess
import xendroid.compose.ui.library.ACTION_LAUNCH_GAME
import xendroid.compose.ui.library.EXTRA_GAME_URI

/**
 * Roadmap item 3: a damaged save-restore journal in content/.save-transactions. Recovery names
 * the transaction and keeps it (its directory holds the only copy from before that restore),
 * and starting a game shows the dialog naming it instead of booting. The original save is never
 * touched. The game process is started for real (the :emu Activity), with a file that is not a
 * game: storage is prepared before the game file is ever opened.
 */
@RunWith(AndroidJUnit4::class)
class Item03SaveRecoveryTest {
    private val transaction = "restore-roadmap-damaged"
    private lateinit var content: File
    private lateinit var save: File
    private val journal get() = File(content, ".save-transactions/$transaction/journal.json")
    private val owner = "E0300000000000A3"

    @Before fun setUp() {
        Device.requireTestPackage()
        content = ContentPaths.contentRoot()
        save = File(content, "$owner/4D5309C9/00000001/save.dat").apply { parentFile!!.mkdirs(); writeText("original save") }
        journal.apply { parentFile!!.mkdirs(); writeText("{ this is not a journal") }
    }

    @After fun tearDown() {
        File(content, ".save-transactions/$transaction").deleteRecursively()
        File(content, owner).deleteRecursively()
    }

    @Test fun aDamagedJournalIsReportedByNameAndKept() {
        val report = StorageAccess.saveStore().recoverTransactions()
        assertFalse(report.clean)
        assertEquals(listOf(transaction), report.failures.map { it.transaction })
        assertTrue("the journal is kept", journal.isFile)
        assertEquals("original save", save.readText())
        // Until it is recovered, the next attempt says the same and still changes nothing.
        assertEquals(listOf(transaction), StorageAccess.saveStore().recoverTransactions().failures.map { it.transaction })
    }

    @Test fun startingAGameShowsTheDialogNamingTheTransaction() {
        Device.grantAllFilesAccess()
        val notAGame = File(Device.storageRoot, "roadmap-not-a-game.iso").apply { writeBytes(ByteArray(2048)) }
        try {
            val device = UiDevice.getInstance(Device.instrumentation)
            Device.context.startActivity(Intent(ACTION_LAUNCH_GAME).apply {
                setPackage(Device.context.packageName)
                putExtra(EXTRA_GAME_URI, notAGame.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            val title = Device.string(R.string.host_launch_failed)
            assertNotNull("dialog \"$title\"", device.wait(Until.findObject(By.text(title)), 30_000))
            assertNotNull("the dialog names $transaction", device.findObject(By.textContains(transaction)))
            device.findObject(By.res("android:id/button1")).click()
            assertTrue("the game process ends", device.wait(Until.gone(By.text(title)), 10_000))
            Device.waitUntil("the :emu process exits", 15_000) {
                Device.shell("pidof ${Device.context.packageName}:emu").isBlank()
            }
            assertEquals("original save", save.readText())
            assertTrue("the journal is kept", journal.isFile)
        } finally {
            notAGame.delete()
        }
    }
}
