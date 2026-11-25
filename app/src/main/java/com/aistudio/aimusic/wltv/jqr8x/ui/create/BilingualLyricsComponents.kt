package com.aistudio.aimusic.wltv.jqr8x.ui.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionLanguagePlannerCard(
    sectionLanguages: Map<String, String>,
    onSectionLanguageChange: (section: String, lang: String) -> Unit,
    onApplyPreset: (Map<String, String>) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("section_language_planner_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Section Language Mixer (English & Twi)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Choose English, Twi, or Mixed per song section",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Twi Verses + English Chorus",
                    "English Verses + Twi Chorus",
                    "Line-by-Line Mixed"
                ).forEach { preset ->
                    OutlinedButton(
                        onClick = { onApplyPreset(BilingualGospelHelper.applyPreset(preset)) },
                        modifier = Modifier.testTag(
                            "section_preset_${BilingualGospelHelper.languageTestTagSuffix(preset)}"
                        )
                    ) {
                        Text(preset, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            BilingualGospelHelper.SONG_SECTIONS.forEach { section ->
                val chosen = sectionLanguages[section] ?: "English"
                val secTag = section.lowercase().replace(" ", "_")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = section,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(84.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BilingualGospelHelper.SECTION_LANGUAGE_OPTIONS.forEach { langChoice ->
                            FilterChip(
                                selected = chosen.equals(langChoice, ignoreCase = true),
                                onClick = { onSectionLanguageChange(section, langChoice) },
                                modifier = Modifier.testTag("sec_lang_${secTag}_${langChoice.lowercase()}"),
                                label = { Text(langChoice, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BilingualCaptionCuesEditorCard(
    tagPrefix: String,
    cues: List<TimedBilingualCue>,
    onCuesChange: (List<TimedBilingualCue>) -> Unit,
    onAutoSyncFromLyrics: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_bilingual_cues_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Synchronized English & Twi Captions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${cues.size} timed caption cue${if (cues.size == 1) "" else "s"} for playback & MP4 video",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onAutoSyncFromLyrics != null) {
                    OutlinedButton(
                        onClick = onAutoSyncFromLyrics,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("${tagPrefix}_autosync_cues_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Sync Lyrics", maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = {
                        val nextStart = cues.maxOfOrNull { it.endSec } ?: 0
                        val nextCue = TimedBilingualCue(
                            id = "cue_${UUID.randomUUID().toString().take(6)}",
                            startSec = nextStart,
                            endSec = nextStart + 5,
                            sectionLabel = "Chorus",
                            englishText = "All glory to Jesus",
                            twiText = "Ayeyi nka Awurade Yesu"
                        )
                        onCuesChange(cues + nextCue)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("${tagPrefix}_add_cue_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Cue", maxLines = 1)
                }
            }

            cues.forEachIndexed { index, cue ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${tagPrefix}_cue_item_$index")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${cue.sectionLabel.ifBlank { "Cue ${index + 1}" }} • ${cue.startSec}s – ${cue.endSec}s",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        val updated = cue.copy(endSec = (cue.endSec - 1).coerceAtLeast(cue.startSec + 1))
                                        onCuesChange(cues.toMutableList().apply { set(index, updated) })
                                    }
                                ) { Text("-1s") }
                                Spacer(modifier = Modifier.width(4.dp))
                                OutlinedButton(
                                    onClick = {
                                        val updated = cue.copy(endSec = cue.endSec + 1)
                                        onCuesChange(cues.toMutableList().apply { set(index, updated) })
                                    }
                                ) { Text("+1s") }
                                IconButton(
                                    onClick = { onCuesChange(cues.filterNot { it.id == cue.id }) },
                                    modifier = Modifier.testTag("${tagPrefix}_remove_cue_$index")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove caption cue")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = cue.englishText,
                            onValueChange = { value ->
                                val updated = cue.copy(englishText = value)
                                onCuesChange(cues.toMutableList().apply { set(index, updated) })
                            },
                            label = { Text("English Caption") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = cue.twiText,
                            onValueChange = { value ->
                                val updated = cue.copy(twiText = value)
                                onCuesChange(cues.toMutableList().apply { set(index, updated) })
                            },
                            label = { Text("Twi Caption") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OpenAudioMixerBannerButton(
    onOpenAudioMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onOpenAudioMixer,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("open_audio_mixer_button"),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.GraphicEq, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Open Multi-Track Audio Mixer (MP3 / WAV / M4A -> MP3)", fontWeight = FontWeight.Bold)
    }
}
