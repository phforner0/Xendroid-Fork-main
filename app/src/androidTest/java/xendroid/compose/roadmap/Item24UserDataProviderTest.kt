package xendroid.compose.roadmap

import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileNotFoundException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.archive.ContentLease
import xendroid.compose.core.StorageAccess

/**
 * Roadmap item 24 (L07, lote 12r): the user data in the system file manager, through the real
 * DocumentsProvider of the test package, the way the Files app calls it: what is listed and
 * hidden, copies that never replace, rename and delete, everything read-only while a game holds
 * the storage lease (reading still works), and "Games by title".
 *
 * Left for the phone: the Files app's own screens, a third-party app granted the root, and a
 * grant surviving an APK update.
 */
@RunWith(AndroidJUnit4::class)
class Item24UserDataProviderTest {
    private val context = Device.context
    private val resolver = context.contentResolver
    private val authority get() = "${context.packageName}.DocumentsProvider"
    private lateinit var root: File
    private val made = ArrayList<File>()
    private var lease: ContentLease? = null

    private val xuid = "E0300000000000A4"
    private val title = "4D5309C9"

    @Before fun setUp() {
        Device.requireTestPackage()
        root = Device.storageRoot
        listOf("config", "patches", "content", "logs").forEach { File(root, it).mkdirs() }
    }

    @After fun tearDown() {
        lease?.close()
        made.asReversed().forEach { it.deleteRecursively() }
        File(root, "content/$xuid").deleteRecursively()
        File(root, "content/0000000000000000/$title/00000002").deleteRecursively()
    }

    private fun file(path: String, text: String = "roadmap"): File =
        File(root, path).apply { parentFile!!.mkdirs(); writeText(text); made += this }

    private fun dir(path: String): File = File(root, path).apply { mkdirs(); made += this }

    private fun docUri(id: String): Uri = DocumentsContract.buildDocumentUri(authority, id)

    private data class Row(val id: String, val name: String, val flags: Int, val mime: String)

    private fun rows(cursor: Cursor?): List<Row> = cursor!!.use { c ->
        val id = c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
        val name = c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
        val flags = c.getColumnIndexOrThrow(Document.COLUMN_FLAGS)
        val mime = c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
        generateSequence { if (c.moveToNext()) Row(c.getString(id), c.getString(name), c.getInt(flags), c.getString(mime)) else null }.toList()
    }

    private fun children(parentId: String): List<Row> =
        rows(resolver.query(DocumentsContract.buildChildDocumentsUri(authority, parentId), null, null, null, null))

    private fun rootSummary(): String? = resolver.query(DocumentsContract.buildRootsUri(authority), null, null, null, null)!!.use { c ->
        assertTrue("one root", c.moveToFirst())
        assertEquals(Device.string(R.string.app_name), c.getString(c.getColumnIndexOrThrow(Root.COLUMN_TITLE)))
        assertEquals(root.absolutePath, c.getString(c.getColumnIndexOrThrow(Root.COLUMN_DOCUMENT_ID)))
        c.getString(c.getColumnIndexOrThrow(Root.COLUMN_SUMMARY))
    }

    private fun create(parent: File, name: String, text: String): Uri {
        val uri = DocumentsContract.createDocument(resolver, docUri(parent.absolutePath), "application/octet-stream", name)!!
        resolver.openOutputStream(uri, "w")!!.use { it.write(text.toByteArray()) }
        made += File(DocumentsContract.getDocumentId(uri))
        return uri
    }

    @Test fun theRootShowsTheUserDataAndHidesCachesAndBookkeeping() {
        dir("cache/roadmap")
        dir("cache0/roadmap")
        dir("cache1/roadmap")
        file("content/.xendroid-trash/profiles/roadmap.bin")
        file("content/$xuid/$title/00000001/save.dat")

        assertNull("no read-only note while nothing runs", rootSummary())
        val top = children(root.absolutePath).map { it.name }
        assertTrue(top.toString(), top.containsAll(listOf("config", "content", "patches", "logs")))
        assertTrue(top.toString(), Device.string(R.string.ud_games) in top)
        listOf("cache", "cache0", "cache1").forEach { assertFalse("$it is listed: $top", it in top) }
        val content = children(File(root, "content").absolutePath).map { it.name }
        assertTrue(content.toString(), xuid in content)
        assertFalse(content.toString(), ".xendroid-trash" in content)
        // Not even by asking for it directly (the provider answers "not found": no cursor).
        val hidden = runCatching { resolver.query(docUri(File(root, "cache/roadmap").absolutePath), null, null, null, null) }.getOrNull()
        hidden?.close()
        assertNull("a cache folder is served", hidden)
    }

    @Test fun copiesNeverReplaceAndRenameAndDeleteWork() {
        val patches = File(root, "patches")
        val first = create(patches, "roadmap-copy.patch.toml", "first")
        assertEquals("roadmap-copy.patch.toml", File(DocumentsContract.getDocumentId(first)).name)
        val second = create(patches, "roadmap-copy.patch.toml", "second")
        assertEquals("roadmap-copy.patch (1).toml", File(DocumentsContract.getDocumentId(second)).name)
        assertEquals("first", File(patches, "roadmap-copy.patch.toml").readText())
        assertEquals("second", File(patches, "roadmap-copy.patch (1).toml").readText())
        // A copy inside the provider (the Files app's own copy) also gets a free name.
        val inside = DocumentsContract.copyDocument(resolver, first, docUri(patches.absolutePath))!!
        made += File(DocumentsContract.getDocumentId(inside))
        assertEquals("roadmap-copy.patch (2).toml", File(DocumentsContract.getDocumentId(inside)).name)

        val renamed = DocumentsContract.renameDocument(resolver, second, "roadmap-renamed.toml")!!
        made += File(DocumentsContract.getDocumentId(renamed))
        assertTrue(File(patches, "roadmap-renamed.toml").isFile)
        assertFalse(File(patches, "roadmap-copy.patch (1).toml").exists())
        assertTrue(DocumentsContract.deleteDocument(resolver, renamed))
        assertFalse(File(patches, "roadmap-renamed.toml").exists())
        // Search finds by name and never reaches hidden folders.
        dir("cache/roadmap-copy-hidden")
        // As the Files app asks since Android 10: the words in queryArgs (a search with none is
        // refused by Android itself before it reaches the provider, as the first run on a phone showed).
        val search = android.os.Bundle().apply { putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, "roadmap-copy") }
        val found = rows(resolver.query(DocumentsContract.buildSearchDocumentsUri(authority, "root", "roadmap-copy"), null, search, null))
        assertTrue(found.toString(), found.any { it.name == "roadmap-copy.patch.toml" })
        assertFalse(found.toString(), found.any { it.name == "roadmap-copy-hidden" })
    }

    @Test fun whileAGameRunsNothingChangesButReadingWorks() {
        val log = file("logs/roadmap-xe.log", "log line")
        val patch = file("patches/roadmap-busy.patch.toml", "keep me")
        lease = StorageAccess.acquire()        // what the game process holds while it runs

        assertEquals(Device.string(R.string.ud_read_only_now), rootSummary())
        children(File(root, "patches").absolutePath).forEach { assertEquals("${it.name} flags", 0, it.flags) }
        val create = assertThrows(FileNotFoundException::class.java) {
            DocumentsContract.createDocument(resolver, docUri(File(root, "patches").absolutePath), "text/plain", "roadmap-new.txt")
        }
        assertTrue(create.message, create.message!!.startsWith("Close the running game first"))
        assertFalse(File(root, "patches/roadmap-new.txt").exists())
        assertThrows(FileNotFoundException::class.java) { DocumentsContract.deleteDocument(resolver, docUri(patch.absolutePath)) }
        assertThrows(FileNotFoundException::class.java) { resolver.openOutputStream(docUri(patch.absolutePath), "w") }
        assertEquals("keep me", patch.readText())
        // Reading (xe.log while the game runs, a save copied to Downloads) still works.
        assertEquals("log line", resolver.openInputStream(docUri(log.absolutePath))!!.use { it.readBytes().toString(Charsets.UTF_8) })

        lease!!.close(); lease = null
        assertNull(rootSummary())
        assertTrue(DocumentsContract.deleteDocument(resolver, docUri(patch.absolutePath)))
        assertFalse(patch.exists())
    }

    @Test fun gamesByTitleGathersEachGamesDataReadOnly() {
        file("content/$xuid/$title/00000001/save.dat")
        file("content/0000000000000000/$title/00000002/dlc.pkg")
        file("config/$title.config.toml", "[GPU]\n")
        file("patches/$title - roadmap.patch.toml")

        val games = children("xendroid:games")
        val game = games.firstOrNull { it.id == "xendroid:game:$title" }
        assertNotNull(games.toString(), game)
        assertEquals(0, game!!.flags)
        val entries = children(game.id)
        val profile = entries.single { it.name == Device.string(R.string.ud_profile_data, xuid) }
        val console = entries.single { it.name == Device.string(R.string.ud_console_data) }
        assertEquals(File(root, "content/$xuid/$title").absolutePath, profile.id)
        assertEquals(File(root, "content/0000000000000000/$title").absolutePath, console.id)
        // Gathering folders: copy out, nothing renamed, moved or deleted from here.
        listOf(profile, console).forEach {
            assertEquals(Document.MIME_TYPE_DIR, it.mime)
            assertEquals("${it.name} flags", 0, it.flags and (Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME or Document.FLAG_SUPPORTS_MOVE))
            assertTrue(it.flags and Document.FLAG_SUPPORTS_COPY != 0)
        }
        assertTrue(entries.toString(), entries.any { it.name == "$title.config.toml" })
        assertTrue(entries.toString(), entries.any { it.name == "$title - roadmap.patch.toml" })
        // The save opens through the gathering folder: it is the real file.
        val save = children(profile.id).single().let { children(it.id).single() }
        assertEquals("roadmap", resolver.openInputStream(docUri(save.id))!!.use { it.readBytes().toString(Charsets.UTF_8) })

        lease = StorageAccess.acquire()
        children(game.id).forEach { assertEquals("${it.name} flags while a game runs", 0, it.flags) }
    }
}
