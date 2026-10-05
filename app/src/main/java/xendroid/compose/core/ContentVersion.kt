package xendroid.compose.core

import java.io.File
import java.io.RandomAccessFile

/**
 * Round 2: the game version a title update brings, read from the package itself. A package file
 * (CON/LIVE/PIRS) keeps it in its header's execution info (0x358); an installed one is a folder
 * whose patch executable (.xexp) keeps it in its XEX2 execution info. Both pack it as major (4
 * bits), minor (4), build (16) and QFE (8), as the console shows it: "1.0.3.0".
 */
object ContentVersion {
    private const val PACKAGE_VERSION_OFFSET = 0x358L
    /** XEX2 optional header key of the execution info (its value is an offset). */
    private const val EXECUTION_INFO = 0x00040006
    private val PACKAGE_MAGICS = setOf("CON ", "LIVE", "PIRS")

    fun format(packed: Int): String =
        "${packed ushr 28 and 0xF}.${packed ushr 24 and 0xF}.${packed ushr 8 and 0xFFFF}.${packed and 0xFF}"

    /** The version of a package file's header; null when it is not a package or says none. */
    fun ofPackage(file: File): String? = runCatching {
        RandomAccessFile(file, "r").use { f ->
            if (f.length() < PACKAGE_VERSION_OFFSET + 4) return null
            val magic = ByteArray(4).also { f.readFully(it) }
            if (String(magic, Charsets.US_ASCII) !in PACKAGE_MAGICS) return null
            f.seek(PACKAGE_VERSION_OFFSET)
            f.readInt().takeIf { it != 0 }?.let(::format)
        }
    }.getOrNull()

    /** The version of an installed title update: its patch executable's, a few folders down at most. */
    fun ofInstalled(dir: File): String? {
        val executables = dir.walkTopDown().maxDepth(3)
            .filter { it.isFile && (it.name.endsWith(".xexp", ignoreCase = true) || it.name.endsWith(".xex", ignoreCase = true)) }
            .sortedBy { if (it.name.endsWith(".xexp", ignoreCase = true)) 0 else 1 }
            .take(4).toList()
        return executables.firstNotNullOfOrNull { xex ->
            runCatching { RandomAccessFile(xex, "r").use(::executionVersion) }.getOrNull()?.takeIf { it != 0 }?.let(::format)
        }
    }

    /** XEX2: after the fixed 0x18 bytes, (key, value) pairs; the execution info's value is the
     *  offset of {media id, version, base version, title id, ...}. Big-endian throughout. */
    internal fun executionVersion(f: RandomAccessFile): Int? {
        if (f.length() < 0x18) return null
        val magic = ByteArray(4).also { f.readFully(it) }
        if (String(magic, Charsets.US_ASCII) != "XEX2") return null
        f.seek(0x14)
        val count = f.readInt()
        if (count !in 0..64) return null
        for (i in 0 until count) {
            f.seek(0x18L + i * 8L)
            val key = f.readInt()
            val value = f.readInt().toLong() and 0xFFFFFFFFL
            if (key != EXECUTION_INFO) continue
            if (value + 8 > f.length()) return null
            f.seek(value + 4)
            return f.readInt()
        }
        return null
    }
}
