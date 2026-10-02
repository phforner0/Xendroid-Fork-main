package xendroid.compose

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import xendroid.compose.archive.ContentBusyException
import xendroid.compose.core.StorageAccess
import xendroid.compose.userdata.UserDataFiles

/**
 * L07: the emulator's user data in the system file manager ("Open user data", and any app the
 * user grants a folder to). A thin adapter: what may be seen and changed, and how, is
 * [UserDataFiles] (inside the root only, caches and bookkeeping hidden, changes under the
 * storage lease). Same class name and authority as the provider it replaces, so document ids
 * and grants already given keep working.
 */
class DocumentsProvider : android.provider.DocumentsProvider() {
    private val files by lazy { UserDataFiles(File(Utils.get_storage_root_path())) { StorageAccess.acquire() } }
    private val closeHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val root = files.root
        result.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT)
            add(Root.COLUMN_SUMMARY, null)
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_TITLE, context!!.getString(R.string.app_name))
            add(Root.COLUMN_DOCUMENT_ID, files.docIdFor(root))
            add(Root.COLUMN_MIME_TYPES, "*/*")
            add(Root.COLUMN_AVAILABLE_BYTES, root.freeSpace)
            add(Root.COLUMN_ICON, xendroid.compose.core.R.drawable.app_icon)
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).also { include(it, files.fileFor(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).also { cursor ->
            files.children(parentDocumentId).forEach { include(cursor, it) }
        }

    override fun querySearchDocuments(rootId: String, query: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).also { cursor ->
            files.search(query).forEach { include(cursor, it) }
        }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        files.isChild(parentDocumentId, documentId)

    override fun getDocumentType(documentId: String): String = typeOf(files.fileFor(documentId))

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val access = ParcelFileDescriptor.parseMode(mode)
        if (access == ParcelFileDescriptor.MODE_READ_ONLY) {
            val file = files.fileFor(documentId)
            if (file.isDirectory) throw FileNotFoundException("A folder cannot be opened")
            return ParcelFileDescriptor.open(file, access)
        }
        // Writing holds the storage lease until the caller closes the file.
        val (file, lease) = guarded { files.openForWriting(documentId) }
        return try {
            ParcelFileDescriptor.open(file, access, closeHandler) { lease.close() }
        } catch (e: Exception) {
            lease.close()
            throw e
        }
    }

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point?, signal: CancellationSignal?): AssetFileDescriptor {
        val file = files.fileFor(documentId)
        if (!typeOf(file).startsWith("image/")) throw FileNotFoundException("No thumbnail")
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0,
            AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String =
        guarded { files.create(parentDocumentId, displayName, directory = mimeType == Document.MIME_TYPE_DIR) }

    override fun renameDocument(documentId: String, displayName: String): String =
        guarded { files.rename(documentId, displayName) }

    override fun deleteDocument(documentId: String) = guarded { files.delete(documentId) }

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String =
        guarded { files.copy(sourceDocumentId, targetParentDocumentId) }

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String =
        guarded { files.move(sourceDocumentId, targetParentDocumentId) }

    /** The file UI shows FileNotFoundException messages; a running game holds the lease. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: ContentBusyException) {
        throw FileNotFoundException("Close the running game first: its data cannot change while it runs")
    } catch (e: FileNotFoundException) {
        throw e
    } catch (e: SecurityException) {
        throw e
    } catch (e: Exception) {
        throw FileNotFoundException(e.message ?: "The change failed")
    }

    private fun include(cursor: MatrixCursor, file: File) {
        val type = typeOf(file)
        var flags = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
            Document.FLAG_SUPPORTS_MOVE or Document.FLAG_SUPPORTS_COPY
        flags = flags or if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
        if (type.startsWith("image/")) flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        if (file == files.root) flags = Document.FLAG_DIR_SUPPORTS_CREATE
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, files.docIdFor(file))
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_SIZE, file.length())
            add(Document.COLUMN_MIME_TYPE, type)
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_ICON, xendroid.compose.core.R.drawable.app_icon)
        }
    }

    private fun typeOf(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        val extension = file.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private companion object {
        const val ROOT = "root"
        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_MIME_TYPES, Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_AVAILABLE_BYTES,
        )
        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
        )
    }
}
