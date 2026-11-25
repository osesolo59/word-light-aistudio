package com.aistudio.aimusic.wltv.jqr8x.ui.mixer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsRequest
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.media.AudioFileShareHelper
import com.aistudio.aimusic.wltv.jqr8x.data.media.AudioMixerEngine
import com.aistudio.aimusic.wltv.jqr8x.data.media.AudioTrackImportResult
import com.aistudio.aimusic.wltv.jqr8x.data.media.SaveToDeviceResult
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioExportFormat
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMasteringSettings
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioTrackRole
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.model.MasteringPreset
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AudioMixerUiState(
    val projectId: String = "",
    val title: String = "",
    val language: String = BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI,
    val sectionLanguages: Map<String, String> = BilingualGospelHelper.defaultSectionLanguages(
        BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI
    ),
    val lyrics: String = "",
    val captionCues: List<TimedBilingualCue> = emptyList(),
    val tracks: List<AudioMixTrack> = emptyList(),
    val selectedExportFormat: AudioExportFormat = AudioExportFormat.MP3_320,
    val masteringSettings: AudioMasteringSettings = AudioMasteringSettings(),
    val isImportingAudio: Boolean = false,
    val isGeneratingLyrics: Boolean = false,
    val isRenderingPreview: Boolean = false,
    val isExportingMp3: Boolean = false,
    val isSavingProject: Boolean = false,
    val previewWavPath: String = "",
    val exportedMp3Path: String = "",
    val exportedMp3SizeBytes: Long = 0L,
    val exportedDurationMs: Long = 0L,
    val exportedFormatLabel: String = "",
    val savedLyrics: List<Lyric> = emptyList(),
    val pendingDocumentSaveFileName: String? = null,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class AudioMixerViewModel(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioMixerUiState())
    val uiState: StateFlow<AudioMixerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMyLyrics().collectLatest { items ->
                _uiState.update { it.copy(savedLyrics = items) }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateLanguage(language: String) {
        _uiState.update { state ->
            val nextSections = if (state.sectionLanguages.isEmpty() || !BilingualGospelHelper.isMixedEnglishTwi(language)) {
                BilingualGospelHelper.defaultSectionLanguages(language)
            } else {
                state.sectionLanguages
            }
            state.copy(language = language, sectionLanguages = nextSections)
        }
    }

    fun updateSectionLanguage(section: String, langChoice: String) {
        _uiState.update { state ->
            val updated = state.sectionLanguages.toMutableMap().apply { put(section, langChoice) }
            state.copy(sectionLanguages = updated)
        }
    }

    fun applyLanguagePreset(presetMap: Map<String, String>) {
        _uiState.update {
            it.copy(
                language = BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI,
                sectionLanguages = presetMap
            )
        }
    }

    fun updateLyrics(lyrics: String) {
        _uiState.update { it.copy(lyrics = lyrics) }
    }

    fun updateCaptionCues(cues: List<TimedBilingualCue>) {
        _uiState.update { it.copy(captionCues = cues) }
    }

    fun autoSyncCaptionsFromLyrics() {
        val state = _uiState.value
        val durationSec = (state.exportedDurationMs / 1000L).toInt().coerceAtLeast(0)
        val cues = BilingualGospelHelper.generateCuesFromLyrics(
            formattedLyrics = state.lyrics,
            totalDurationSecHint = durationSec
        )
        _uiState.update {
            it.copy(
                captionCues = cues,
                feedbackMessage = if (cues.isEmpty()) {
                    "Write or generate lyrics first to auto-sync English & Twi captions."
                } else {
                    "Synced ${cues.size} bilingual caption cues."
                }
            )
        }
    }

    fun generateBilingualLyrics(useLiveAi: Boolean) {
        val state = _uiState.value
        val effectiveTopic = state.title.ifBlank { "Grace and Praise" }
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingLyrics = true, errorMessage = null) }
            try {
                val request = AiLyricsRequest(
                    title = state.title,
                    topic = effectiveTopic,
                    genre = "Gospel",
                    mood = "Uplifting",
                    tempo = "Moderate (95 BPM)",
                    language = state.language,
                    sectionLanguages = if (BilingualGospelHelper.isMixedEnglishTwi(state.language)) {
                        state.sectionLanguages
                    } else {
                        emptyMap()
                    }
                )
                val result = repository.generateAiLyrics(request, useDemoMode = !useLiveAi)
                _uiState.update {
                    it.copy(
                        isGeneratingLyrics = false,
                        title = it.title.ifBlank { result.title },
                        lyrics = result.lyrics,
                        captionCues = result.captionCues,
                        feedbackMessage = if (result.isDemo) {
                            "Generated natural English & Twi template lyrics with ${result.captionCues.size} timed captions."
                        } else {
                            "Generated Live Gemini English & Twi lyrics with ${result.captionCues.size} timed captions."
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingLyrics = false,
                        errorMessage = e.localizedMessage ?: "Live AI lyrics unavailable; tap 'Demo Lyrics' to continue free offline."
                    )
                }
            }
        }
    }

    fun loadSavedLyric(lyric: Lyric) {
        val cues = lyric.captionCues.ifEmpty { BilingualGospelHelper.generateCuesFromLyrics(lyric.lyrics) }
        _uiState.update {
            it.copy(
                title = it.title.ifBlank { lyric.title },
                language = lyric.language.ifBlank { BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI },
                sectionLanguages = lyric.sectionLanguages.ifEmpty {
                    BilingualGospelHelper.defaultSectionLanguages(lyric.language)
                },
                lyrics = lyric.lyrics,
                captionCues = cues,
                feedbackMessage = "Loaded lyrics \"${lyric.title}\""
            )
        }
    }

    fun loadMixProject(mix: AudioMixProject) {
        val existingMp3 = mix.localMp3Path.takeIf { it.isNotBlank() && File(it.removePrefix("file://")).exists() }.orEmpty()
        _uiState.update {
            it.copy(
                projectId = mix.id,
                title = mix.title,
                language = mix.language.ifBlank { BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI },
                sectionLanguages = mix.sectionLanguages.ifEmpty {
                    BilingualGospelHelper.defaultSectionLanguages(mix.language)
                },
                lyrics = mix.lyrics,
                captionCues = mix.captionCues,
                tracks = mix.tracks,
                exportedMp3Path = existingMp3,
                exportedDurationMs = mix.totalDurationMs,
                exportedMp3SizeBytes = if (existingMp3.isNotBlank()) File(existingMp3.removePrefix("file://")).length() else 0L,
                feedbackMessage = "Reopened mix project \"${mix.title}\""
            )
        }
    }

    fun importAudioUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingAudio = true, errorMessage = null) }
            when (val res = AudioMixerEngine.importAudioTracksFromUris(context, uris)) {
                is AudioTrackImportResult.Success -> {
                    _uiState.update { state ->
                        val nextTracks = state.tracks + res.tracks
                        val msg = buildString {
                            append("Added ${res.tracks.size} audio track(s)")
                            if (res.skippedUnsupported > 0) {
                                append(" (${res.skippedUnsupported} skipped — MP3, WAV, M4A only)")
                            }
                        }
                        state.copy(
                            isImportingAudio = false,
                            tracks = nextTracks,
                            feedbackMessage = msg
                        )
                    }
                }
                is AudioTrackImportResult.Error -> {
                    _uiState.update {
                        it.copy(isImportingAudio = false, errorMessage = res.message)
                    }
                }
            }
        }
    }

    fun addSamplePadTrack(context: Context, isSecondVoice: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingAudio = true, errorMessage = null) }
            try {
                val freq = if (isSecondVoice) 329.63 else 261.63
                val name = if (isSecondVoice) "Choir Harmony Tone (Sample WAV)" else "Worship Warm Pad (Sample WAV)"
                val file = withContext(Dispatchers.IO) {
                    AudioFileShareHelper.createBuiltInWarmPadSampleWav(
                        context = context,
                        sampleTitle = name,
                        baseFreqHz = freq,
                        durationMs = 6_000L
                    )
                }
                val track = AudioMixerEngine.importAudioFileToTrack(
                    file = file,
                    displayName = name,
                    formatLabel = "WAV"
                )
                _uiState.update {
                    it.copy(
                        isImportingAudio = false,
                        tracks = it.tracks + track,
                        feedbackMessage = "Added $name to mixer"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isImportingAudio = false, errorMessage = e.localizedMessage ?: "Failed to create sample track")
                }
            }
        }
    }

    fun setTracksForTest(tracks: List<AudioMixTrack>) {
        _uiState.update { it.copy(tracks = tracks) }
    }

    fun updateTrackVolume(trackId: String, volume: Float) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { t ->
                    if (t.id == trackId) t.copy(volume = volume.coerceIn(0f, 1.5f)) else t
                }
            )
        }
    }

    fun toggleTrackMute(trackId: String) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { t ->
                    if (t.id == trackId) t.copy(isMuted = !t.isMuted) else t
                }
            )
        }
    }

    fun updateTrackTrim(trackId: String, trimStartMs: Long, trimEndMs: Long) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { t ->
                    if (t.id == trackId) {
                        val maxEnd = t.durationMs.coerceAtLeast(1_000L)
                        val clampedStart = trimStartMs.coerceIn(0L, maxEnd - 250L)
                        val clampedEnd = trimEndMs.coerceIn(clampedStart + 250L, maxEnd)
                        t.copy(trimStartMs = clampedStart, trimEndMs = clampedEnd)
                    } else t
                }
            )
        }
    }

    fun updateTrackOffset(trackId: String, offsetMs: Long) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { t ->
                    if (t.id == trackId) t.copy(offsetMs = offsetMs.coerceIn(0L, 120_000L)) else t
                }
            )
        }
    }

    fun updateTrackRole(trackId: String, role: AudioTrackRole) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { t ->
                    if (t.id == trackId) t.copy(role = role) else t
                }
            )
        }
    }

    fun selectExportFormat(format: AudioExportFormat) {
        _uiState.update { it.copy(selectedExportFormat = format) }
    }

    fun selectMasteringPreset(preset: MasteringPreset) {
        _uiState.update { state ->
            state.copy(
                masteringSettings = AudioMasteringSettings.fromPreset(
                    preset = preset,
                    vocalBalanceOffset = state.masteringSettings.vocalBalanceOffset
                )
            )
        }
    }

    fun updateMasteringSettings(settings: AudioMasteringSettings) {
        _uiState.update { it.copy(masteringSettings = settings) }
    }

    fun removeTrack(trackId: String) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.filterNot { it.id == trackId },
                feedbackMessage = "Removed track from mix"
            )
        }
    }

    fun renderMixPreview(context: Context) {
        val state = _uiState.value
        if (state.tracks.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Import at least one MP3, WAV, or M4A track first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRenderingPreview = true, errorMessage = null) }
            try {
                val out = AudioMixerEngine.renderPreviewWav(
                    context = context,
                    tracks = state.tracks,
                    title = state.title.ifBlank { "Gospel_Mix" },
                    sampleRate = state.selectedExportFormat.sampleRateHz,
                    mastering = state.masteringSettings
                )
                _uiState.update {
                    it.copy(
                        isRenderingPreview = false,
                        previewWavPath = out.filePath,
                        exportedDurationMs = out.durationMs,
                        feedbackMessage = "Mastered preview ready (${out.formatLabel}, ${out.durationMs / 1000L}s). Press Play below."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRenderingPreview = false, errorMessage = e.localizedMessage ?: "Failed to render audio preview.")
                }
            }
        }
    }

    fun exportFinalMp3(context: Context) {
        val state = _uiState.value
        if (state.tracks.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Import at least one MP3, WAV, or M4A track before exporting.") }
            return
        }
        val chosenFormat = state.selectedExportFormat
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingMp3 = true, errorMessage = null) }
            try {
                val out = AudioMixerEngine.exportFinalAudio(
                    context = context,
                    tracks = state.tracks,
                    title = state.title.ifBlank { "WordLight_Gospel_Mix" },
                    format = chosenFormat,
                    mastering = state.masteringSettings
                )
                _uiState.update { current ->
                    val autoCues = if (current.captionCues.isEmpty() && current.lyrics.isNotBlank()) {
                        BilingualGospelHelper.generateCuesFromLyrics(
                            formattedLyrics = current.lyrics,
                            totalDurationSecHint = (out.durationMs / 1000L).toInt()
                        )
                    } else {
                        current.captionCues
                    }
                    current.copy(
                        isExportingMp3 = false,
                        exportedMp3Path = out.filePath,
                        exportedMp3SizeBytes = out.fileSizeBytes,
                        exportedDurationMs = out.durationMs,
                        exportedFormatLabel = out.formatLabel,
                        previewWavPath = out.filePath,
                        captionCues = autoCues,
                        feedbackMessage = "Exported ${out.formatLabel} (${out.durationMs / 1000L}s, ${out.fileSizeBytes / 1024L} KB)."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExportingMp3 = false, errorMessage = e.localizedMessage ?: "Audio export failed.")
                }
            }
        }
    }

    fun saveMixProject() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProject = true, errorMessage = null) }
            try {
                val saved = repository.saveAudioMixProject(
                    existingId = state.projectId,
                    title = state.title.ifBlank { "English & Twi Gospel Mix" },
                    language = state.language,
                    sectionLanguages = if (BilingualGospelHelper.isMixedEnglishTwi(state.language)) {
                        state.sectionLanguages
                    } else {
                        emptyMap()
                    },
                    lyrics = state.lyrics,
                    captionCues = state.captionCues,
                    tracks = state.tracks,
                    totalDurationMs = state.exportedDurationMs,
                    localMp3Path = state.exportedMp3Path
                )
                _uiState.update {
                    it.copy(
                        isSavingProject = false,
                        projectId = saved.id,
                        title = saved.title,
                        feedbackMessage = "Saved \"${saved.title}\" safely to My Projects."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSavingProject = false, errorMessage = e.localizedMessage ?: "Sign in required to save mix projects.")
                }
            }
        }
    }

    fun saveMp3ToDevice(context: Context) {
        val state = _uiState.value
        if (state.exportedMp3Path.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Export Final Master first before saving to device.") }
            return
        }
        viewModelScope.launch {
            when (val res = AudioFileShareHelper.saveMixedAudioToDevice(context, state.exportedMp3Path, state.title)) {
                is SaveToDeviceResult.SavedToMediaStore -> {
                    _uiState.update { it.copy(feedbackMessage = "Saved audio to ${res.displayPath}") }
                }
                is SaveToDeviceResult.RequiresDocumentPicker -> {
                    _uiState.update { it.copy(pendingDocumentSaveFileName = res.suggestedFileName) }
                }
                is SaveToDeviceResult.Error -> {
                    _uiState.update { it.copy(errorMessage = res.message) }
                }
            }
        }
    }

    fun completeDocumentPickerSave(context: Context, destUri: Uri) {
        val path = _uiState.value.exportedMp3Path
        _uiState.update { it.copy(pendingDocumentSaveFileName = null) }
        if (path.isBlank()) return
        viewModelScope.launch {
            val ok = AudioFileShareHelper.copyAudioToUri(context, path, destUri)
            _uiState.update {
                it.copy(
                    feedbackMessage = if (ok) "MP3 saved to selected device folder." else null,
                    errorMessage = if (!ok) "Unable to write MP3 to selected destination." else null
                )
            }
        }
    }

    fun dismissPendingDocumentSave() {
        _uiState.update { it.copy(pendingDocumentSaveFileName = null) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
