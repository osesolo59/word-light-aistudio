package com.aistudio.aimusic.wltv.jqr8x.ui.create

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiFailureReason
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiGenerationException
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsRequest
import com.aistudio.aimusic.wltv.jqr8x.data.ai.FirebaseAiTextGenerator
import com.aistudio.aimusic.wltv.jqr8x.data.ai.FreeSparkModels
import com.aistudio.aimusic.wltv.jqr8x.data.ai.LyricsStreamEvent
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface LyricsUiState {
    object Idle : LyricsUiState
    object Loading : LyricsUiState
    data class Streaming(
        val partialLyrics: String,
        val modelName: String = FreeSparkModels.DEFAULT_FREE_MODEL
    ) : LyricsUiState
    data class Success(
        val lyrics: String,
        val title: String = "",
        val isDemo: Boolean = false,
        val modelName: String = FreeSparkModels.DEFAULT_FREE_MODEL,
        val scriptureReference: String = "",
        val captionCues: List<TimedBilingualCue> = emptyList()
    ) : LyricsUiState
    data class Saved(val lyricId: String, val title: String) : LyricsUiState
    data class Error(
        val message: String,
        val reason: AiFailureReason? = null,
        val diagnosticCode: String = ""
    ) : LyricsUiState
}

fun lyricsErrorHeadline(reason: AiFailureReason?, diagnosticCode: String = ""): String =
    when {
        diagnosticCode == "APP_CHECK_TOKEN_MISSING" -> "App Check Token Missing"
        diagnosticCode == "APP_CHECK_ATTESTATION_FAILED" -> "App Check Attestation Failed"
        diagnosticCode in setOf(
            "FIREBASE_PERMISSION_DENIED",
            "SERVICE_DISABLED",
            "ANDROID_KEY_OR_APP_RESTRICTED"
        ) || reason == AiFailureReason.PERMISSION_OR_APP_CHECK -> "Firebase Permission Denied"
        reason == AiFailureReason.NETWORK_UNAVAILABLE -> "Network Unavailable"
        else -> "Gemini Generation Failure"
    }

class LyricsViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    fun generateLyrics(
        topic: String,
        genre: String,
        mood: String,
        language: String = "English"
    ) {
        generateLyrics(
            request = AiLyricsRequest(
                topic = topic,
                genre = genre,
                mood = mood,
                language = language
            )
        )
    }

    fun generateLyrics(request: AiLyricsRequest) {
        viewModelScope.launch {
            _uiState.value = LyricsUiState.Loading
            try {
                withTimeout(65_000L) {
                    var completed = false
                    repository.generateAiLyricsStream(request)
                        .collect { event ->
                            when (event) {
                                is LyricsStreamEvent.Chunk -> {
                                    if (event.partialText.isNotBlank()) {
                                        _uiState.value = LyricsUiState.Streaming(
                                            partialLyrics = event.partialText,
                                            modelName = repository.activeAiModelName()
                                        )
                                    }
                                }
                                is LyricsStreamEvent.Completed -> {
                                    completed = true
                                    val result = event.result
                                    if (result.lyrics.isNotBlank()) {
                                        _uiState.value = LyricsUiState.Success(
                                            lyrics = result.lyrics,
                                            title = result.title,
                                            isDemo = result.isDemo,
                                            modelName = result.modelName,
                                            scriptureReference = result.scriptureReference,
                                            captionCues = result.captionCues
                                        )
                                    } else {
                                        _uiState.value = LyricsUiState.Error(
                                            message = "Gemini returned empty lyrics. Please tap Retry.",
                                            reason = AiFailureReason.UNKNOWN
                                        )
                                    }
                                }
                            }
                        }
                    if (!completed) {
                        _uiState.value = LyricsUiState.Error(
                            message = "Gemini response was incomplete. Please tap Retry.",
                            reason = AiFailureReason.UNKNOWN
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiGenerationException) {
                Log.e("LyricsVM", "AI generation failed reason=${e.reason} code=${e.diagnosticCode} http=${e.httpStatus ?: "n/a"}")
                _uiState.value = LyricsUiState.Error(
                    message = e.message ?: "Gemini generation failure. Please tap Retry.",
                    reason = e.reason,
                    diagnosticCode = e.diagnosticCode
                )
            } catch (e: Exception) {
                val mapped = FirebaseAiTextGenerator.mapAiException(e)
                Log.e("LyricsVM", "Unexpected generation failure reason=${mapped.reason} code=${mapped.diagnosticCode}")
                _uiState.value = LyricsUiState.Error(
                    message = mapped.message ?: "Gemini generation failure. Please tap Retry.",
                    reason = mapped.reason,
                    diagnosticCode = mapped.diagnosticCode
                )
            }
        }
    }

    fun saveLyrics(
        title: String,
        lyrics: String,
        topic: String,
        genre: String,
        mood: String,
        language: String,
        scriptureReference: String = "",
        userInstructions: String = "",
        generationSource: String = "AI_GEMINI",
        sectionLanguages: Map<String, String> = emptyMap(),
        captionCues: List<TimedBilingualCue> = emptyList()
    ) {
        viewModelScope.launch {
            _uiState.value = LyricsUiState.Loading
            try {
                val id = withTimeout(15_000L) {
                    repository.saveLyrics(
                        title = title,
                        lyrics = lyrics,
                        topic = topic,
                        genre = genre,
                        mood = mood,
                        language = language,
                        scriptureReference = scriptureReference,
                        userInstructions = userInstructions,
                        generationSource = generationSource,
                        sectionLanguages = sectionLanguages,
                        captionCues = captionCues
                    )
                }
                _uiState.value = LyricsUiState.Saved(id, title.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LyricsVM", "Saving lyrics failed", e)
                _uiState.value = LyricsUiState.Error(
                    message = e.localizedMessage ?: "Failed to save lyrics."
                )
            }
        }
    }

    fun reset() {
        _uiState.value = LyricsUiState.Idle
    }
}
