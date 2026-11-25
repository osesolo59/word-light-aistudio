package com.aistudio.aimusic.wltv.jqr8x.ui.mixer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.media.AudioFileShareHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.create.BilingualCaptionCuesEditorCard
import com.aistudio.aimusic.wltv.jqr8x.ui.create.SectionLanguagePlannerCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioMixerScreen(
    onNavigateToMusicVideoCreator: (
        title: String,
        language: String,
        localMp3Uri: String,
        lyrics: String,
        captionCues: List<TimedBilingualCue>
    ) -> Unit,
    onNavigateToProjects: () -> Unit,
    repositoryOverride: MusicRepository? = null,
    viewModelOverride: AudioMixerViewModel? = null,
    reopenMixFlow: StateFlow<AudioMixProject?>? = null,
    onConsumeReopenMix: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val viewModel: AudioMixerViewModel = viewModelOverride ?: viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return AudioMixerViewModel(repository) as T
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fallbackReopen = remember { MutableStateFlow<AudioMixProject?>(null) }
    val reopenMix by (reopenMixFlow ?: fallbackReopen).collectAsStateWithLifecycle()
    var showLoadLyricDialog by remember { mutableStateOf(false) }

    LaunchedEffect(reopenMix?.id) {
        val target = reopenMix ?: return@LaunchedEffect
        viewModel.loadMixProject(target)
        onConsumeReopenMix()
    }

    val multiAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioUris(context, uris)
        }
    }

    val createMp3DocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/*")
    ) { destUri ->
        if (destUri != null) {
            viewModel.completeDocumentPickerSave(context, destUri)
        } else {
            viewModel.dismissPendingDocumentSave()
        }
    }

    LaunchedEffect(state.pendingDocumentSaveFileName) {
        val fileName = state.pendingDocumentSaveFileName ?: return@LaunchedEffect
        createMp3DocLauncher.launch(fileName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "English & Twi Gospel Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Bilingual songwriting, multi-track MP3/WAV/M4A mixing & MP3 export",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "On-device mastering studio: import MP3, WAV & M4A tracks, balance vocals & instrumentals, apply EQ/compressor/reverb, and export WAV (44.1/48 kHz) or MP3 up to 320 kbps for free (no AI vocal synthesis).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        if (state.feedbackMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mixer_feedback_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.feedbackMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::clearFeedback) { Text("OK") }
                }
            }
        }

        if (state.errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mixer_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                }
            }
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::updateTitle,
            label = { Text("Gospel Song / Mix Title") },
            placeholder = { Text("e.g. Onyame Nhyira (Morning Praise)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mixer_title_input")
        )

        Text("1. Language & Section Mixer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI,
                BilingualGospelHelper.LANGUAGE_ENGLISH,
                BilingualGospelHelper.LANGUAGE_TWI
            ).forEach { lang ->
                FilterChip(
                    selected = state.language == lang,
                    onClick = { viewModel.updateLanguage(lang) },
                    modifier = Modifier.testTag("mixer_lang_${BilingualGospelHelper.languageTestTagSuffix(lang)}"),
                    label = { Text(lang) }
                )
            }
        }

        if (BilingualGospelHelper.isMixedEnglishTwi(state.language)) {
            SectionLanguagePlannerCard(
                sectionLanguages = state.sectionLanguages,
                onSectionLanguageChange = viewModel::updateSectionLanguage,
                onApplyPreset = viewModel::applyLanguagePreset
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.generateBilingualLyrics(useLiveAi = false) },
                enabled = !state.isGeneratingLyrics,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mixer_demo_lyrics_button")
            ) {
                Text("Demo Twi+Eng Lyrics", maxLines = 1)
            }
            Button(
                onClick = { viewModel.generateBilingualLyrics(useLiveAi = true) },
                enabled = !state.isGeneratingLyrics,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mixer_ai_lyrics_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Live AI Lyrics", maxLines = 1)
            }
        }

        if (state.savedLyrics.isNotEmpty()) {
            OutlinedButton(
                onClick = { showLoadLyricDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("mixer_load_saved_lyric_button")
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Load Saved Song Lyrics (${state.savedLyrics.size})")
            }
        }

        OutlinedTextField(
            value = state.lyrics,
            onValueChange = viewModel::updateLyrics,
            label = { Text("Bilingual Lyrics Editor (English & Twi)") },
            placeholder = { Text("Edit English and Asante Twi lines for Intro, Verse, Chorus, Bridge...") },
            minLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mixer_lyrics_input")
        )

        BilingualCaptionCuesEditorCard(
            tagPrefix = "mixer",
            cues = state.captionCues,
            onCuesChange = viewModel::updateCaptionCues,
            onAutoSyncFromLyrics = viewModel::autoSyncCaptionsFromLyrics
        )

        Text("2. Multi-Track Audio Recordings (MP3, WAV, M4A)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { multiAudioPickerLauncher.launch(AudioFileShareHelper.PICKER_AUDIO_MIME_TYPES) },
                enabled = !state.isImportingAudio,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mixer_import_audio_button")
            ) {
                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Audio", maxLines = 1)
            }
            OutlinedButton(
                onClick = { viewModel.addSamplePadTrack(context, isSecondVoice = state.tracks.isNotEmpty()) },
                enabled = !state.isImportingAudio,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mixer_add_sample_track_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Pad WAV", maxLines = 1)
            }
        }

        if (state.tracks.isEmpty()) {
            AssistChip(
                onClick = { viewModel.addSamplePadTrack(context, isSecondVoice = false) },
                label = { Text("No tracks yet — tap Import Audio (MP3/WAV/M4A) or Add Pad WAV") }
            )
        } else {
            state.tracks.forEachIndexed { idx, track ->
                AudioMixTrackCard(
                    index = idx,
                    track = track,
                    onVolumeChange = { vol -> viewModel.updateTrackVolume(track.id, vol) },
                    onToggleMute = { viewModel.toggleTrackMute(track.id) },
                    onTrimChange = { startMs, endMs -> viewModel.updateTrackTrim(track.id, startMs, endMs) },
                    onOffsetChange = { offMs -> viewModel.updateTrackOffset(track.id, offMs) },
                    onRoleChange = { role -> viewModel.updateTrackRole(track.id, role) },
                    onRemove = { viewModel.removeTrack(track.id) }
                )
            }
        }

        ProAudioMasteringCard(
            tracks = state.tracks,
            settings = state.masteringSettings,
            selectedFormat = state.selectedExportFormat,
            onSelectPreset = viewModel::selectMasteringPreset,
            onUpdateSettings = viewModel::updateMasteringSettings,
            onSelectFormat = viewModel::selectExportFormat
        )

        if (state.isRenderingPreview || state.isExportingMp3 || state.isSavingProject || state.isGeneratingLyrics) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        SynchronizedMixPreviewPlayerCard(
            audioFilePath = state.previewWavPath.ifBlank { state.exportedMp3Path },
            cues = state.captionCues,
            durationMsHint = state.exportedDurationMs
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.renderMixPreview(context) },
                enabled = state.tracks.isNotEmpty() && !state.isRenderingPreview && !state.isExportingMp3,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mixer_render_preview_button")
            ) {
                Text("Render Preview", maxLines = 1)
            }
            Button(
                onClick = { viewModel.exportFinalMp3(context) },
                enabled = state.tracks.isNotEmpty() && !state.isExportingMp3 && !state.isRenderingPreview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mixer_export_mp3_button")
            ) {
                Text("Export ${state.selectedExportFormat.label}", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        if (state.exportedMp3Path.isNotBlank() && File(state.exportedMp3Path.removePrefix("file://")).exists()) {
            val exportedFile = File(state.exportedMp3Path.removePrefix("file://"))
            val formatBadge = state.exportedFormatLabel.ifBlank {
                if (exportedFile.extension.equals("wav", ignoreCase = true)) "WAV" else "MP3"
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mixer_exported_mp3_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$formatBadge Ready: ${exportedFile.name} (${state.exportedMp3SizeBytes / 1024L} KB • ${state.exportedDurationMs / 1000L}s)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.saveMp3ToDevice(context) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("mixer_save_device_mp3_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Audio", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                val share = AudioFileShareHelper.createShareAudioIntent(
                                    context = context,
                                    audioFilePath = state.exportedMp3Path,
                                    title = state.title
                                )
                                if (share != null) {
                                    context.startActivity(Intent.createChooser(share, "Share Mastered Audio"))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("mixer_share_mp3_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Audio", maxLines = 1)
                        }
                    }
                    Button(
                        onClick = {
                            val uriStr = Uri.fromFile(exportedFile).toString()
                            onNavigateToMusicVideoCreator(
                                state.title.ifBlank { "English & Twi Gospel Mix" },
                                state.language,
                                uriStr,
                                state.lyrics,
                                state.captionCues
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("mixer_use_in_mv_button")
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Mastered Audio + Captions in Music Video", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = viewModel::saveMixProject,
                enabled = !state.isSavingProject && (state.tracks.isNotEmpty() || state.lyrics.isNotBlank()),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mixer_save_project_button")
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save to My Projects", maxLines = 1)
            }
            OutlinedButton(
                onClick = onNavigateToProjects,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mixer_go_projects_button")
            ) {
                Text("Projects")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLoadLyricDialog) {
        AlertDialog(
            onDismissRequest = { showLoadLyricDialog = false },
            title = { Text("Load Saved Lyrics", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.savedLyrics, key = { it.id }) { lyric ->
                        Surface(
                            onClick = {
                                viewModel.loadSavedLyric(lyric)
                                showLoadLyricDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(lyric.title.ifBlank { "Untitled Lyrics" }, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${lyric.language} • ${lyric.captionCues.size} cues",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadLyricDialog = false }) { Text("Close") }
            }
        )
    }
}
