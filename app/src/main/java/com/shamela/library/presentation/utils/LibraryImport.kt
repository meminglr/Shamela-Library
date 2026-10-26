package com.shamela.library.presentation.utils

import com.shamela.library.data.local.files.StorageDirs
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File

/**
 * Copies books from a folder the user picks (typically the previous Shamela app's
 * `Android/media/com.shamela.library/ShamelaDownloads`) into this app's library folder. The old
 * app's folder is deleted when it is uninstalled, and another app's folder can't be updated in
 * place, so the books are copied while it is still installed.
 *
 * The library is `ShamelaDownloads/<category>/<title>.epub`: each book keeps the name of the folder
 * it was found in as its category, so the same books get the same ids (and favorites) as before.
 */
object LibraryImport {
    private const val TAG = "LibraryImport"
    private const val MAX_DEPTH = 4

    /** Where the previous app kept its books; offered as the picker's starting folder. */
    val OLD_APP_FOLDER: Uri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:Android/media/com.shamela.library/ShamelaDownloads"
    )

    data class FoundBook(val category: String, val fileName: String, val uri: Uri, val size: Long)

    private data class Child(val documentId: String, val name: String, val isDirectory: Boolean, val size: Long)

    /** Every `.epub` under [treeUri], with the name of the folder that holds it. */
    fun findBooks(resolver: ContentResolver, treeUri: Uri): List<FoundBook> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = rootId.substringAfterLast(':').substringAfterLast('/').ifBlank { "ShamelaDownloads" }
        val found = mutableListOf<FoundBook>()
        fun walk(documentId: String, folderName: String, depth: Int) {
            listChildren(resolver, treeUri, documentId).forEach { child ->
                when {
                    child.isDirectory && depth < MAX_DEPTH -> walk(child.documentId, child.name, depth + 1)
                    !child.isDirectory && child.name.endsWith(".epub", ignoreCase = true) -> found += FoundBook(
                        category = folderName,
                        fileName = child.name,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId),
                        size = child.size,
                    )
                }
            }
        }
        walk(rootId, rootName, 0)
        return found
    }

    private fun listChildren(resolver: ContentResolver, treeUri: Uri, documentId: String): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        return try {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Child(
                                documentId = cursor.getString(0),
                                name = cursor.getString(1).orEmpty(),
                                isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                                size = if (cursor.isNull(3)) -1L else cursor.getLong(3),
                            )
                        )
                    }
                }
            }.orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "listChildren: ${e.message}")
            emptyList()
        }
    }

    /**
     * Copies [book] into [libraryRoot]. Returns the file, or null when it failed. A book that is
     * already there with the same size is not copied again ([copied] = false).
     */
    fun copy(resolver: ContentResolver, book: FoundBook, libraryRoot: File): Result? {
        val destination = File(File(libraryRoot, book.category), book.fileName)
        if (destination.isFile && (book.size < 0 || destination.length() == book.size)) {
            return Result(destination, copied = false)
        }
        // Copy outside the library first: the library lists every file in a category folder.
        val partial = File(File(libraryRoot.parentFile, "import-tmp").also { StorageDirs.ensure(it) }, "${book.fileName}.part")
        return try {
            if (!StorageDirs.ensure(destination.parentFile!!)) return null
            resolver.openInputStream(book.uri)?.use { input ->
                partial.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return null
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) return null
            Result(destination, copied = true)
        } catch (e: Exception) {
            Log.e(TAG, "copy ${book.fileName}: ${e.message}")
            partial.delete()
            null
        }
    }

    data class Result(val file: File, val copied: Boolean)
}
