package com.debloated.player

import android.app.Application
import com.debloated.player.data.AppDatabase
import com.debloated.player.data.MusicRepository

/**
 * Manual dependency container. Shared by the UI process parts (ViewModel) and the
 * PlaybackService, which must be able to record play counts while the UI is gone.
 */
class DebloatedPlayerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: MusicRepository by lazy { MusicRepository(this, database) }
}
