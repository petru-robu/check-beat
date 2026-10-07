package com.debloated.player.playback

import com.debloated.player.data.PlayRules
import java.util.BitSet

/**
 * Decides when a track has really been listened to: enough distinct seconds of it were played.
 * Seeking over a part does not count that part, and replaying a part does not count it twice, so
 * the only way to get there is to listen.
 *
 * Only one track is followed at a time; switching to another track starts it from scratch.
 * Not thread-safe (the playback service calls it from the main thread).
 */
class ListenTracker(private val minPercent: Int = PlayRules.MIN_LISTENED_PERCENT) {

    private var key: String? = null
    private val heard = BitSet()
    private var totalSeconds = 0
    private var required = 0
    private var done = false

    /**
     * Report that second [positionMs] of the track [key] (which is [durationMs] long) is playing
     * right now; call this repeatedly while it plays. Returns true exactly once per listen: on
     * the call that brings the track to the required share.
     */
    fun onPlaying(key: String, positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        if (key != this.key) {
            reset()
            this.key = key
            totalSeconds = ((durationMs + 999) / 1000).toInt().coerceAtLeast(1)
            required = ((totalSeconds * minPercent + 99) / 100).coerceIn(1, totalSeconds)
        }
        heard.set((positionMs / 1000).toInt().coerceIn(0, totalSeconds - 1))
        if (!done && heard.cardinality() >= required) {
            done = true
            return true
        }
        return false
    }

    /** Forget everything, so the same track can be listened to (and counted) again. */
    fun reset() {
        key = null
        heard.clear()
        totalSeconds = 0
        required = 0
        done = false
    }
}
