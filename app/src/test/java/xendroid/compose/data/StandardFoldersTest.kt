package xendroid.compose.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StandardFoldersTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun createsTheThreeFoldersUnderXenDroid() {
        val root = tmp.newFolder("storage")
        val made = StandardFolders.create(root, "Jogos")
        assertNotNull(made)
        made!!
        assertEquals(listOf("Jogos", "TU", "DLC"), made.all.map { it.name })
        assertTrue(made.all.all { it.isDirectory && it.parentFile == File(root, "XenDroid") })
        // Again: nothing to do, the same folders.
        assertEquals(made.all, StandardFolders.create(root, "Jogos")!!.all)
    }

    @Test fun aFileInTheWayMeansNoFolders() {
        val root = tmp.newFolder("storage")
        File(root, "XenDroid").writeText("a file, not a folder")
        assertNull(StandardFolders.create(root, "Games"))
    }
}
