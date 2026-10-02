package xendroid.compose.userdata

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import xendroid.compose.archive.ArchiveFiles

/**
 * L07: what the system file manager may see and change of the emulator's user data (config,
 * per-game config, patches, content: saves, profiles, DLC; logs), behind the app's
 * DocumentsProvider. Plain files, so every rule is tested on the JVM:
 *
 *  - a document id is the absolute path (as before: grants already given keep working), and it
 *    is accepted only when, with "." / ".." and links resolved, it is [base] or inside it;
 *  - emulator caches (shaders derived from games, regenerable) and the app's own bookkeeping
 *    (trash, locks) are neither listed nor opened;
 *  - names are single, plain path segments; an existing name gets " (n)", nothing is replaced;
 *  - every change runs under the storage lease ([lease]), so nothing changes under a running
 *    game or a save/content job; reading needs no lease.
 */
class UserDataFiles(base: File, private val lease: () -> AutoCloseable) {
    private val rootPath: Path = base.absoluteFile.toPath().normalize()
    /** Resolved on each use: where the root really is (links included) right now. */
    private val rootCanonical: Path get() = rootPath.toFile().canonicalFile.toPath()

    val root: File get() = rootPath.toFile()

    fun docIdFor(file: File): String = file.absolutePath

    /** The file of [docId] if it may be shared and exists; FileNotFoundException otherwise. */
    fun fileFor(docId: String): File {
        val path = resolve(docId) ?: throw FileNotFoundException("Not part of the shared user data")
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw FileNotFoundException("$docId not found")
        return path.toFile()
    }

    /** True when [childId] is strictly inside [parentId], both shareable. */
    fun isChild(parentId: String, childId: String): Boolean {
        val parent = resolve(parentId) ?: return false
        val child = resolve(childId) ?: return false
        return child != parent && child.startsWith(parent)
    }

    fun isVisible(file: File): Boolean {
        val path = file.absoluteFile.toPath().normalize()
        if (!path.startsWith(rootPath)) return false
        val parts = rootPath.relativize(path).map { it.toString() }.filter { it.isNotEmpty() }
        if (parts.firstOrNull() in HIDDEN_TOP_LEVEL) return false
        return parts.none { it.startsWith(PRIVATE_PREFIX) || it in HIDDEN_ANYWHERE }
    }

    fun children(parentId: String): List<File> {
        val parent = fileFor(parentId)
        if (!parent.isDirectory) throw FileNotFoundException("Not a folder")
        return parent.listFiles().orEmpty().filter(::isVisible).sortedBy { it.name.lowercase() }
    }

    /** Names containing [query] (ignoring case), breadth first, at most [limit] results after
     *  looking at [maxVisited] entries. */
    fun search(query: String, limit: Int = 20, maxVisited: Int = 10_000): List<File> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val found = ArrayList<File>()
        val pending = ArrayDeque<File>().apply { add(root) }
        var visited = 0
        while (pending.isNotEmpty() && found.size < limit && visited < maxVisited) {
            val dir = pending.removeFirst()
            for (child in dir.listFiles().orEmpty()) {
                if (++visited > maxVisited || found.size >= limit) break
                if (!isVisible(child) || Files.isSymbolicLink(child.toPath())) continue
                if (child.name.lowercase().contains(needle)) found += child
                if (child.isDirectory) pending.add(child)
            }
        }
        return found
    }

    fun create(parentId: String, name: String, directory: Boolean): String = changing {
        val parent = fileFor(parentId)
        if (!parent.isDirectory) throw FileNotFoundException("Not a folder")
        val file = uniqueChild(parent, name)
        val made = if (directory) file.mkdir() else file.createNewFile()
        if (!made) throw FileNotFoundException("Could not create $name")
        docIdFor(file)
    }

    fun rename(docId: String, name: String): String = changing {
        val file = fileFor(docId)
        if (file.toPath() == rootPath) throw FileNotFoundException("The root cannot be renamed")
        val target = File(file.parentFile, cleanName(file.parentFile!!, name))
        if (target.exists()) throw FileNotFoundException("\"${target.name}\" already exists")
        Files.move(file.toPath(), target.toPath())
        docIdFor(target)
    }

    fun delete(docId: String) = changing {
        val file = fileFor(docId)
        if (file.toPath() == rootPath) throw FileNotFoundException("The root cannot be deleted")
        ArchiveFiles.deleteTree(file)
    }

    fun copy(docId: String, targetParentId: String): String = changing {
        val source = fileFor(docId)
        val parent = fileFor(targetParentId)
        if (!parent.isDirectory) throw FileNotFoundException("Not a folder")
        if (parent.toPath().startsWith(source.toPath())) throw FileNotFoundException("A folder cannot go inside itself")
        val target = uniqueChild(parent, source.name)
        copyTree(source, target)
        docIdFor(target)
    }

    fun move(docId: String, targetParentId: String): String = changing {
        val source = fileFor(docId)
        val parent = fileFor(targetParentId)
        if (source.toPath() == rootPath) throw FileNotFoundException("The root cannot be moved")
        if (!parent.isDirectory) throw FileNotFoundException("Not a folder")
        if (parent.toPath().startsWith(source.toPath())) throw FileNotFoundException("A folder cannot go inside itself")
        val target = uniqueChild(parent, source.name)
        Files.move(source.toPath(), target.toPath())
        docIdFor(target)
    }

    /** For an open in a writing mode: the file and the lease to release when it is closed. */
    fun openForWriting(docId: String): Pair<File, AutoCloseable> {
        val held = lease()
        try {
            val file = fileFor(docId)
            if (file.isDirectory) throw FileNotFoundException("A folder cannot be opened")
            return file to held
        } catch (e: Exception) {
            held.close()
            throw e
        }
    }

    /** A free name in [parent] for [name]: "save.bin", then "save (1).bin", "save (2).bin"... */
    fun uniqueChild(parent: File, name: String): File {
        val clean = cleanName(parent, name)
        val first = File(parent, clean)
        if (!Files.exists(first.toPath(), LinkOption.NOFOLLOW_LINKS)) return first
        val dot = clean.lastIndexOf('.').takeIf { it > 0 } ?: clean.length
        val stem = clean.substring(0, dot)
        val extension = clean.substring(dot)
        for (n in 1..999) {
            val candidate = File(parent, "$stem ($n)$extension")
            if (!Files.exists(candidate.toPath(), LinkOption.NOFOLLOW_LINKS)) return candidate
        }
        throw FileNotFoundException("Too many files called $clean")
    }

    /** A plain, shareable name in [parent]; FileNotFoundException for anything else. */
    private fun cleanName(parent: File, name: String): String {
        val clean = name.trim()
        if (clean.isEmpty() || clean == "." || clean == ".." || clean.contains('/') || clean.contains('\u0000') ||
            clean.toByteArray().size > 255) throw FileNotFoundException("Invalid name")
        if (!isVisible(File(parent, clean))) throw FileNotFoundException("\"$clean\" is reserved")
        return clean
    }

    private fun resolve(docId: String): Path? {
        val file = File(docId)
        if (!file.isAbsolute) return null
        val path = file.toPath().normalize()
        // Links and "..": the real place must be the root or inside it.
        val real = runCatching { path.toFile().canonicalFile.toPath() }.getOrNull() ?: return null
        val canonicalRoot = runCatching { rootCanonical }.getOrNull() ?: return null
        if (real != canonicalRoot && !real.startsWith(canonicalRoot)) return null
        if (!path.startsWith(rootPath)) return null
        if (!isVisible(path.toFile())) return null
        return path
    }

    private fun copyTree(source: File, target: File) {
        if (Files.isSymbolicLink(source.toPath())) return   // never followed out of the root
        if (source.isDirectory) {
            if (!target.mkdir()) throw FileNotFoundException("Could not create ${target.name}")
            for (child in source.listFiles().orEmpty()) {
                if (isVisible(child)) copyTree(child, File(target, child.name))
            }
        } else {
            Files.copy(source.toPath(), target.toPath())
        }
    }

    private fun <T> changing(block: () -> T): T = lease().use { block() }

    companion object {
        /** Emulator caches (pipeline/shader data derived from games; regenerable). */
        val HIDDEN_TOP_LEVEL = setOf("cache", "cache0", "cache1")
        /** The app's own bookkeeping (trash, journals) anywhere in the tree. */
        const val PRIVATE_PREFIX = ".xendroid-"
        val HIDDEN_ANYWHERE = setOf(".content-session.lock", ".lock")
    }
}
