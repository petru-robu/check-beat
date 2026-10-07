package com.debloated.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import com.debloated.player.data.scan.LibraryScanner
import com.debloated.player.data.scan.ScannedAlbum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicRepository(
    context: Context,
    private val database: AppDatabase,
    private val scanner: LibraryScanner = LibraryScanner(context),
) {
    private val appContext = context.applicationContext
    private val dao = database.audioDao()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _libraryRoot = MutableStateFlow(prefs.getString(KEY_ROOT_URI, null)?.let(Uri::parse))

    /** The SAF tree the user picked as their music library, or null before they pick one. */
    val libraryRoot: StateFlow<Uri?> = _libraryRoot.asStateFlow()

    /** All albums, already sorted: 0 plays first, then 1, then completed ones. */
    val albums: Flow<List<Album>> = dao.getAllAlbums()

    fun album(albumId: Long): Flow<Album?> = dao.observeAlbum(albumId)

    fun tracksFor(albumId: Long): Flow<List<Track>> = dao.getTracksForAlbum(albumId)

    /**
     * Makes [treeUri] the library root and scans it. On failure the previous library is left
     * untouched (including its stored folder permission).
     */
    suspend fun setLibraryRoot(treeUri: Uri) {
        val previous = _libraryRoot.value
        appContext.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            syncLibrary(scanner.scan(treeUri))
        } catch (e: Exception) {
            if (treeUri != previous) releasePermission(treeUri)
            throw e
        }
        prefs.edit().putString(KEY_ROOT_URI, treeUri.toString()).apply()
        _libraryRoot.value = treeUri
        if (previous != null && previous != treeUri) releasePermission(previous)
    }

    /** Re-reads the current library root, keeping the play counts of albums that still exist. */
    suspend fun rescan() {
        val root = _libraryRoot.value ?: return
        syncLibrary(scanner.scan(root))
    }

    /** Called when a track has played to its end. */
    suspend fun markTrackPlayed(albumId: Long, fileUri: String) = dao.markTrackPlayed(albumId, fileUri)

    /**
     * The user's "Played it!": counts one full pass of [albumId] and starts the next pass with
     * every track unplayed. Returns the updated album, or null if the album is gone, already
     * locked, or not every track has been played to its end yet (nothing is changed then).
     */
    suspend fun completeAlbumPass(albumId: Long): Album? = database.withTransaction {
        val album = dao.getAlbum(albumId) ?: return@withTransaction null
        if (album.isLocked || dao.countTracks(albumId) == 0 || dao.countUnplayedTracks(albumId) > 0) {
            return@withTransaction null
        }
        dao.incrementPlayCount(albumId, PlayRules.MAX_PLAYS)
        dao.resetTracksPlayed(albumId)
        dao.getAlbum(albumId)
    }

    /**
     * Albums are matched by folder URI: existing ones keep their id and playCount (only their
     * tracks are refreshed, keeping which files were already played), new folders are added, and
     * folders that vanished are removed.
     */
    private suspend fun syncLibrary(scanned: List<ScannedAlbum>) {
        database.withTransaction {
            val existing = dao.getAlbums().associateBy { it.folderUri }
            val scannedUris = scanned.mapTo(HashSet()) { it.folderUri }

            val staleIds = existing.values.filter { it.folderUri !in scannedUris }.map { it.id }
            if (staleIds.isNotEmpty()) dao.deleteAlbums(staleIds)

            for (album in scanned) {
                val albumId = existing[album.folderUri]?.id
                    ?: dao.insertAlbum(Album(folderUri = album.folderUri, name = album.name))
                val played = dao.getPlayedTrackUris(albumId).toHashSet()
                dao.deleteTracksForAlbum(albumId)
                dao.insertTracks(
                    album.tracks.map {
                        Track(
                            albumId = albumId,
                            title = it.title,
                            fileUri = it.fileUri,
                            duration = it.durationMs,
                            played = it.fileUri in played,
                        )
                    },
                )
            }
        }
    }

    private fun releasePermission(treeUri: Uri) {
        try {
            appContext.contentResolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Permission was already gone; nothing to release.
        }
    }

    private companion object {
        const val PREFS_NAME = "library"
        const val KEY_ROOT_URI = "root_uri"
    }
}
