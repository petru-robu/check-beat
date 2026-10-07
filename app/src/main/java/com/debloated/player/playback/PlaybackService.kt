package com.debloated.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.debloated.player.DebloatedPlayerApp
import com.debloated.player.MainActivity
import com.debloated.player.data.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer so playback survives the UI, and is the single place where a track is
 * marked as played: only once enough of it has actually been heard (see [ListenTracker]), so
 * skipping, or seeking to the end, never counts.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listenTracker = ListenTracker()
    private var listenJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val repository = (application as DebloatedPlayerApp).repository

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Sample the position while (and only while) audio is actually playing.
                listenJob?.cancel()
                listenJob = if (isPlaying) {
                    serviceScope.launch {
                        while (isActive) {
                            trackListening(player, repository)
                            delay(LISTEN_TICK_MS)
                        }
                    }
                } else {
                    null
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // A new queue (e.g. the album was started again) is a fresh listen, even of the same file.
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) listenTracker.reset()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // STATE_ENDED is only reached when the last item finishes (repeat is off) or the
                // queue is cleared; skipping past the last track is not possible.
                if (playbackState == Player.STATE_ENDED) onQueueFinished(player)
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun onQueueFinished(player: Player) {
        if (player.currentMediaItem == null) return
        // Park the queue paused at track 1, so a later "play" from the notification or a headset
        // restarts the album instead of replaying only the last track. It is a new listen.
        listenTracker.reset()
        player.pause()
        player.seekToDefaultPosition(0)
    }

    private fun trackListening(player: Player, repository: MusicRepository) {
        val item = player.currentMediaItem ?: return
        val albumId = item.albumId ?: return
        val fileUri = item.fileUri ?: return
        // The player only knows the length once prepared; until then use the scanned one.
        val duration = player.duration.takeIf { it > 0 } ?: item.mediaMetadata.durationMs ?: return
        if (listenTracker.onPlaying(fileUri, player.currentPosition, duration)) {
            serviceScope.launch { repository.markTrackPlayed(albumId, fileUri) }
        }
    }

    private companion object {
        /** Fine enough that no second of continuous playback is missed. */
        const val LISTEN_TICK_MS = 250L
    }
}
