package xendroid.compose.core

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Redacts diagnostics only in the share copy. Original session logs remain intact on disk. */
object LogRedactor {
    private const val HEX16 = "[0-9A-Fa-f]{16}"

    /** Applied in order: specific Xenia log formats first, then generic key/value forms. */
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("(?i)/(?:storage/(?:emulated/\\d+|[\\w.-]+)|sdcard|data/user/\\d+)/[^\\s\"'<>]+") to "[storage-path]",
        Regex("(?i)[A-Z]:\\\\Users\\\\[^\\s\"']+") to "[user-path]",
        Regex("""(?i)["']?\b(?:access[_-]?token|refresh[_-]?token|auth[_-]?token|password|authorization|api[_-]?key)\b["']?\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s,;]+)""") to "[credential]",
        Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~-]{8,}") to "[credential]",
        // profile_manager.cc: "Loaded {gamertag} (GUID: {xuid}) to slot {n}".
        Regex("\\bLoaded .+? \\(GUID: (?:0x)?$HEX16\\)") to "Loaded [identity] (GUID: [xuid])",
        // user_profile.cc: "User {name} (XUID: {xuid}) ...".
        Regex("\\bUser .+? \\(XUID: (?:0x)?$HEX16\\)") to "User [identity] (XUID: [xuid])",
        // profile_manager.cc account/profile listing lines name the XUID bare.
        Regex("\\b(Loading Account:|Adding profile|Reloading GPDs for profile|Profile) (?:0x)?$HEX16\\b") to "$1 [xuid]",
        // Keys that END in xuid too (logged_profile_slot_0_xuid = "..." in the config dump).
        Regex("""(?i)(["']?\b\w*(?:xuid|gamertag|username|profile[_-]?name)\w*\b["']?\s*[:=]).*$""") to "$1 [identity]",
        Regex("(?i)\\b(?:xuid|guid)\\b\\s*[:=]?\\s*(?:0x)?$HEX16\\b") to "[xuid]",
        Regex("(?i)(?<=content[/\\\\])[a-f0-9]{16}(?=[/\\\\])") to "[xuid]",
        Regex("(?<![\\w.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w.])") to "[ip]",
        Regex("(?i)(?<![\\w:])(?:[a-f0-9]{1,4}:){2,}[a-f0-9]{0,4}(?![\\w:])") to "[ip]",
        Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b") to "[email]",
    )

    fun redact(line: String): String = rules.fold(line) { text, (pattern, replacement) -> pattern.replace(text, replacement) }
}

/** Flat, bounded ZIP: no nested raw session archives or binary crash dumps in shared diagnostics. */
object SanitizedSessionExport {
    private const val MAX_ARCHIVES = 16
    private const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
    private const val MAX_ENTRY_CHARS = 4L * 1024 * 1024
    private const val MAX_TOTAL_CHARS = 32L * 1024 * 1024
    private const val MAX_LINE_CHARS = 8192
    private val allowedName = Regex("(?:(?:previous-[0-9-]+-)?(?:xe\\.log|logcat\\.txt)|logcat-current\\.txt|context\\.json|exit-info\\.txt|exit-trace-\\d+\\.txt)")

    fun create(
        destination: File,
        histories: List<File>,
        current: List<Pair<File, String>>,
    ): File? {
        if (histories.isEmpty() && current.isEmpty()) return null
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        var written = 0
        var remaining = MAX_TOTAL_CHARS
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                histories.takeLast(MAX_ARCHIVES).forEach { archive ->
                    if (!archive.isFile || archive.length() > MAX_ARCHIVE_BYTES ||
                        !archive.name.matches(Regex("session_[A-Za-z0-9_-]+\\.zip"))) return@forEach
                    ZipInputStream(FileInputStream(archive).buffered()).use { source ->
                        var entries = 0
                        while (entries++ < 12 && remaining > 0) {
                            val entry = source.nextEntry ?: break
                            // Skip, don't stop: a binary tombstone may precede exit-info.txt.
                            // The archive is size-capped above and the entry count bounds the work.
                            if (entry.isDirectory || !allowedName.matches(entry.name)) continue
                            zip.putNextEntry(ZipEntry("sessions/${archive.nameWithoutExtension}/${entry.name}"))
                            val limit = minOf(remaining, MAX_ENTRY_CHARS)
                            val consumed = writeRedacted(source, zip, limit)
                            remaining -= consumed
                            zip.closeEntry()
                            written++
                            // nextEntry drains the previous entry. Stop instead when it exceeded
                            // the limit: don't decompress the rest of a potentially hostile ZIP.
                            if (consumed == limit) break
                        }
                    }
                }
                current.forEach { (file, name) ->
                    if (!file.isFile || !allowedName.matches(name) || remaining <= 0) return@forEach
                    zip.putNextEntry(ZipEntry("current/$name"))
                    FileInputStream(file).use { source ->
                        val skip = (file.length() - MAX_ENTRY_CHARS).coerceAtLeast(0)
                        if (skip > 0) {
                            source.skip(skip)
                            // Tail snapshots can begin mid-line; drop it rather than leaking a
                            // token suffix that no complete-line pattern could recognize.
                            while (source.read().let { it >= 0 && it != '\n'.code }) { }
                        }
                        remaining -= writeRedacted(source, zip, minOf(remaining, MAX_ENTRY_CHARS))
                    }
                    zip.closeEntry()
                    written++
                }
            }
            if (written == 0) return null
            if (!temporary.renameTo(destination)) error("Could not finalize diagnostics")
            return destination
        } finally {
            temporary.delete()
        }
    }

    /** Read one character at a time from a buffered reader to bound long lines and ZIP bombs. */
    private fun writeRedacted(input: InputStream, zip: ZipOutputStream, limit: Long): Long {
        val reader = InputStreamReader(input, Charsets.UTF_8).buffered()
        val line = StringBuilder()
        var dropping = false
        var count = 0L
        while (count < limit) {
            val c = reader.read()
            if (c < 0) break
            count++
            if (c == '\n'.code) {
                val text = if (dropping) "[long line omitted]" else LogRedactor.redact(line.toString())
                zip.write("$text\n".toByteArray(Charsets.UTF_8))
                line.setLength(0)
                dropping = false
            } else if (!dropping && line.length < MAX_LINE_CHARS) {
                line.append(c.toChar())
            } else {
                dropping = true
            }
        }
        if (dropping) zip.write("[long line omitted]\n".toByteArray(Charsets.UTF_8))
        else if (line.isNotEmpty()) zip.write((LogRedactor.redact(line.toString()) + "\n").toByteArray(Charsets.UTF_8))
        if (count == limit) zip.write("[diagnostics truncated]\n".toByteArray(Charsets.UTF_8))
        return count
    }
}
