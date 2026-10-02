package xendroid.compose.saves

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.archive.ContentLease

/** Data stored under one title (or the dashboard) inside a profile's content tree. */
data class TitleUsage(val titleId: String, val files: Int, val bytes: Long)

/** Everything a profile deletion takes away: content/<XUID> holds the account AND the
 * saves of every game played with it. */
data class ProfileContentSummary(val xuid: String, val titles: List<TitleUsage>, val truncated: Boolean) {
    val files: Int get() = titles.sumOf { it.files }
    val bytes: Long get() = titles.sumOf { it.bytes }
    /** Title folders other than the dashboard's account/profile data. */
    val gameTitles: List<TitleUsage> get() = titles.filter { it.titleId != DASHBOARD }

    companion object { const val DASHBOARD = "FFFE07D1" }
}

data class TrashedProfile(val id: String, val xuid: String, val deletedAt: Long)

/**
 * Deleting a profile moves its whole content/<XUID> tree into a trash folder on the
 * same filesystem with one atomic rename: nothing is lost if the process dies, and the
 * profile (with every save) can be restored until the user purges it. Every mutation
 * takes the storage lease as proof that no game or other content job is running.
 * The trash lives under content/ but its name never matches Xenia's XUID folder
 * pattern, so the emulator does not list it as a profile.
 */
class ProfileTrash(private val contentRoot: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val xuidPattern = Regex("[0-9A-F]{16}")
    private val idPattern = Regex("([0-9A-F]{16})-([0-9]{13})")
    private val trashRoot = File(contentRoot, ".xendroid-trash/profiles")

    private fun xuid(raw: String): String = raw.uppercase().also {
        require(xuidPattern.matches(it)) { "Invalid XUID" }
        // The all-zero "machine" XUID holds installed DLC and title updates, not a profile.
        require(it != "0000000000000000") { "Not a profile" }
    }

    fun summarize(rawXuid: String, maxEntries: Int = 200_000): ProfileContentSummary {
        val id = xuid(rawXuid)
        val root = ArchiveFiles.resolve(contentRoot, id)
        if (!root.isDirectory) return ProfileContentSummary(id, emptyList(), false)
        var entries = 0
        var truncated = false
        val titles = root.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.map { title ->
            var files = 0
            var bytes = 0L
            if (!truncated) Files.walk(title.toPath()).use { walk ->
                val iterator = walk.iterator()
                while (iterator.hasNext()) {
                    val path = iterator.next()
                    if (++entries > maxEntries) { truncated = true; break }
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        files++; bytes += Files.size(path)
                    }
                }
            }
            TitleUsage(title.name.uppercase(), files, bytes)
        }
        return ProfileContentSummary(id, titles, truncated)
    }

    fun moveToTrash(@Suppress("UNUSED_PARAMETER") lease: ContentLease, rawXuid: String): TrashedProfile {
        val id = xuid(rawXuid)
        val source = ArchiveFiles.resolve(contentRoot, id)
        require(source.isDirectory) { "Profile folder not found" }
        check(trashRoot.isDirectory || trashRoot.mkdirs()) { "Cannot create the profile trash" }
        var time = clock()
        var target = File(trashRoot, "$id-${"%013d".format(time)}")
        while (target.exists()) target = File(trashRoot, "$id-${"%013d".format(++time)}")
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return TrashedProfile(target.name, id, time)
    }

    fun list(): List<TrashedProfile> = trashRoot.listFiles().orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { dir -> idPattern.matchEntire(dir.name)?.let { TrashedProfile(dir.name, it.groupValues[1], it.groupValues[2].toLong()) } }
        .sortedByDescending { it.deletedAt }

    /** Never merges into or replaces an existing profile folder with the same XUID. */
    fun restore(@Suppress("UNUSED_PARAMETER") lease: ContentLease, trashId: String): String {
        val entry = idPattern.matchEntire(trashId) ?: throw IllegalArgumentException("Invalid trash entry")
        val id = xuid(entry.groupValues[1])
        val source = ArchiveFiles.resolve(trashRoot, trashId)
        require(source.isDirectory) { "Trash entry not found" }
        val target = ArchiveFiles.resolve(contentRoot, id)
        require(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "A profile with this XUID exists again; delete or rename it first"
        }
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return id
    }

    fun purge(@Suppress("UNUSED_PARAMETER") lease: ContentLease, trashId: String) {
        require(idPattern.matches(trashId)) { "Invalid trash entry" }
        ArchiveFiles.deleteTree(ArchiveFiles.resolve(trashRoot, trashId))
    }
}
