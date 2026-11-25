package com.aistudio.aimusic.wltv.jqr8x.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import java.io.File

@Composable
fun AudioMixesTabContent(
    mixes: List<AudioMixProject>,
    onPlayMix: (AudioMixProject) -> Unit,
    onReopenInMixer: (AudioMixProject) -> Unit,
    onUseInMusicVideo: (AudioMixProject) -> Unit,
    onSaveMixToDevice: (AudioMixProject) -> Unit,
    onShareMix: (AudioMixProject) -> Unit,
    onDeleteMix: (AudioMixProject) -> Unit
) {
    if (mixes.isEmpty()) {
        EmptyLibrary("No English & Twi mixes yet")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("audio_mixes_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(mixes, key = { it.id }) { mix ->
            AudioMixProjectCard(
                mix = mix,
                onPlay = { onPlayMix(mix) },
                onReopenInMixer = { onReopenInMixer(mix) },
                onUseInMusicVideo = { onUseInMusicVideo(mix) },
                onSaveToDevice = { onSaveMixToDevice(mix) },
                onShare = { onShareMix(mix) },
                onDelete = { onDeleteMix(mix) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioMixProjectCard(
    mix: AudioMixProject,
    onPlay: () -> Unit,
    onReopenInMixer: () -> Unit,
    onUseInMusicVideo: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val cleanPath = mix.localMp3Path.removePrefix("file://").trim()
    val hasLocalAudio = cleanPath.isNotEmpty() && File(cleanPath).exists()
    val exportedExtLabel = if (cleanPath.endsWith(".wav", ignoreCase = true)) "WAV" else "MP3"
    val formatsSummary = mix.tracks
        .map { it.formatLabel.ifBlank { "AUDIO" } }
        .distinct()
        .joinToString("/")
        .ifBlank { "MIX" }
    val durationSec = (mix.totalDurationMs / 1000L).coerceAtLeast(0L)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_mix_card_${mix.id}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mix.title.ifBlank { "Untitled Gospel Mix" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${mix.tracks.size} track(s) ($formatsSummary) • ${mix.captionCues.size} cues • ${durationSec}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Surface(
                    color = if (hasLocalAudio) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (hasLocalAudio) exportedExtLabel else mix.language.take(14),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasLocalAudio) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_mix_${mix.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Mix",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (mix.language.isNotBlank()) {
                Text(
                    text = "Language: ${mix.language}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (mix.lyrics.isNotBlank()) {
                Text(
                    text = mix.lyrics,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onPlay,
                    enabled = hasLocalAudio,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("play_mix_${mix.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play $exportedExtLabel")
                }

                OutlinedButton(
                    onClick = onReopenInMixer,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reopen_mix_${mix.id}")
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit in Mixer")
                }

                OutlinedButton(
                    onClick = onUseInMusicVideo,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("use_mix_mv_${mix.id}")
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Music Video")
                }

                if (hasLocalAudio) {
                    OutlinedButton(
                        onClick = onSaveToDevice,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_mix_${mix.id}")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("share_mix_${mix.id}")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}
