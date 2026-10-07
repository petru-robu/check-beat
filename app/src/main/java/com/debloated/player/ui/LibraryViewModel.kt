package com.debloated.player.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.debloated.player.DebloatedPlayerApp
import com.debloated.player.data.Album
import com.debloated.player.data.MusicRepository
import com.debloated.player.data.PlayRules
import com.debloated.player.data.Track
import com.debloated.player.data.isLocked
import com.debloated.player.playback.NowPlaying
import com.debloated.player.playback.PlaybackConnection
import com.debloated.player.playback.PlaybackPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    /** Already sorted by play count (0, 1, 2), then by name. */
    val albums: List<Album> = emptyList(),
    val hasLibrary: Boolean = false,
    val isScanning: Boolean = false,
    /** True until the first database emission, so the empty state doesn't flash on launch. */
    val isLoading: Boolean = true,
)

/** An album with its tracks in play order, as shown on the tracklist screen. */
data class AlbumDetail(val album: Album, val tracks: List<Track>) {
    val playedCount: Int get() = tracks.count { it.played }

    /** "Played it!" unlocks once every track has played to its end during this pass. */
    val canComplete: Boolean get() = !album.isLocked && tracks.isNotEmpty() && playedCount == tracks.size
}

class LibraryViewModel(
    private val repository: MusicRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {

    private val isScanning = MutableStateFlow(false)
    private val _openAlbumId = MutableStateFlow<Long?>(null)
    private val _playerExpanded = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.BUFFERED)

    /** One-off messages (errors) for the snackbar. */
    val events: Flow<String> = messages.receiveAsFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.albums,
        repository.libraryRoot,
        isScanning,
    ) { albums, root, scanning ->
        LibraryUiState(albums = albums, hasLibrary = root != null, isScanning = scanning, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val nowPlaying: StateFlow<NowPlaying?> = playback.nowPlaying

    val position: StateFlow<PlaybackPosition> = playback.position

    /** True while the full-screen player is open on top of the other screens. */
    val playerExpanded: StateFlow<Boolean> = _playerExpanded.asStateFlow()

    fun expandPlayer() {
        if (nowPlaying.value != null) _playerExpanded.value = true
    }

    fun collapsePlayer() {
        _playerExpanded.value = false
    }

    /** The album whose tracklist is on screen, or null while the library is shown. */
    val openAlbumId: StateFlow<Long?> = _openAlbumId.asStateFlow()

    /** Live album + tracks. A missing album (e.g. its folder was deleted and rescanned) closes the screen. */
    fun albumDetail(albumId: Long): Flow<AlbumDetail?> =
        combine(repository.album(albumId), repository.tracksFor(albumId)) { album, tracks ->
            album?.let { AlbumDetail(it, tracks) }
        }.onEach { if (it == null) closeAlbum() }

    fun openAlbum(album: Album) {
        // Completed albums are locked; the UI disables them too, this is the backstop.
        if (album.isLocked) return
        _openAlbumId.value = album.id
    }

    fun closeAlbum() {
        _openAlbumId.value = null
    }

    fun onFolderPicked(treeUri: Uri?) {
        if (treeUri == null) return
        scan { repository.setLibraryRoot(treeUri) }
    }

    fun rescan() = scan { repository.rescan() }

    /** Plays [album] from its first track. */
    fun playAlbum(album: Album) {
        if (album.isLocked) return
        viewModelScope.launch {
            try {
                val tracks = repository.tracksFor(album.id).first()
                if (tracks.isEmpty()) {
                    messages.send("\"${album.name}\" has no playable tracks. Try rescanning.")
                    return@launch
                }
                playback.playAlbum(album, tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.send("Couldn't start playback.")
            }
        }
    }

    /** Plays the track at [index] of [detail], or pauses/resumes it if it is already the current one. */
    fun onTrackClick(detail: AlbumDetail, index: Int) {
        val track = detail.tracks.getOrNull(index) ?: return
        if (detail.album.isLocked) return
        val current = nowPlaying.value
        if (current?.albumId == detail.album.id && current.trackUri == track.fileUri) {
            playback.togglePlayPause()
            return
        }
        viewModelScope.launch {
            try {
                playback.playAlbum(detail.album, detail.tracks, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.send("Couldn't start playback.")
            }
        }
    }

    /** The "Played it!" button: counts a full pass, starts the next one, and locks the album after the last. */
    fun onAlbumPlayed(album: Album) {
        viewModelScope.launch {
            val updated = repository.completeAlbumPass(album.id)
            when {
                updated == null -> messages.send("Play every track to the end first.")
                updated.isLocked -> {
                    // A locked album must not stay replayable from the player bar either.
                    playback.stopIfPlaying(album.id)
                    closeAlbum()
                    messages.send("\"${album.name}\" is complete.")
                }
                else -> messages.send("Played ${updated.playCount} of ${PlayRules.MAX_PLAYS}. Starting over.")
            }
        }
    }

    fun togglePlayPause() = playback.togglePlayPause()

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun skipToPrevious() = playback.skipToPrevious()

    fun skipToNext() = playback.skipToNext()

    private fun scan(block: suspend () -> Unit) {
        if (isScanning.value) return
        isScanning.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                messages.send("Lost access to the music folder. Please select it again.")
            } catch (e: Exception) {
                messages.send("Scan failed: ${e.message ?: e::class.java.simpleName}")
            } finally {
                isScanning.value = false
            }
        }
    }

    override fun onCleared() {
        playback.release()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DebloatedPlayerApp
                LibraryViewModel(app.repository, PlaybackConnection(app))
            }
        }
    }
}
