package com.debloated.player.playback

import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.debloated.player.data.Track

private const val EXTRA_ALBUM_ID = "com.debloated.player.extra.ALBUM_ID"

/**
 * Builds the queue entry for a track. The album id travels with the item (in the metadata
 * extras) so the service knows which album to credit when a track finishes, even after
 * the UI that started playback is gone.
 */
fun Track.toMediaItem(albumName: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(fileUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setAlbumTitle(albumName)
                // Tags are not stored, so the album name doubles as the notification subtitle.
                .setArtist(albumName)
                .setDurationMs(duration.takeIf { it > 0 })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(bundleOf(EXTRA_ALBUM_ID to albumId))
                .build(),
        )
        .build()

val MediaItem.albumId: Long?
    get() = mediaMetadata.extras
        ?.takeIf { it.containsKey(EXTRA_ALBUM_ID) }
        ?.getLong(EXTRA_ALBUM_ID)

/** The track's file, which (unlike its row id) survives a library rescan. */
val MediaItem.fileUri: String?
    get() = localConfiguration?.uri?.toString()
