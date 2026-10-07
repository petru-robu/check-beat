package com.debloated.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Hosts the two screens (album list, album tracklist) and the state they share, so none of it
 * is lost when switching between them, plus the full-screen player that slides over both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebloatedPlayerRoot(viewModel: LibraryViewModel) {
    val openAlbumId by viewModel.openAlbumId.collectAsStateWithLifecycle()
    val playerExpanded by viewModel.playerExpanded.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Needed on Android 13+ for the playback notification; playback itself works without it.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // Nothing left to show (queue cleared, e.g. the album just got locked): close the full player,
    // so it does not pop up again the next time something plays.
    LaunchedEffect(nowPlaying == null) {
        if (nowPlaying == null) viewModel.collapsePlayer()
    }

    // Declared last, so it gets Back first: full player, then album tracklist, then the app.
    BackHandler(enabled = openAlbumId != null, onBack = viewModel::closeAlbum)
    BackHandler(enabled = playerExpanded, onBack = viewModel::collapsePlayer)

    Box {
        val albumId = openAlbumId
        if (albumId == null) {
            LibraryScreen(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                scrollBehavior = scrollBehavior,
                listState = listState,
            )
        } else {
            AlbumScreen(albumId = albumId, viewModel = viewModel, snackbarHostState = snackbarHostState)
        }

        AnimatedVisibility(
            visible = playerExpanded && nowPlaying != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            nowPlaying?.let { PlayerScreen(nowPlaying = it, viewModel = viewModel) }
        }
    }
}
