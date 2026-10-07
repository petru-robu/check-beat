package com.debloated.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.debloated.player.R
import com.debloated.player.data.Album

/** The list of albums. Scroll state is hoisted so it survives a visit to an album's tracklist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    snackbarHostState: SnackbarHostState,
    scrollBehavior: TopAppBarScrollBehavior,
    listState: LazyListState,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()

    // SAF folder picker (ACTION_OPEN_DOCUMENT_TREE).
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onFolderPicked(uri)
    }
    val pickFolder = { folderPicker.launch(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    if (state.hasLibrary) {
                        IconButton(onClick = viewModel::rescan, enabled = !state.isScanning) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan library")
                        }
                        IconButton(onClick = pickFolder, enabled = !state.isScanning) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = "Change music folder")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Emit nothing (not an empty Box) when idle so Scaffold falls back to the nav-bar inset.
        bottomBar = {
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
        },
    ) { padding ->
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            when {
                state.isLoading -> Unit
                !state.hasLibrary -> WelcomeContent(onPickFolder = pickFolder)
                state.albums.isEmpty() && !state.isScanning -> EmptyLibraryContent(
                    onRescan = viewModel::rescan,
                    onPickFolder = pickFolder,
                )
                else -> AlbumList(
                    albums = state.albums,
                    currentAlbumId = nowPlaying?.albumId,
                    listState = listState,
                    onAlbumClick = viewModel::openAlbum,
                    onPlayAlbum = viewModel::playAlbum,
                )
            }
            if (state.isScanning) {
                LinearProgressIndicator(modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun AlbumList(
    albums: List<Album>,
    currentAlbumId: Long?,
    listState: LazyListState,
    onAlbumClick: (Album) -> Unit,
    onPlayAlbum: (Album) -> Unit,
) {
    // `albums` arrives sorted by playCount, so grouping keeps the 0 -> 1 -> 2 order.
    val groups = remember(albums) { albums.groupBy { it.playCount } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for ((playCount, group) in groups) {
            item(key = "header-$playCount", contentType = "header") {
                SectionHeader(playCount = playCount, albumCount = group.size)
            }
            items(group, key = { it.id }, contentType = { "album" }) { album ->
                AlbumItem(
                    album = album,
                    isCurrent = album.id == currentAlbumId,
                    onClick = { onAlbumClick(album) },
                    onPlay = { onPlayAlbum(album) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(playCount: Int, albumCount: Int) {
    val (title, subtitle) = when (playCount) {
        0 -> "Up next" to "Not played yet"
        1 -> "One more spin" to "Played once"
        else -> "Completed" to "Played twice"
    }
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp)) {
        Text(
            text = "$title · $albumCount",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WelcomeContent(onPickFolder: () -> Unit) {
    CenteredMessage(
        title = "Finish what you start",
        body = "Pick your music library folder. Every sub-folder becomes an album, " +
            "and every album is locked once you've played it through twice.",
    ) {
        Button(onClick = onPickFolder) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Select Music Folder")
        }
    }
}

@Composable
private fun EmptyLibraryContent(onRescan: () -> Unit, onPickFolder: () -> Unit) {
    CenteredMessage(
        title = "No albums found",
        body = "Each album must be a folder directly inside your library folder, " +
            "containing .mp3, .flac or .m4a files.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRescan) { Text("Rescan") }
            Button(onClick = onPickFolder) { Text("Choose folder") }
        }
    }
}

@Composable
private fun CenteredMessage(title: String, body: String, actions: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        actions()
    }
}
