package com.aistudio.aimusic.wltv.jqr8x.ui.ideas

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiGenerationException
import com.aistudio.aimusic.wltv.jqr8x.data.ai.FreeSparkModels
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.model.StudioAudioMixDraft
import com.aistudio.aimusic.wltv.jqr8x.data.model.StudioIdeaDraft
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class IdeaGeneratorUiState(
    val selectedCategory: String? = null,
    val selectedGenre: String? = null,
    val selectedMood: String? = null,
    val selectedLanguage: String = "English",
    val batchSeed: Int = 0,
    val useDemoMode: Boolean = false,
    val isGenerating: Boolean = false,
    val isLiveAiActive: Boolean = false,
    val activeModelLabel: String = FreeSparkModels.DEFAULT_FREE_MODEL,
    val generatedIdeas: List<CreativeIdea> = emptyList(),
    val savedIdeas: List<CreativeIdea> = emptyList(),
    val savingIdeaId: String? = null,
    val savedIdeaIds: Set<String> = emptySet(),
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class IdeaGeneratorViewModel(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdeaGeneratorUiState())
    val uiState: StateFlow<IdeaGeneratorUiState> = _uiState.asStateFlow()

    private val _studioDraft = MutableStateFlow<StudioIdeaDraft?>(null)
    val studioDraft: StateFlow<StudioIdeaDraft?> = _studioDraft.asStateFlow()

    private val _audioMixDraft = MutableStateFlow<StudioAudioMixDraft?>(null)
    val audioMixDraft: StateFlow<StudioAudioMixDraft?> = _audioMixDraft.asStateFlow()

    private val _reopenMixProject = MutableStateFlow<AudioMixProject?>(null)
    val reopenMixProject: StateFlow<AudioMixProject?> = _reopenMixProject.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMySavedIdeas().collectLatest { items ->
                _uiState.update { it.copy(savedIdeas = items) }
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { current ->
            val next = if (current.selectedCategory == category) null else category
            current.copy(selectedCategory = next)
        }
    }

    fun selectGenre(genre: String) {
        _uiState.update { current ->
            val next = if (current.selectedGenre == genre) null else genre
            current.copy(selectedGenre = next)
        }
    }

    fun selectMood(mood: String) {
        _uiState.update { current ->
            val next = if (current.selectedMood == mood) null else mood
            current.copy(selectedMood = next)
        }
    }

    fun selectLanguage(language: String) {
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun setDemoMode(useDemo: Boolean) {
        _uiState.update {
            it.copy(
                useDemoMode = useDemo,
                errorMessage = null,
                activeModelLabel = if (useDemo) "demo-local-templates" else FreeSparkModels.DEFAULT_FREE_MODEL
            )
        }
    }

    fun generateIdeas() {
        val state = _uiState.value
        val nextSeed = if (state.generatedIdeas.isEmpty()) state.batchSeed else state.batchSeed + 1
        loadIdeasBatch(nextSeed = nextSeed, forceDemo = state.useDemoMode, isMore = false)
    }

    fun generateMore() {
        val state = _uiState.value
        val nextSeed = state.batchSeed + 1
        loadIdeasBatch(nextSeed = nextSeed, forceDemo = state.useDemoMode, isMore = true)
    }

    fun generateDemoFallback() {
        val state = _uiState.value
        val nextSeed = if (state.generatedIdeas.isEmpty()) state.batchSeed else state.batchSeed + 1
        _uiState.update {
            it.copy(
                useDemoMode = true,
                errorMessage = null,
                activeModelLabel = "demo-local-templates"
            )
        }
        loadIdeasBatch(nextSeed = nextSeed, forceDemo = true, isMore = false)
    }

    private fun loadIdeasBatch(nextSeed: Int, forceDemo: Boolean, isMore: Boolean) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }
            try {
                val result = withTimeout(65_000L) {
                    repository.generateIdeasWithAi(
                        category = state.selectedCategory,
                        genre = state.selectedGenre,
                        mood = state.selectedMood,
                        language = state.selectedLanguage,
                        batchSeed = nextSeed,
                        useDemoMode = forceDemo
                    )
                }
                val modeTag = if (result.isDemo) "Free Demo Mode" else "Live AI • ${result.modelName}"
                val statusMsg = if (isMore) {
                    "Loaded ${result.ideas.size} fresh ${state.selectedLanguage} ideas ($modeTag)"
                } else {
                    "Generated ${result.ideas.size} ${state.selectedLanguage} creative ideas ($modeTag)"
                }
                _uiState.update {
                    it.copy(
                        batchSeed = nextSeed,
                        isGenerating = false,
                        isLiveAiActive = !result.isDemo,
                        activeModelLabel = result.modelName,
                        generatedIdeas = result.ideas,
                        feedbackMessage = statusMsg,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isGenerating = false) }
                throw e
            } catch (e: AiGenerationException) {
                Log.e("IdeaGenVM", "AI idea generation failed reason=${e.reason} code=${e.diagnosticCode} http=${e.httpStatus ?: "n/a"}")
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isLiveAiActive = false,
                        errorMessage = e.message ?: "Live AI ideas request failed. Tap below to use Free Demo Mode."
                    )
                }
            } catch (e: Exception) {
                val mapped = com.aistudio.aimusic.wltv.jqr8x.data.ai.FirebaseAiTextGenerator.mapAiException(e)
                Log.e("IdeaGenVM", "Unexpected idea generation failure reason=${mapped.reason} code=${mapped.diagnosticCode}")
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isLiveAiActive = false,
                        errorMessage = mapped.message ?: "Live AI ideas request failed. Tap below to use Free Demo Mode."
                    )
                }
            }
        }
    }

    fun saveIdea(idea: CreativeIdea) {
        viewModelScope.launch {
            _uiState.update { it.copy(savingIdeaId = idea.id) }
            try {
                repository.saveIdea(idea)
                _uiState.update { state ->
                    state.copy(
                        savingIdeaId = null,
                        savedIdeaIds = state.savedIdeaIds + idea.id,
                        feedbackMessage = "Saved \"${idea.title}\" to your Idea Vault"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        savingIdeaId = null,
                        feedbackMessage = e.localizedMessage ?: "Sign in required to save ideas"
                    )
                }
            }
        }
    }

    fun deleteSavedIdea(ideaId: String) {
        viewModelScope.launch {
            try {
                repository.deleteSavedIdea(ideaId)
                _uiState.update { it.copy(feedbackMessage = "Removed saved idea") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(feedbackMessage = e.localizedMessage ?: "Unable to delete idea") }
            }
        }
    }

    fun notifyCopied(idea: CreativeIdea) {
        _uiState.update { it.copy(feedbackMessage = "Copied \"${idea.title}\" to clipboard") }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun prepareTransferToStudio(idea: CreativeIdea): StudioIdeaDraft {
        val draft = StudioIdeaDraft(
            transferId = UUID.randomUUID().toString(),
            idea = idea
        )
        _studioDraft.value = draft
        return draft
    }

    fun consumeStudioDraft(transferId: String) {
        if (_studioDraft.value?.transferId == transferId) {
            _studioDraft.value = null
        }
    }

    fun prepareAudioMixTransfer(
        title: String,
        language: String,
        localMp3Uri: String,
        lyrics: String = "",
        captionCues: List<TimedBilingualCue> = emptyList()
    ): StudioAudioMixDraft {
        val draft = StudioAudioMixDraft(
            transferId = UUID.randomUUID().toString(),
            title = title,
            language = language,
            localMp3Uri = localMp3Uri,
            lyrics = lyrics,
            captionCues = captionCues
        )
        _audioMixDraft.value = draft
        return draft
    }

    fun consumeAudioMixDraft(transferId: String) {
        if (_audioMixDraft.value?.transferId == transferId) {
            _audioMixDraft.value = null
        }
    }

    fun setMixToReopen(mix: AudioMixProject) {
        _reopenMixProject.value = mix
    }

    fun consumeMixToReopen() {
        _reopenMixProject.value = null
    }
}
