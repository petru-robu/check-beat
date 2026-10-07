package com.debloated.player.data.scan

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

data class ScannedTrack(val title: String, val fileUri: String, val durationMs: Long)

data class ScannedAlbum(val name: String, val folderUri: String, val tracks: List<ScannedTrack>)

/**
 * Walks a SAF document tree: every immediate sub-folder of the root is an album, and the
 * supported audio files directly inside it are its tracks.
 *
 * Failures to read a folder are thrown, never swallowed: an incomplete scan that looked
 * successful would make the repository delete albums (and their play counts).
 */
class LibraryScanner(private val context: Context) {

    suspend fun scan(treeUri: Uri): List<ScannedAlbum> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

        resolver.listChildren(treeUri, rootDocumentId)
            .filter { it.isDirectory }
            .sortedWith(compareBy(NaturalOrder) { it.name })
            .mapNotNull { folder -> scanAlbum(resolver, treeUri, folder) }
    }

    private suspend fun scanAlbum(resolver: ContentResolver, treeUri: Uri, folder: Child): ScannedAlbum? {
        val audioFiles = resolver.listChildren(treeUri, folder.documentId)
            .filter { !it.isDirectory && it.extension in SUPPORTED_EXTENSIONS }
            .sortedWith(compareBy(NaturalOrder) { it.name })

        // A folder without playable audio is not an album.
        if (audioFiles.isEmpty()) return null

        val tracks = audioFiles.map { file ->
            currentCoroutineContext().ensureActive()
            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.documentId)
            val (title, durationMs) = readMetadata(fileUri, fallbackTitle = file.name.substringBeforeLast('.'))
            ScannedTrack(title = title, fileUri = fileUri.toString(), durationMs = durationMs)
        }

        return ScannedAlbum(
            name = folder.name,
            folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, folder.documentId).toString(),
            tracks = tracks,
        )
    }

    private fun readMetadata(fileUri: Uri, fallbackTitle: String): Pair<String, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, fileUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: fallbackTitle
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            title to duration
        } catch (e: RuntimeException) {
            // Unreadable tags must not hide an otherwise playable file.
            fallbackTitle to 0L
        } finally {
            retriever.release()
        }
    }

    private fun ContentResolver.listChildren(treeUri: Uri, parentDocumentId: String): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = query(childrenUri, projection, null, null, null)
            ?: throw IOException("Unable to list $childrenUri")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val name = it.getString(1) ?: continue
                    add(Child(documentId = it.getString(0), name = name, mimeType = it.getString(2).orEmpty()))
                }
            }
        }
    }

    private class Child(val documentId: String, val name: String, val mimeType: String) {
        val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
        val extension: String get() = name.substringAfterLast('.', "").lowercase()
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "m4a")
    }
}

/** Case-insensitive ordering that compares digit runs numerically, so "2 - x" sorts before "10 - x". */
internal object NaturalOrder : Comparator<String> {
    private val chunks = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val left = chunks.findAll(a.lowercase()).map { it.value }.toList()
        val right = chunks.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val l = left[i]
            val r = right[i]
            val result = if (l[0].isDigit() && r[0].isDigit()) {
                val ln = l.trimStart('0')
                val rn = r.trimStart('0')
                if (ln.length != rn.length) ln.length - rn.length else ln.compareTo(rn)
            } else {
                l.compareTo(r)
            }
            if (result != 0) return result
        }
        return left.size - right.size
    }
}
