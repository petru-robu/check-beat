package com.debloated.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.debloated.player.data.Album
import com.debloated.player.data.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Snapshot of what the player bar needs. */
data class NowPlaying(
    val albumId: Long?,
    /** The current track's file, to tell which row of an album is the one playing. */
    val trackUri: String?,
    val title: String,
    val albumName: String,
    val isPlaying: Boolean,
    val hasNext: Boolean,
)

/**
 * Where playback is inside the current track. Kept apart from [NowPlaying] because it changes
 * several times a second and only the seek bar needs it.
 */
data class PlaybackPosition(val positionMs: Long = 0L, val durationMs: Long = 0L)

/**
 * UI-side handle to the [PlaybackService]: connects a [MediaController], mirrors the player
 * into [nowPlaying] and forwards user actions.
 */
class PlaybackConnection(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerReady = CompletableDeferred<MediaController>()
    private var controller: MediaController? = null

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _position = MutableStateFlow(PlaybackPosition())
    val position: StateFlow<PlaybackPosition> = _position.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    private val controllerFuture: ListenableFuture<MediaController> = run {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        MediaController.Builder(appContext, token).buildAsync()
    }

    init {
        controllerFuture.addListener(
            {
                try {
                    val connected = controllerFuture.get()
                    controller = connected
                    connected.addListener(listener)
                    // The service may already be playing (UI was closed and reopened).
                    publish(connected)
                    controllerReady.complete(connected)
                } catch (e: Exception) {
                    controllerReady.completeExceptionally(e)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )

        // The player emits no events while a track advances, so poll to move the seek bar.
        scope.launch {
            while (isActive) {
                delay(PROGRESS_POLL_MS)
                controller?.takeIf { it.isPlaying }?.let { publishPosition(it) }
            }
        }
    }

    /** Replaces the queue with [tracks] (in order) and starts playing from [startIndex]. */
    suspend fun playAlbum(album: Album, tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val player = controllerReady.await()
        player.setMediaItems(
            tracks.map { it.toMediaItem(album.name) },
            startIndex.coerceIn(0, tracks.lastIndex),
            /* startPositionMs = */ 0L,
        )
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isShowingPause()) {
            player.pause()
        } else {
            when (player.playbackState) {
                Player.STATE_IDLE -> player.prepare()
                // Normally the service has already rewound a finished queue; this is the fallback.
                Player.STATE_ENDED -> player.seekToDefaultPosition(0)
            }
            player.play()
        }
    }

    /** Like any player: restarts the track if it is a few seconds in, otherwise goes to the previous one. */
    fun skipToPrevious() {
        val player = controller ?: return
        player.seekToPrevious()
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }

    fun skipToNext() {
        val player = controller ?: return
        if (!player.hasNextMediaItem()) return
        player.seekToNextMediaItem()
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }

    /** Jumps to [positionMs] in the current track. */
    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        player.seekTo(positionMs.coerceAtLeast(0L))
        publishPosition(player)
    }

    /** Stops playback and empties the queue if it holds a track of [albumId] (used when it gets locked). */
    fun stopIfPlaying(albumId: Long) {
        val player = controller ?: return
        if (player.currentMediaItem?.albumId != albumId) return
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        scope.cancel()
        controller?.removeListener(listener)
        controller = null
        MediaController.releaseFuture(controllerFuture)
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        _nowPlaying.value = if (item == null) {
            null
        } else {
            NowPlaying(
                albumId = item.albumId,
                trackUri = item.fileUri,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                albumName = item.mediaMetadata.albumTitle?.toString().orEmpty(),
                isPlaying = player.isShowingPause(),
                hasNext = player.hasNextMediaItem(),
            )
        }
        publishPosition(player)
    }

    private fun publishPosition(player: Player) {
        val item = player.currentMediaItem
        _position.value = if (item == null) {
            PlaybackPosition()
        } else {
            // The player only knows the length once the file is prepared; until then use the scanned one.
            val duration = player.duration.takeIf { it > 0 } ?: item.mediaMetadata.durationMs ?: 0L
            PlaybackPosition(
                positionMs = player.currentPosition.coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE),
                durationMs = duration,
            )
        }
    }

    /** True while playback is running or about to run (buffering counts, so the icon doesn't flicker). */
    private fun Player.isShowingPause(): Boolean =
        playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)

    private companion object {
        const val PROGRESS_POLL_MS = 250L
    }
}
