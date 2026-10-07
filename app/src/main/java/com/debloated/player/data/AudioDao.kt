package com.debloated.player.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDao {

    @Insert
    suspend fun insertAlbum(album: Album): Long

    @Insert
    suspend fun insertAlbums(albums: List<Album>): List<Long>

    @Insert
    suspend fun insertTracks(tracks: List<Track>)

    /** Sorted for the library screen: unplayed first, then played once, then completed. */
    @Query("SELECT * FROM albums ORDER BY playCount ASC, name COLLATE NOCASE ASC")
    fun getAllAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums")
    suspend fun getAlbums(): List<Album>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getAlbum(albumId: Long): Album?

    /** Emits null when the album does not exist (any more). */
    @Query("SELECT * FROM albums WHERE id = :albumId")
    fun observeAlbum(albumId: Long): Flow<Album?>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY id ASC")
    fun getTracksForAlbum(albumId: Long): Flow<List<Track>>

    @Query("SELECT fileUri FROM tracks WHERE albumId = :albumId AND played = 1")
    suspend fun getPlayedTrackUris(albumId: Long): List<String>

    /** Matched by file, not row id: a rescan re-creates the track rows while the queue keeps playing. */
    @Query("UPDATE tracks SET played = 1 WHERE albumId = :albumId AND fileUri = :fileUri")
    suspend fun markTrackPlayed(albumId: Long, fileUri: String)

    @Query("UPDATE tracks SET played = 0 WHERE albumId = :albumId")
    suspend fun resetTracksPlayed(albumId: Long)

    @Query("SELECT COUNT(*) FROM tracks WHERE albumId = :albumId")
    suspend fun countTracks(albumId: Long): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE albumId = :albumId AND played = 0")
    suspend fun countUnplayedTracks(albumId: Long): Int

    @Query("UPDATE albums SET playCount = :playCount WHERE id = :albumId")
    suspend fun updatePlayCount(albumId: Long, playCount: Int)

    /** Atomic +1 that never goes past [maxPlays]. */
    @Query("UPDATE albums SET playCount = MIN(playCount + 1, :maxPlays) WHERE id = :albumId")
    suspend fun incrementPlayCount(albumId: Long, maxPlays: Int)

    /** Tracks are removed too (ON DELETE CASCADE). */
    @Query("DELETE FROM albums WHERE id IN (:albumIds)")
    suspend fun deleteAlbums(albumIds: List<Long>)

    @Query("DELETE FROM tracks WHERE albumId = :albumId")
    suspend fun deleteTracksForAlbum(albumId: Long)
}
