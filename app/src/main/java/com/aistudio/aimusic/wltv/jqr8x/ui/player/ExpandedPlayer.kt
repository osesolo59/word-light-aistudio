package com.aistudio.aimusic.wltv.jqr8x.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedPlayer(
    title: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val playerManager = LocalMusicPlayer.current
    val currentPosition by playerManager?.currentPosition?.collectAsState() ?: remember { mutableStateOf(0L) }
    val duration by playerManager?.duration?.collectAsState() ?: remember { mutableStateOf(0L) }
    val shuffleEnabled by playerManager?.shuffleEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    val repeatMode by playerManager?.repeatMode?.collectAsState() ?: remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    val queueTitles by playerManager?.queueTitles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    var showQueueDialog by remember { mutableStateOf(false) }

    // Poll position while playing
    LaunchedEffect(isPlaying, title) {
        playerManager?.updatePosition()
        while (isPlaying) {
            playerManager?.updatePosition()
            delay(500)
        }
    }

    val sliderPosition = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("expanded_player_close")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", modifier = Modifier.size(32.dp))
                }
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { showQueueDialog = true },
                    modifier = Modifier.testTag("expanded_player_queue")
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "Playback Queue")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Album Art Placeholder
            Surface(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title and Artist
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Word & Light AI STUDIO",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Seek Bar
            Slider(
                value = if (isDragging) dragPosition else sliderPosition,
                onValueChange = {
                    isDragging = true
                    dragPosition = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    playerManager?.seekTo((dragPosition * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.testTag("expanded_player_seek_bar")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isDragging) (dragPosition * duration).toLong() else currentPosition),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playerManager?.toggleShuffle() },
                    modifier = Modifier.testTag("expanded_player_shuffle")
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { playerManager?.seekTo((currentPosition - 10000).coerceAtLeast(0L)) },
                    modifier = Modifier.size(48.dp).testTag("expanded_player_replay_10")
                ) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Back 10s", modifier = Modifier.size(36.dp))
                }

                Surface(
                    onClick = onTogglePlay,
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(72.dp).testTag("expanded_player_play_pause")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { playerManager?.seekTo((currentPosition + 10000).coerceAtMost(duration)) },
                    modifier = Modifier.size(48.dp).testTag("expanded_player_forward_10")
                ) {
                    Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(36.dp))
                }

                IconButton(
                    onClick = { playerManager?.toggleRepeat() },
                    modifier = Modifier.testTag("expanded_player_repeat")
                ) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showQueueDialog) {
        AlertDialog(
            onDismissRequest = { showQueueDialog = false },
            title = { Text("Playback Queue") },
            text = {
                if (queueTitles.isEmpty()) {
                    Text("Current track: $title")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        queueTitles.forEachIndexed { index, qTitle ->
                            Text(
                                text = "${index + 1}. $qTitle",
                                fontWeight = if (qTitle == title) FontWeight.Bold else FontWeight.Normal,
                                color = if (qTitle == title) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueueDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
