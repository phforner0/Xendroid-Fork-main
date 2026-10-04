package xendroid.compose.data

import java.io.File
import java.io.RandomAccessFile

/**
 * Lote 6: how many games a folder holds, for the folder browser and the game folders screen,
 * before the user picks it. The library's own walk ([LibraryWalker]) with a small budget, and
 * only what is surely a game: an Xbox 360 disc image (not another console's ISO), a ZAR file, a
 * folder with default.xex, and a GOD or XBLA container in its content-type folder (an
 * extensionless file elsewhere may be anything).
 */
object FolderGames {
    data class Count(val games: Int, val partial: Boolean)

    /** Content-type folders a game container sits in: <Title ID>/<type>/<container>. */
    private val GAME_TYPES = setOf("00007000", "000D0000", "00004000", "00080000", "02000000")

    /** Where a disc's game partition may start, as the core looks (xenia/vfs/gdfx_util.h); the
     *  XDVDFS magic sits at its sector 32. */
    private val PARTITIONS = longArrayOf(0x00000000, 0x0000FB20, 0x00020600, 0x02080000, 0x0FD90000)
    private val MAGIC = "MICROSOFT*XBOX*MEDIA".toByteArray(Charsets.US_ASCII)
    private const val SECTOR = 2048L

    /** An Xbox 360 (or Xbox) disc image: the XDVDFS magic where the core looks for it. A PS2 or
     *  other console's ISO in the same folder is not counted. */
    fun isXboxDisc(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { image ->
            val length = image.length()
            val read = ByteArray(MAGIC.size)
            PARTITIONS.any { start ->
                val at = start + 32 * SECTOR
                at + MAGIC.size <= length && run { image.seek(at); image.readFully(read); read.contentEquals(MAGIC) }
            }
        }
    }.getOrDefault(false)

    fun count(dir: File, maxEntries: Int = 4000, maxDepth: Int = 8): Count {
        val walk = LibraryWalker(maxDepth = maxDepth, maxEntries = maxEntries).walk(listOf(dir.absolutePath))
        val games = walk.candidates.count { candidate ->
            when (candidate) {
                is LibraryWalker.Candidate.XexFolder -> true
                is LibraryWalker.Candidate.GameFile -> when (GameFormat.fromFileName(candidate.file.name)) {
                    GameFormat.ISO -> isXboxDisc(candidate.file)
                    GameFormat.ZAR -> true
                    GameFormat.GOD -> candidate.file.parentFile?.name?.uppercase() in GAME_TYPES
                    else -> false
                }
            }
        }
        return Count(games, walk.truncated)
    }
}
