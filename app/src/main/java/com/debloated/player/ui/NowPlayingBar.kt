package com.debloated.player.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.debloated.player.playback.NowPlaying
import com.debloated.player.playback.PlaybackPosition

/**
 * Persistent bottom bar: current track, previous / play-pause / next, and a seek bar with the
 * elapsed and total time. Tapping the bar (outside the buttons and slider) calls [onClick] to
 * open the full-screen player. [position] is a lambda so the ticking time only recomposes this bar.
 */
@Composable
fun NowPlayingBar(
    nowPlaying: NowPlaying,
    position: () -> PlaybackPosition,
    onClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nowPlaying.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = nowPlaying.albumName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onSkipPrevious) {
                    Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = "Previous track")
                }
                FilledIconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(onClick = onSkipNext, enabled = nowPlaying.hasNext) {
                    Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = "Next track")
                }
            }
            SeekBar(
                position = position,
                onSeek = onSeek,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * Elapsed time, a draggable slider, total time. While the finger is down the slider and the
 * elapsed label follow it instead of the player; the seek happens once, on release (a tap on
 * the track seeks too). The times flank the slider, or sit under it when [labelsBelow].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeekBar(
    position: () -> PlaybackPosition,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    labelsBelow: Boolean = false,
) {
    val (positionMs, durationMs) = position()
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val fraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = if (dragFraction != null) (fraction * durationMs).toLong() else positionMs
    val elapsed = formatDuration(shownMs)
    val total = formatDuration(durationMs)

    val slider = @Composable { sliderModifier: Modifier ->
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                dragFraction = null
            },
            enabled = durationMs > 0,
            modifier = sliderModifier
                .height(32.dp)
                .semantics { contentDescription = "Seek" },
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = remember { MutableInteractionSource() },
                    thumbSize = DpSize(14.dp, 14.dp),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
        )
    }

    if (labelsBelow) {
        Column(modifier = modifier) {
            slider(Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth()) {
                TimeLabel(text = elapsed, alignment = TextAlign.Start, modifier = Modifier.weight(1f))
                TimeLabel(text = total, alignment = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            TimeLabel(text = elapsed, alignment = TextAlign.Start, modifier = Modifier.width(44.dp))
            slider(Modifier.weight(1f).padding(horizontal = 8.dp))
            TimeLabel(text = total, alignment = TextAlign.End, modifier = Modifier.width(44.dp))
        }
    }
}

@Composable
private fun TimeLabel(text: String, alignment: TextAlign, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = alignment,
        maxLines = 1,
    )
}
