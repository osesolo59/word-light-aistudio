package com.aistudio.aimusic.wltv.jqr8x.ui.create

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiFailureReason
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiGenerationException
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsRequest
import com.aistudio.aimusic.wltv.jqr8x.data.ai.FreeSparkModels
import com.aistudio.aimusic.wltv.jqr8x.data.ai.LyricsStreamEvent
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
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
        val isDemo: Boolean = true,
        val modelName: String = "demo-local-template",
        val scriptureReference: String = "",
        val captionCues: List<TimedBilingualCue> = emptyList()
    ) : LyricsUiState
    data class Saved(val lyricId: String, val title: String) : LyricsUiState
    data class Error(
        val message: String,
        val canFallbackToDemo: Boolean = true,
        val reason: AiFailureReason? = null
    ) : LyricsUiState
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
            ),
            useDemoMode = false
        )
    }

    fun generateLyrics(
        request: AiLyricsRequest,
        useDemoMode: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = LyricsUiState.Loading
            try {
                withTimeout(65_000L) {
                    var completed = false
                    repository.generateAiLyricsStream(request, useDemoMode = useDemoMode)
                        .collect { event ->
                            when (event) {
                                is LyricsStreamEvent.Chunk -> {
                                    if (event.partialText.isNotBlank()) {
                                        _uiState.value = LyricsUiState.Streaming(
                                            partialLyrics = event.partialText,
                                            modelName = if (useDemoMode) {
                                                "demo-local-template"
                                            } else {
                                                repository.activeAiModelName()
                                            }
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
                                            message = "Live AI returned empty lyrics. Retry or use Free Demo Mode instead.",
                                            canFallbackToDemo = true,
                                            reason = AiFailureReason.UNKNOWN
                                        )
                                    }
                                }
                            }
                        }
                    if (!completed) {
                        val current = _uiState.value
                        if (current is LyricsUiState.Streaming && current.partialLyrics.isNotBlank()) {
                            _uiState.value = LyricsUiState.Success(
                                lyrics = current.partialLyrics,
                                title = request.title.ifBlank { request.topic.ifBlank { "Song of Praise" } },
                                isDemo = useDemoMode,
                                modelName = current.modelName,
                                scriptureReference = request.scriptureReference,
                                captionCues = BilingualGospelHelper.generateCuesFromLyrics(current.partialLyrics)
                            )
                        } else if (current is LyricsUiState.Loading) {
                            _uiState.value = LyricsUiState.Error(
                                message = "Live AI returned empty lyrics. Retry or use Free Demo Mode instead.",
                                canFallbackToDemo = true,
                                reason = AiFailureReason.UNKNOWN
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiGenerationException) {
                Log.e("LyricsVM", "AI generation failed reason=${e.reason} code=${e.diagnosticCode} http=${e.httpStatus ?: "n/a"}")
                _uiState.value = LyricsUiState.Error(
                    message = e.message ?: "Live AI request failed. You can use Free Demo Mode instead.",
                    canFallbackToDemo = true,
                    reason = e.reason
                )
            } catch (e: Exception) {
                val mapped = com.aistudio.aimusic.wltv.jqr8x.data.ai.FirebaseAiTextGenerator.mapAiException(e)
                Log.e("LyricsVM", "Unexpected generation failure reason=${mapped.reason} code=${mapped.diagnosticCode}")
                _uiState.value = LyricsUiState.Error(
                    message = mapped.message ?: "Live AI request failed. You can use Free Demo Mode instead.",
                    canFallbackToDemo = true,
                    reason = mapped.reason
                )
            }
        }
    }

    fun generateDemoFallback(request: AiLyricsRequest) {
        val result = repository.buildDemoLyricsResult(request)
        _uiState.value = LyricsUiState.Success(
            lyrics = result.lyrics,
            title = result.title,
            isDemo = true,
            modelName = result.modelName,
            scriptureReference = result.scriptureReference,
            captionCues = result.captionCues
        )
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
        generationSource: String = "DEMO",
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
                    message = e.localizedMessage ?: "Failed to save lyrics.",
                    canFallbackToDemo = false
                )
            }
        }
    }

    fun reset() {
        _uiState.value = LyricsUiState.Idle
    }
}
