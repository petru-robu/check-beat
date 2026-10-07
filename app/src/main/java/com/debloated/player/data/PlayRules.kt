package com.debloated.player.data

object PlayRules {
    /** An album is "completed" (and locked) once it has been played through this many times. */
    const val MAX_PLAYS = 2

    /** A track counts as played once this share of its length has actually been heard. */
    const val MIN_LISTENED_PERCENT = 90
}

val Album.isLocked: Boolean
    get() = playCount >= PlayRules.MAX_PLAYS
