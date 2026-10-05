package xendroid.compose.saves

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.archive.ArchiveLimits
import xendroid.compose.archive.ContentLease

@Serializable
data class SaveFileRecord(val path: String, val bytes: Long, val sha256: String)
@Serializable
data class SaveManifest(val version: Int = 1, val titleId: String, val xuids: List<String>,
                        val scopes: List<String>, val directories: List<String>, val files: List<SaveFileRecord>)
data class SaveProfile(val xuid: String, val saveFiles: Int, val bytes: Long, val hasProfile: Boolean)
data class PreparedSaveRestore(val directory: File, val manifest: SaveManifest, val conflicts: List<String>)

/** One interrupted restore that could not be rolled back. Its directory is kept intact:
 * `original/` holds the data that was on the device before the restore. */
data class RecoveryFailure(val transaction: String, val message: String)
data class RecoveryReport(
    val rolledBack: List<String> = emptyList(),
    val failures: List<RecoveryFailure> = emptyList(),
    val discardedStages: List<String> = emptyList(),
) {
    val clean: Boolean get() = failures.isEmpty()
    val summary: String get() = failures.joinToString("; ") { "${it.transaction}: ${it.message}" }
}

class SaveRecoveryException(val report: RecoveryReport) : IllegalStateException(
    "An interrupted save restore could not be rolled back (${report.summary}). " +
        "The data from before that restore is kept in content/.save-transactions; " +
        "no game or save operation can run until it is recovered.")

@Serializable
private data class SaveSwap(val scope: String, val hadOriginal: Boolean)
@Serializable
private data class SaveJournal(val version: Int = 1, val committed: Boolean = false, val swaps: List<SaveSwap>)

/** Xbox content layout is preserved verbatim: save data AND Headers/00000001,
 * optional dashboard profile, original XUID. No DLC/TU/installed game roots. */
class SaveBackupStore(private val contentRoot: File, private val leaseRoot: File = contentRoot,
                      private val limits: ArchiveLimits = ArchiveLimits(),
                      private val clock: () -> Long = System::currentTimeMillis,
                      /** Test hook: throwing here simulates a process death inside a rollback. */
                      private val rollbackCheckpoint: (String) -> Unit = {}) {
    private companion object { const val STALE_STAGE_MS = 24L * 60 * 60 * 1000 }
    private val json = Json { prettyPrint = true }
    private val titlePattern = Regex("[0-9A-F]{8}")
    private val xuidPattern = Regex("[0-9A-F]{16}")
    private val saveType = "00000001"
    private val dashboard = "FFFE07D1"
    private val profileType = "00010000"

    private fun title(raw: String): String = raw.uppercase().also {
        require(titlePattern.matches(it) && it != "00000000") { "Invalid Title ID" }
    }
    private fun xuid(raw: String): String = raw.uppercase().also {
        require(xuidPattern.matches(it)) { "Invalid XUID" }
    }

    fun profiles(rawTitle: String): List<SaveProfile> {
        val tid = title(rawTitle)
        if (!contentRoot.isDirectory) return emptyList()
        return contentRoot.listFiles().orEmpty().filter { xuidPattern.matches(it.name) && it.isDirectory }
            .mapNotNull { root ->
                val files = regularFiles(ArchiveFiles.resolve(contentRoot, "${root.name}/$tid/$saveType"))
                if (files.isEmpty()) null else SaveProfile(root.name, files.size, files.sumOf { it.length() },
                    ArchiveFiles.resolve(contentRoot, "${root.name}/$dashboard/$profileType/${root.name}").isDirectory)
            }.sortedBy { it.xuid }
    }

    /**
     * Cheap change detector for what [export] would include for every profile with saves
     * of this title: relative paths, sizes and modification times. Only an optimization
     * (skipping an unchanged automatic backup); the backup itself is still verified.
     */
    fun fingerprint(rawTitle: String, includeProfiles: Boolean = true): String {
        val tid = title(rawTitle)
        val digest = MessageDigest.getInstance("SHA-256")
        profiles(tid).map { it.xuid }.forEach { id ->
            val scopes = listOf("$id/$tid/$saveType", "$id/$tid/Headers/$saveType") +
                if (includeProfiles) listOf("$id/$dashboard/$profileType/$id") else emptyList()
            scopes.map { ArchiveFiles.resolve(contentRoot, it) }.filter { it.isDirectory }.forEach { scope ->
                regularFiles(scope).sortedBy { it.path }.forEach { f ->
                    digest.update("${f.relativeTo(contentRoot).invariantSeparatorsPath}\u0000${f.length()}\u0000${f.lastModified()}\n".toByteArray())
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun export(rawTitle: String, selectedXuids: List<String>, output: File, includeProfiles: Boolean = true,
               checkCancelled: () -> Unit = {}): File {
        val tid = title(rawTitle)
        val ids = selectedXuids.map(::xuid).distinct().sorted()
        require(ids.isNotEmpty()) { "Select a profile with saves" }
        ContentLease.acquire(leaseRoot).use {
            requireRecovered()
            val scopes = ids.flatMap { id ->
                listOf("$id/$tid/$saveType", "$id/$tid/Headers/$saveType") +
                    if (includeProfiles) listOf("$id/$dashboard/$profileType/$id") else emptyList()
            }.filter { ArchiveFiles.resolve(contentRoot, it).isDirectory }
            require(scopes.any { it.endsWith("/$saveType") && !it.contains("/Headers/") }) { "No saved games found" }
            val directories = mutableListOf<String>()
            val sources = mutableListOf<File>()
            for (scope in scopes) {
                Files.walk(ArchiveFiles.resolve(contentRoot, scope).toPath()).use { walk ->
                    walk.forEach { path ->
                        checkCancelled()
                        require(!Files.isSymbolicLink(path)) { "Save contains symbolic link" }
                        if (Files.isDirectory(path)) directories.add(path.toFile().relativeTo(contentRoot).invariantSeparatorsPath)
                        else {
                            require(Files.isRegularFile(path)) { "Unsupported save file" }
                            sources.add(path.toFile())
                        }
                        require(sources.size + directories.size <= limits.entries) { "Too many save entries" }
                    }
                }
            }
            require(sources.sumOf { it.length() } <= limits.totalBytes && sources.all { it.length() <= limits.entryBytes }) { "Saves exceed backup limits" }
            output.parentFile!!.mkdirs()
            val temp = File.createTempFile(".save-export-", ".zip", output.parentFile)
            try {
                val records = mutableListOf<SaveFileRecord>()
                var totalBytes = 0L
                ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                    sources.sortedBy { it.path }.forEach { source ->
                        checkCancelled()
                        val path = source.relativeTo(contentRoot).invariantSeparatorsPath
                        val digest = MessageDigest.getInstance("SHA-256")
                        var bytes = 0L
                        zip.putNextEntry(ZipEntry("payload/$path").apply { time = 0 })
                        source.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                checkCancelled()
                                val n = input.read(buffer)
                                if (n < 0) break
                                bytes += n
                                totalBytes += n
                                require(bytes <= limits.entryBytes && totalBytes <= limits.totalBytes) { "Save grew while exporting" }
                                digest.update(buffer, 0, n); zip.write(buffer, 0, n)
                            }
                        }
                        zip.closeEntry()
                        records.add(SaveFileRecord(path, bytes, digest.digest().joinToString("") { "%02x".format(it) }))
                    }
                    zip.putNextEntry(ZipEntry("manifest.json").apply { time = 0 })
                    val manifestBytes = json.encodeToString(SaveManifest(titleId = tid, xuids = ids,
                        scopes = scopes.sorted(), directories = directories.sorted(), files = records)).toByteArray()
                    require(manifestBytes.size <= 4 * 1024 * 1024) { "Save manifest exceeds backup limits" }
                    zip.write(manifestBytes)
                    zip.closeEntry()
                }
                require(temp.length() <= limits.packedBytes) { "Backup ZIP exceeds size limit" }
                Files.move(temp.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                return output
            } finally { temp.delete() }
        }
    }

    /** Prepare on the SAME filesystem as the destination, without changing any saves. */
    fun prepare(archive: File, expectedTitle: String, checkCancelled: () -> Unit = {}): PreparedSaveRestore {
        val tid = title(expectedTitle)
        contentRoot.mkdirs()
        ContentLease.acquire(leaseRoot).use {
            requireRecovered()
            val transactions = File(contentRoot, ".save-transactions").apply { mkdirs() }
            val stage = Files.createTempDirectory(transactions.toPath(), "restore-").toFile()
            val payload = File(stage, "unpacked").apply { mkdirs() }
            try {
                ArchiveFiles.unpack(archive, payload, limits, checkCancelled)
                val manifestFile = File(payload, "manifest.json")
                require(manifestFile.isFile && manifestFile.length() <= 4L * 1024 * 1024) { "Missing/oversized save manifest" }
                val manifest = json.decodeFromString<SaveManifest>(manifestFile.readText())
                validateManifest(manifest, tid)
                val expected = manifest.files.map { "payload/${it.path}" }.toSet() + "manifest.json"
                require(regularFiles(payload).map { it.relativeTo(payload).invariantSeparatorsPath }.toSet() == expected) { "Unexpected or missing backup files" }
                val data = File(payload, "payload").apply { mkdirs() }
                manifest.directories.forEach { ArchiveFiles.resolve(data, it).mkdirs() }
                manifest.files.forEach { record ->
                    checkCancelled()
                    val file = ArchiveFiles.resolve(data, record.path)
                    require(file.isFile && file.length() == record.bytes && ArchiveFiles.sha256(file) == record.sha256) { "Save integrity check failed" }
                }
                manifest.scopes.forEach { require(ArchiveFiles.resolve(data, it).isDirectory) { "Missing save scope" } }
                val conflicts = manifest.scopes.filter { ArchiveFiles.resolve(contentRoot, it).exists() }
                return PreparedSaveRestore(stage, manifest, conflicts)
            } catch (e: Exception) { ArchiveFiles.deleteTree(stage); throw e }
        }
    }

    /** All swaps are recoverable using a persisted journal. Original directories stay
     * in the transaction after success as a local pre-restore backup. */
    fun restore(prepared: PreparedSaveRestore, overwrite: Boolean, overwriteProfiles: Boolean = false,
                checkpoint: (String) -> Unit = {}): File {
        ContentLease.acquire(leaseRoot).use {
            require(prepared.directory.canonicalFile.parentFile == File(contentRoot, ".save-transactions").canonicalFile)
            requireRecovered(except = prepared.directory.name)
            validateManifest(prepared.manifest, prepared.manifest.titleId)
            val data = File(prepared.directory, "unpacked/payload")
            // Verify again after the confirmation UI: a stale staging directory is not trusted.
            prepared.manifest.files.forEach {
                val f = ArchiveFiles.resolve(data, it.path)
                require(f.isFile && f.length() == it.bytes && ArchiveFiles.sha256(f) == it.sha256) { "Staged save changed" }
            }
            val scopes = prepared.manifest.scopes.filterNot { scope ->
                isProfileScope(scope) && !overwriteProfiles && ArchiveFiles.resolve(contentRoot, scope).exists()
            }
            if (!overwrite) require(scopes.none { ArchiveFiles.resolve(contentRoot, it).exists() }) { "Save conflicts require confirmation" }
            val journal = SaveJournal(swaps = scopes.map { SaveSwap(it, ArchiveFiles.resolve(contentRoot, it).exists()) })
            val journalFile = File(prepared.directory, "journal.json")
            require(!journalFile.exists()) { "Restore already attempted; prepare a fresh preview" }
            ArchiveFiles.atomicText(journalFile, json.encodeToString(journal))
            try {
                journal.swaps.forEachIndexed { index, swap ->
                    checkpoint("before:$index")
                    val target = ArchiveFiles.resolve(contentRoot, swap.scope)
                    val backup = ArchiveFiles.resolve(File(prepared.directory, "original"), swap.scope)
                    if (swap.hadOriginal) {
                        backup.parentFile!!.mkdirs()
                        Files.move(target.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    }
                    target.parentFile!!.mkdirs()
                    Files.move(ArchiveFiles.resolve(data, swap.scope).toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    checkpoint("after:$index")
                }
                ArchiveFiles.atomicText(journalFile, json.encodeToString(journal.copy(committed = true)))
                return prepared.directory
            } catch (e: Exception) {
                try { rollback(prepared.directory, journal) } catch (rollback: Exception) { e.addSuppressed(rollback) }
                throw e
            }
        }
    }

    fun discard(prepared: PreparedSaveRestore) {
        require(prepared.directory.canonicalFile.parentFile == File(contentRoot, ".save-transactions").canonicalFile)
        // Never delete a failed/committed journal: it contains the only pre-restore copy.
        if (!File(prepared.directory, "journal.json").exists()) ArchiveFiles.deleteTree(prepared.directory)
    }

    fun recoverTransactions(): RecoveryReport = ContentLease.acquire(leaseRoot).use { recoverTransactions(it) }

    /** For a caller that keeps the storage lease for longer (a game session): recover
     * under that same lease, so nothing can start a restore in between. */
    fun recoverTransactions(lease: ContentLease): RecoveryReport {
        require(lease.root == leaseRoot.canonicalFile) { "Lease of another storage root" }
        return recoverTransactionsLocked()
    }

    private fun requireRecovered(except: String? = null) {
        val report = recoverTransactionsLocked(except)
        if (!report.clean) throw SaveRecoveryException(report)
    }

    /** Each transaction is recovered on its own: one damaged journal must not stop the
     * rollback of the others, and a failed one is reported, never deleted. */
    private fun recoverTransactionsLocked(except: String? = null): RecoveryReport {
        val rolledBack = mutableListOf<String>()
        val failures = mutableListOf<RecoveryFailure>()
        val discarded = mutableListOf<String>()
        val now = clock()
        File(contentRoot, ".save-transactions").listFiles().orEmpty()
            .filter { it.isDirectory && it.name != except }.sortedBy { it.name }.forEach { dir ->
                try {
                    val file = ArchiveFiles.resolve(dir, "journal.json")
                    if (!file.exists()) {
                        // A preview that was never confirmed: only staged copies, no originals.
                        // Kept for a day so an open preview dialog is not pulled away.
                        if (now - dir.lastModified() > STALE_STAGE_MS && !File(dir, "original").exists()) {
                            ArchiveFiles.deleteTree(dir); discarded.add(dir.name)
                        }
                        return@forEach
                    }
                    require(file.isFile && file.length() <= 1024 * 1024) { "Restore journal unreadable or too large" }
                    val journal = json.decodeFromString<SaveJournal>(file.readText())
                    require(journal.version == 1 && journal.swaps.size <= 128) { "Unsupported restore journal" }
                    journal.swaps.forEach { validateScope(it.scope, it.scope.split('/')[1], listOf(it.scope.split('/')[0])) }
                    if (!journal.committed) { rollback(dir, journal); rolledBack.add(dir.name) }
                } catch (e: Exception) {
                    failures.add(RecoveryFailure(dir.name, e.message ?: e.javaClass.simpleName))
                }
            }
        return RecoveryReport(rolledBack, failures, discarded)
    }

    private fun rollback(dir: File, journal: SaveJournal) {
        journal.swaps.asReversed().forEachIndexed { index, swap ->
            rollbackCheckpoint("rollback:$index")
            val target = ArchiveFiles.resolve(contentRoot, swap.scope)
            val original = ArchiveFiles.resolve(File(dir, "original"), swap.scope)
            val incoming = ArchiveFiles.resolve(File(dir, "unpacked/payload"), swap.scope)
            if (original.exists()) {
                ArchiveFiles.deleteTree(target)
                target.parentFile!!.mkdirs()
                Files.move(original.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } else if (!swap.hadOriginal && !incoming.exists()) {
                ArchiveFiles.deleteTree(target)
            }
        }
        // A successful rollback can be discarded; an interrupted one keeps its journal.
        ArchiveFiles.deleteTree(dir)
    }

    private fun validateManifest(m: SaveManifest, expected: String) {
        require(m.version == 1 && m.titleId == expected && title(m.titleId) == m.titleId) { "Wrong game or unsupported backup version" }
        require(m.xuids.isNotEmpty() && m.xuids.size <= 32 && m.xuids.distinct().size == m.xuids.size)
        m.xuids.forEach { require(xuid(it) == it) }
        require(m.scopes.isNotEmpty() && m.scopes.size <= 128 && m.scopes.distinct().size == m.scopes.size)
        m.scopes.forEach { validateScope(it, expected, m.xuids) }
        require(m.files.size + m.directories.size <= limits.entries)
        val paths = m.files.map { it.path } + m.directories
        require(paths.map { it.lowercase() }.distinct().size == paths.size) { "Duplicate manifest path" }
        paths.forEach { path ->
            ArchiveFiles.relative(path)
            require(m.scopes.any { path == it || path.startsWith("$it/") }) { "Backup escaped declared save scope" }
        }
        require(m.files.all { it.bytes in 0..limits.entryBytes && it.sha256.matches(Regex("[0-9a-f]{64}")) })
        var total = 0L
        m.files.forEach { require(it.bytes <= limits.totalBytes - total); total += it.bytes }
        require(m.scopes.none { a -> m.scopes.any { b -> a != b && a.startsWith("$b/") } })
    }

    private fun validateScope(path: String, tid: String, ids: List<String>) {
        require(titlePattern.matches(tid) && tid != "00000000") { "Invalid Title ID in scope" }
        val parts = ArchiveFiles.relative(path).split('/')
        require(parts.first() in ids && xuidPattern.matches(parts.first()))
        val valid = (parts.size == 3 && parts[1] == tid && parts[2] == saveType) ||
            (parts.size == 4 && parts[1] == tid && parts[2] == "Headers" && parts[3] == saveType) ||
            (parts.size == 4 && parts[1] == dashboard && parts[2] == profileType && parts[3] == parts[0])
        require(valid) { "Backup contains non-save content" }
    }

    private fun isProfileScope(scope: String): Boolean = scope.split('/').let { it.size == 4 && it[1] == dashboard && it[2] == profileType }

    private fun regularFiles(root: File): List<File> {
        if (!root.exists()) return emptyList()
        require(!Files.isSymbolicLink(root.toPath())) { "Symbolic link in save tree" }
        val files = mutableListOf<File>()
        Files.walk(root.toPath()).use { walk -> walk.forEach {
            require(!Files.isSymbolicLink(it)) { "Symbolic link in save tree" }
            if (Files.isRegularFile(it)) files.add(it.toFile())
            else require(Files.isDirectory(it)) { "Unsupported file in save tree" }
            require(files.size <= limits.entries) { "Too many files" }
        } }
        return files
    }
}
