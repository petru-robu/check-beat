package com.debloated.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.debloated.player.data.Album
import com.debloated.player.data.PlayRules
import com.debloated.player.data.isLocked

/**
 * One album row. Tapping it opens the tracklist; the trailing button plays the album from the
 * start. A completed album ([isLocked]) is greyed out, uses disabled text colours and is not
 * clickable at all (the Card is disabled, which also exposes it as disabled to a11y).
 */
@Composable
fun AlbumItem(
    album: Album,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locked = album.isLocked
    val colors = MaterialTheme.colorScheme

    Card(
        onClick = onClick,
        enabled = !locked,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (isCurrent) colors.onPrimaryContainer else colors.onSurface,
            disabledContainerColor = colors.onSurface.copy(alpha = 0.08f),
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AlbumCover(locked = locked)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                PlayProgress(playCount = album.playCount, locked = locked)
            }

            if (locked) {
                // Same footprint as the play button so locked and unlocked rows line up.
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Rounded.Lock, contentDescription = "Completed")
                }
            } else {
                IconButton(onClick = onPlay) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = "Play album")
                }
            }
        }
    }
}

/** Two dots (one per required play) plus the "0 / 2 plays" label. */
@Composable
internal fun PlayProgress(playCount: Int, locked: Boolean, modifier: Modifier = Modifier) {
    val contentColor = LocalContentColor.current
    val filled = if (locked) contentColor else MaterialTheme.colorScheme.primary

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(PlayRules.MAX_PLAYS) { index ->
            val done = index < playCount
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .then(
                        if (done) Modifier.background(filled)
                        else Modifier.border(1.5.dp, contentColor.copy(alpha = 0.5f), CircleShape),
                    ),
            )
            Spacer(Modifier.size(6.dp))
        }
        Spacer(Modifier.size(2.dp))
        Text(
            text = "$playCount / ${PlayRules.MAX_PLAYS} plays",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Plain grey placeholder cover (no artwork is read, and no lettering); lighter when locked. */
@Composable
private fun AlbumCover(locked: Boolean) {
    val tile = MaterialTheme.colorScheme.onSurface.copy(alpha = if (locked) 0.08f else 0.14f)

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = LocalContentColor.current.copy(alpha = 0.6f),
        )
    }
}
