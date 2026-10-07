package com.debloated.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.debloated.player.data.PlayRules
import com.debloated.player.data.Track

/** Tracklist of one album: tap a track to play it, "Played it!" counts a finished pass. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: Long,
    viewModel: LibraryViewModel,
    snackbarHostState: SnackbarHostState,
) {
    // A fresh flow per album, so a previously opened album never flashes while this one loads.
    val detail by remember(albumId) { viewModel.albumDetail(albumId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.album?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeAlbum) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                detail?.let { current ->
                    PlayedItBar(
                        enabled = current.canComplete,
                        // The player bar already clears the navigation bar when it is showing.
                        clearNavigationBar = nowPlaying == null,
                        onClick = { viewModel.onAlbumPlayed(current.album) },
                    )
                }
                nowPlaying?.let { current ->
                    NowPlayingBar(
                        nowPlaying = current,
                        position = { position },
                        onClick = viewModel::expandPlayer,
                        onSeek = viewModel::seekTo,
                        onSkipPrevious = viewModel::skipToPrevious,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSkipNext = viewModel::skipToNext,
                    )
                }
            }
        },
    ) { padding ->
        val current = detail ?: return@Scaffold
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item(key = "header", contentType = "header") {
                AlbumHeader(
                    playCount = current.album.playCount,
                    playedTracks = current.playedCount,
                    totalTracks = current.tracks.size,
                )
            }
            itemsIndexed(current.tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                val isCurrent = nowPlaying?.let { it.albumId == track.albumId && it.trackUri == track.fileUri } == true
                TrackRow(
                    number = index + 1,
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && nowPlaying?.isPlaying == true,
                    onClick = { viewModel.onTrackClick(current, index) },
                )
            }
        }
    }
}

@Composable
private fun AlbumHeader(playCount: Int, playedTracks: Int, totalTracks: Int) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)) {
        PlayProgress(playCount = playCount, locked = false)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "$playedTracks / $totalTracks tracks played",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (totalTracks == 0) 0f else playedTracks.toFloat() / totalTracks },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "A track counts once you've listened to ${PlayRules.MIN_LISTENED_PERCENT}% of it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackRow(
    number: Int,
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            when {
                isCurrent -> Icon(
                    imageVector = if (isPlaying) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.Pause,
                    contentDescription = if (isPlaying) "Playing" else "Paused",
                    modifier = Modifier.size(20.dp),
                )
                else -> Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Text(
            text = track.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (track.duration > 0) {
            Text(
                text = formatDuration(track.duration),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (track.played) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (track.played) "Played" else "Not played yet",
            modifier = Modifier
                .padding(start = 12.dp)
                .size(22.dp),
            tint = if (track.played) colors.onSurface else colors.outlineVariant,
        )
    }
}

/** Greyed out until every track has been played to its end, then a normal button. */
@Composable
private fun PlayedItBar(enabled: Boolean, clearNavigationBar: Boolean, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (clearNavigationBar) Modifier.navigationBarsPadding() else Modifier)
                .padding(16.dp),
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = "Played it!",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
