package com.aistudio.aimusic.wltv.jqr8x.ui.mixer

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aistudio.aimusic.wltv.jqr8x.data.media.Media3SlideshowExporter
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioTrackRole
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioMixTrackCard(
    index: Int,
    track: AudioMixTrack,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onTrimChange: (Long, Long) -> Unit,
    onOffsetChange: (Long) -> Unit,
    onRoleChange: (AudioTrackRole) -> Unit = {},
    onRemove: () -> Unit
) {
    val effectiveTrimEnd = if (track.trimEndMs > track.trimStartMs) track.trimEndMs else track.durationMs
    val trimLenSec = ((effectiveTrimEnd - track.trimStartMs) / 1000.0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mixer_track_card_$index"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.displayName.ifBlank { "Track ${index + 1}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Length: ${track.durationMs / 1000L}s • Active: ${String.format("%.1f", trimLenSec)}s • Start Offset: ${track.offsetMs / 1000L}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = track.formatLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mixer_track_mute_$index")
                ) {
                    Icon(
                        imageVector = if (track.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (track.isMuted) "Unmute track ${index + 1}" else "Mute track ${index + 1}",
                        tint = if (track.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mixer_track_remove_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove track ${index + 1}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AudioTrackRole.entries.forEach { role ->
                    FilterChip(
                        selected = track.role == role,
                        onClick = { onRoleChange(role) },
                        modifier = Modifier.testTag("mixer_track_role_${index}_${role.name.lowercase()}"),
                        label = { Text(role.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Volume: ${if (track.isMuted) "MUTED" else "${(track.volume * 100).roundToInt()}%"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(108.dp)
                )
                Slider(
                    value = track.volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1.5f,
                    enabled = !track.isMuted,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mixer_track_volume_$index")
                )
            }

            // Trim & Offset controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trim In: ${track.trimStartMs / 1000L}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { onTrimChange((track.trimStartMs - 1000L).coerceAtLeast(0L), effectiveTrimEnd) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("mixer_trim_start_minus_$index")
                        ) { Text("-1s") }
                        OutlinedButton(
                            onClick = { onTrimChange(track.trimStartMs + 1000L, effectiveTrimEnd) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("mixer_trim_start_plus_$index")
                        ) { Text("+1s") }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trim Out: ${effectiveTrimEnd / 1000L}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { onTrimChange(track.trimStartMs, effectiveTrimEnd - 1000L) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("mixer_trim_end_minus_$index")
                        ) { Text("-1s") }
                        OutlinedButton(
                            onClick = { onTrimChange(track.trimStartMs, effectiveTrimEnd + 1000L) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("mixer_trim_end_plus_$index")
                        ) { Text("+1s") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Track Entry Offset: ${track.offsetMs / 1000L}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onOffsetChange((track.offsetMs - 1000L).coerceAtLeast(0L)) },
                        enabled = track.offsetMs > 0L
                    ) { Text("-1s") }
                    OutlinedButton(
                        onClick = { onOffsetChange(track.offsetMs + 1000L) }
                    ) { Text("+1s") }
                }
            }
        }
    }
}

@Composable
fun SynchronizedMixPreviewPlayerCard(
    audioFilePath: String,
    cues: List<TimedBilingualCue>,
    durationMsHint: Long
) {
    val context = LocalContext.current
    val fileExists = remember(audioFilePath) {
        audioFilePath.isNotBlank() && File(audioFilePath.removePrefix("file://")).exists()
    }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var totalMs by remember(durationMsHint) { mutableLongStateOf(durationMsHint.coerceAtLeast(1_000L)) }

    val exoPlayer = remember(audioFilePath, fileExists) {
        if (!fileExists) null
        else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(audioFilePath.removePrefix("file://")))))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
            exoPlayer?.release()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (exoPlayer != null && isPlaying) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.duration > 0L) totalMs = exoPlayer.duration
            delay(200L)
        }
    }

    val activeCueIdx = Media3SlideshowExporter.resolveActiveCueIndex(cues, positionMs)
    val activeCue = cues.getOrNull(activeCueIdx)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mixer_preview_player_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF280A4B)),
        border = BorderStroke(1.5.dp, Color(0xFFFFD700))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color(0xFFFFD700))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mix Playback & Live Bilingual Captions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Surface(
                color = Color(0x44FFFFFF),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (activeCue != null) {
                        Text(
                            text = "${activeCue.sectionLabel} (${activeCue.startSec}s–${activeCue.endSec}s)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                        if (activeCue.twiText.isNotBlank()) {
                            Text(
                                text = activeCue.twiText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        if (activeCue.englishText.isNotBlank()) {
                            Text(
                                text = activeCue.englishText,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFFF5F0FF)
                            )
                        }
                    } else {
                        Text(
                            text = "Add or auto-sync English & Twi captions to preview them synchronized here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE0D0FF)
                        )
                    }
                }
            }

            if (totalMs > 0L) {
                Slider(
                    value = positionMs.coerceIn(0L, totalMs).toFloat(),
                    onValueChange = { newPos ->
                        positionMs = newPos.toLong()
                        exoPlayer?.seekTo(positionMs)
                    },
                    valueRange = 0f..totalMs.toFloat().coerceAtLeast(1000f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${positionMs / 1000L}s / ${totalMs / 1000L}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFD700)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (exoPlayer != null) {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0L)
                                    }
                                    exoPlayer.play()
                                }
                            }
                        },
                        enabled = fileExists,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mixer_preview_play_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFFFFD700)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlaying) "Pause" else if (fileExists) "Play Preview" else "Render Preview First",
                            color = Color.White
                        )
                    }
                    if (fileExists) {
                        IconButton(
                            onClick = {
                                exoPlayer?.pause()
                                exoPlayer?.seekTo(0L)
                                positionMs = 0L
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop preview", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
