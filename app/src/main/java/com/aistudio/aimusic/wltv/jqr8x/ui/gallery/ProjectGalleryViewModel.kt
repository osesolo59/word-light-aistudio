package com.aistudio.aimusic.wltv.jqr8x.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectStatus
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectType
import com.aistudio.aimusic.wltv.jqr8x.data.model.ProjectCoverTheme
import com.aistudio.aimusic.wltv.jqr8x.data.repository.CreativeProjectInput
import com.aistudio.aimusic.wltv.jqr8x.data.repository.ProjectGalleryDataSource
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProjectGallerySortOrder(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    FAVORITES_FIRST("Favorites First"),
    TITLE_AZ("Title A–Z")
}

sealed interface ProjectGalleryUiState {
    data object Loading : ProjectGalleryUiState
    data class Success(
        val allProjects: List<CreativeProject>,
        val filteredProjects: List<CreativeProject>,
        val availableGenres: List<String>,
        val searchQuery: String,
        val selectedGenre: String?,
        val selectedType: CreativeProjectType?,
        val favoritesOnly: Boolean,
        val sortOrder: ProjectGallerySortOrder
    ) : ProjectGalleryUiState
    data class Error(val message: String) : ProjectGalleryUiState
}

data class ProjectGalleryFilterState(
    val searchQuery: String = "",
    val selectedGenre: String? = null,
    val selectedType: CreativeProjectType? = null,
    val favoritesOnly: Boolean = false,
    val sortOrder: ProjectGallerySortOrder = ProjectGallerySortOrder.NEWEST_FIRST
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectGalleryViewModel(
    private val repository: ProjectGalleryDataSource,
    private val currentUserId: String
) : ViewModel() {

    private val filterState = MutableStateFlow(ProjectGalleryFilterState())
    private val retryTrigger = MutableStateFlow(0)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val uiState: StateFlow<ProjectGalleryUiState> = retryTrigger
        .flatMapLatest {
            repository.observeProjects(currentUserId)
                .map<List<CreativeProject>, Result<List<CreativeProject>>> { Result.success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(Result.failure(firestoreError ?: error))
                }
        }
        .combine(filterState) { result, filters ->
            result.fold(
                onSuccess = { projects ->
                    val genres = projects.mapNotNull { project ->
                        project.genre.trim().takeIf { it.isNotEmpty() }
                    }.distinct().sorted()

                    val query = filters.searchQuery.trim().lowercase()
                    val filtered = projects.filter { project ->
                        val matchesQuery = query.isEmpty() ||
                            project.title.lowercase().contains(query) ||
                            project.genre.lowercase().contains(query) ||
                            project.description.lowercase().contains(query) ||
                            project.language.lowercase().contains(query)
                        val matchesGenre = filters.selectedGenre == null ||
                            project.genre.equals(filters.selectedGenre, ignoreCase = true)
                        val matchesType = filters.selectedType == null ||
                            CreativeProjectType.fromKey(project.projectType) == filters.selectedType
                        val matchesFav = !filters.favoritesOnly || project.favorite
                        matchesQuery && matchesGenre && matchesType && matchesFav
                    }

                    val sorted = when (filters.sortOrder) {
                        ProjectGallerySortOrder.NEWEST_FIRST -> filtered.sortedByDescending {
                            (it.createdAt ?: it.updatedAt)?.toDate()?.time ?: Long.MAX_VALUE
                        }
                        ProjectGallerySortOrder.OLDEST_FIRST -> filtered.sortedBy {
                            (it.createdAt ?: it.updatedAt)?.toDate()?.time ?: Long.MAX_VALUE
                        }
                        ProjectGallerySortOrder.FAVORITES_FIRST -> filtered.sortedWith(
                            compareByDescending<CreativeProject> { it.favorite }
                                .thenByDescending {
                                    (it.createdAt ?: it.updatedAt)?.toDate()?.time ?: Long.MAX_VALUE
                                }
                        )
                        ProjectGallerySortOrder.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
                    }

                    ProjectGalleryUiState.Success(
                        allProjects = projects,
                        filteredProjects = sorted,
                        availableGenres = genres,
                        searchQuery = filters.searchQuery,
                        selectedGenre = filters.selectedGenre,
                        selectedType = filters.selectedType,
                        favoritesOnly = filters.favoritesOnly,
                        sortOrder = filters.sortOrder
                    )
                },
                onFailure = { error ->
                    ProjectGalleryUiState.Error(
                        message = error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Unable to load creative projects right now."
                    )
                }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProjectGalleryUiState.Loading
        )

    fun setSearchQuery(query: String) {
        filterState.update { it.copy(searchQuery = query) }
    }

    fun selectGenre(genre: String?) {
        filterState.update { state ->
            state.copy(selectedGenre = if (state.selectedGenre == genre) null else genre)
        }
    }

    fun selectProjectType(type: CreativeProjectType?) {
        filterState.update { state ->
            state.copy(selectedType = if (state.selectedType == type) null else type)
        }
    }

    fun toggleFavoritesOnly() {
        filterState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun setSortOrder(order: ProjectGallerySortOrder) {
        filterState.update { it.copy(sortOrder = order) }
    }

    fun clearFilters() {
        filterState.value = ProjectGalleryFilterState()
    }

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    fun dismissFeedback() {
        _feedbackMessage.value = null
    }

    fun saveNewProject(
        title: String,
        genre: String,
        projectType: CreativeProjectType,
        language: String,
        description: String,
        status: CreativeProjectStatus,
        coverTheme: ProjectCoverTheme,
        favorite: Boolean = false,
        linkedSourceCollection: String = "",
        linkedSourceId: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createProject(
                CreativeProjectInput(
                    title = title,
                    genre = genre,
                    projectType = projectType,
                    language = language,
                    description = description,
                    status = status,
                    favorite = favorite,
                    coverTheme = coverTheme,
                    linkedSourceCollection = linkedSourceCollection,
                    linkedSourceId = linkedSourceId
                )
            )
            _isSaving.value = false
            result.fold(
                onSuccess = { created ->
                    _feedbackMessage.value = "Saved \"${created.title}\" to Project Gallery."
                    onComplete(true)
                },
                onFailure = { error ->
                    _feedbackMessage.value = error.localizedMessage ?: "Failed to save creative project."
                    onComplete(false)
                }
            )
        }
    }

    fun updateExistingProject(
        projectId: String,
        title: String,
        genre: String,
        projectType: CreativeProjectType,
        language: String,
        description: String,
        status: CreativeProjectStatus,
        coverTheme: ProjectCoverTheme,
        favorite: Boolean,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateProject(
                projectId = projectId,
                input = CreativeProjectInput(
                    title = title,
                    genre = genre,
                    projectType = projectType,
                    language = language,
                    description = description,
                    status = status,
                    favorite = favorite,
                    coverTheme = coverTheme
                )
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _feedbackMessage.value = "Updated \"${title.trim()}\"."
                    onComplete(true)
                },
                onFailure = { error ->
                    _feedbackMessage.value = error.localizedMessage ?: "Failed to update project."
                    onComplete(false)
                }
            )
        }
    }

    fun toggleFavorite(project: CreativeProject) {
        viewModelScope.launch {
            repository.toggleFavorite(project.id, !project.favorite)
                .onFailure { error ->
                    _feedbackMessage.value = error.localizedMessage ?: "Unable to update favorite status."
                }
        }
    }

    fun deleteProject(project: CreativeProject, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteProject(project.id)
                .onSuccess {
                    _feedbackMessage.value = "Removed \"${project.title}\" from Project Gallery."
                    onComplete()
                }
                .onFailure { error ->
                    _feedbackMessage.value = error.localizedMessage ?: "Unable to delete project."
                }
        }
    }
}
