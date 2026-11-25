package com.aistudio.aimusic.wltv.jqr8x.ui.create

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MusicUiState {
    object Idle : MusicUiState
    object Generating : MusicUiState
    data class Success(val song: Song) : MusicUiState
    data class Error(val message: String) : MusicUiState
}

class MusicViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<MusicUiState>(MusicUiState.Idle)
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    fun generateMusic(
        title: String,
        prompt: String,
        lyrics: String,
        genre: String,
        isShortClip: Boolean = false,
        language: String = "English",
        mood: String = "",
        tempo: String = "",
        instruments: List<String> = emptyList(),
        vocalStyle: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = MusicUiState.Generating
            try {
                val modelId = if (isShortClip) "demo-clip" else "demo-full"
                val taskId = java.util.UUID.randomUUID().toString()

                val song = repository.generateMusicViaBackend(
                    title = title,
                    prompt = prompt,
                    lyrics = lyrics,
                    genre = genre,
                    modelId = modelId,
                    taskId = taskId,
                    language = language,
                    mood = mood,
                    tempo = tempo,
                    instruments = instruments,
                    vocalStyle = vocalStyle
                )

                _uiState.value = MusicUiState.Success(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicVM", "Music generation failed", e)
                _uiState.value = MusicUiState.Error(e.localizedMessage ?: "Music generation failed.")
            }
        }
    }

    fun resetState() {
        _uiState.value = MusicUiState.Idle
    }
}
