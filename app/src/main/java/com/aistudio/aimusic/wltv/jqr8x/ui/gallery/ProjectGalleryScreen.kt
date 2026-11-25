package com.aistudio.aimusic.wltv.jqr8x.ui.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.aimusic.wltv.jqr8x.R
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectStatus
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectType
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.model.MusicVideoProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.ProjectCoverTheme
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoProject
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectGalleryScreen(
    viewModel: ProjectGalleryViewModel,
    onNavigateBack: () -> Unit,
    onOpenProjectInStudio: ((CreativeProjectType) -> Unit)? = null,
    musicRepositoryOverride: MusicRepository? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateOrEditDialog by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<CreativeProject?>(null) }
    var importCandidate by remember { mutableStateOf<StudioImportCandidate?>(null) }
    var detailProject by remember { mutableStateOf<CreativeProject?>(null) }
    var deleteCandidate by remember { mutableStateOf<CreativeProject?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var studioSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var studioLyrics by remember { mutableStateOf<List<Lyric>>(emptyList()) }
    var studioMixes by remember { mutableStateOf<List<AudioMixProject>>(emptyList()) }
    var studioVideos by remember { mutableStateOf<List<VideoProject>>(emptyList()) }
    var studioMusicVideos by remember { mutableStateOf<List<MusicVideoProject>>(emptyList()) }

    val musicRepository = remember(musicRepositoryOverride, context) {
        musicRepositoryOverride ?: MusicRepository(context)
    }

    LaunchedEffect(showImportDialog, musicRepository) {
        if (showImportDialog) {
            launch { musicRepository.getMySongs().collectLatest { studioSongs = it } }
            launch { musicRepository.getMyLyrics().collectLatest { studioLyrics = it } }
            launch { musicRepository.getMyAudioMixes().collectLatest { studioMixes = it } }
            launch { musicRepository.getMyVideos().collectLatest { studioVideos = it } }
            launch { musicRepository.getMyMusicVideos().collectLatest { studioMusicVideos = it } }
        }
    }

    LaunchedEffect(feedbackMessage) {
        val message = feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissFeedback()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("project_gallery_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.project_gallery_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.project_gallery_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("gallery_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("gallery_import_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Import project from Studio"
                        )
                    }
                    IconButton(
                        onClick = {
                            editingProject = null
                            importCandidate = null
                            showCreateOrEditDialog = true
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("gallery_new_project_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New creative project"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.secondary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProject = null
                    importCandidate = null
                    showCreateOrEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.testTag("gallery_fab_new_project")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Project")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ProjectGalleryUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("gallery_loading_state"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading creative projects...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ProjectGalleryUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("gallery_error_state"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.retry() },
                            modifier = Modifier.testTag("gallery_retry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }

                is ProjectGalleryUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        GallerySummaryBanner(
                            totalProjects = state.allProjects.size,
                            favoritesCount = state.allProjects.count { it.favorite },
                            genreCount = state.availableGenres.size
                        )

                        GalleryFilterBar(
                            state = state,
                            sortMenuExpanded = sortMenuExpanded,
                            onSortMenuToggle = { sortMenuExpanded = it },
                            onSearchChange = viewModel::setSearchQuery,
                            onSelectGenre = viewModel::selectGenre,
                            onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
                            onSelectSortOrder = {
                                viewModel.setSortOrder(it)
                                sortMenuExpanded = false
                            },
                            onClearFilters = viewModel::clearFilters
                        )

                        if (state.allProjects.isEmpty()) {
                            GalleryEmptyState(
                                hasActiveFilters = false,
                                onAddProject = {
                                    editingProject = null
                                    importCandidate = null
                                    showCreateOrEditDialog = true
                                },
                                onImportFromStudio = { showImportDialog = true },
                                onClearFilters = viewModel::clearFilters
                            )
                        } else if (state.filteredProjects.isEmpty()) {
                            GalleryEmptyState(
                                hasActiveFilters = true,
                                onAddProject = {
                                    editingProject = null
                                    importCandidate = null
                                    showCreateOrEditDialog = true
                                },
                                onImportFromStudio = { showImportDialog = true },
                                onClearFilters = viewModel::clearFilters
                            )
                        } else {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val columns = when {
                                    maxWidth >= 840.dp -> 3
                                    maxWidth >= 560.dp -> 2
                                    else -> 1
                                }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columns),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 96.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("gallery_projects_grid")
                                ) {
                                    items(
                                        items = state.filteredProjects,
                                        key = { it.id }
                                    ) { project ->
                                        CreativeProjectGalleryCard(
                                            project = project,
                                            onClick = { detailProject = project },
                                            onToggleFavorite = { viewModel.toggleFavorite(project) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (detailProject != null) {
        val current = detailProject!!
        val latest = (uiState as? ProjectGalleryUiState.Success)
            ?.allProjects
            ?.firstOrNull { it.id == current.id }
            ?: current
        ProjectDetailDialog(
            project = latest,
            onDismiss = { detailProject = null },
            onEdit = {
                editingProject = latest
                importCandidate = null
                detailProject = null
                showCreateOrEditDialog = true
            },
            onDeleteRequest = {
                deleteCandidate = latest
                detailProject = null
            },
            onToggleFavorite = { viewModel.toggleFavorite(latest) },
            onOpenInStudio = onOpenProjectInStudio?.let { opener ->
                { type ->
                    detailProject = null
                    opener(type)
                }
            }
        )
    }

    if (showCreateOrEditDialog) {
        ProjectEditorDialog(
            initialProject = editingProject,
            initialCandidate = importCandidate,
            isSaving = isSaving,
            onDismiss = {
                showCreateOrEditDialog = false
                editingProject = null
                importCandidate = null
            },
            onSave = { title, genre, type, language, description, status, coverTheme, favorite, sourceCol, sourceId ->
                val targetEdit = editingProject
                if (targetEdit != null) {
                    viewModel.updateExistingProject(
                        projectId = targetEdit.id,
                        title = title,
                        genre = genre,
                        projectType = type,
                        language = language,
                        description = description,
                        status = status,
                        coverTheme = coverTheme,
                        favorite = favorite
                    ) { success ->
                        if (success) {
                            showCreateOrEditDialog = false
                            editingProject = null
                        }
                    }
                } else {
                    viewModel.saveNewProject(
                        title = title,
                        genre = genre,
                        projectType = type,
                        language = language,
                        description = description,
                        status = status,
                        coverTheme = coverTheme,
                        favorite = favorite,
                        linkedSourceCollection = sourceCol,
                        linkedSourceId = sourceId
                    ) { success ->
                        if (success) {
                            showCreateOrEditDialog = false
                            importCandidate = null
                        }
                    }
                }
            }
        )
    }

    if (showImportDialog) {
        ImportFromStudioDialog(
            songs = studioSongs,
            lyrics = studioLyrics,
            audioMixes = studioMixes,
            videos = studioVideos,
            musicVideos = studioMusicVideos,
            onDismiss = { showImportDialog = false },
            onSelectCandidate = { candidate ->
                showImportDialog = false
                editingProject = null
                importCandidate = candidate
                showCreateOrEditDialog = true
            }
        )
    }

    if (deleteCandidate != null) {
        val target = deleteCandidate!!
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete Project") },
            text = { Text("Remove \"${target.title}\" from your Project Gallery?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(target) {
                            deleteCandidate = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("gallery_confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GallerySummaryBanner(
    totalProjects: Int,
    favoritesCount: Int,
    genreCount: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryStatItem(label = "Projects", value = totalProjects.toString())
            SummaryStatItem(label = "Genres", value = genreCount.toString())
            SummaryStatItem(label = "Favorites", value = favoritesCount.toString())
        }
    }
}

@Composable
private fun SummaryStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GalleryFilterBar(
    state: ProjectGalleryUiState.Success,
    sortMenuExpanded: Boolean,
    onSortMenuToggle: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSelectSortOrder: (ProjectGallerySortOrder) -> Unit,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search title, genre, language...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("gallery_search_input")
            )

            Box {
                OutlinedButton(
                    onClick = { onSortMenuToggle(true) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("gallery_sort_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort projects",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { onSortMenuToggle(false) }
                ) {
                    ProjectGallerySortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            onClick = { onSelectSortOrder(order) },
                            modifier = Modifier.testTag("gallery_sort_option_${order.name.lowercase()}")
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.selectedGenre == null && !state.favoritesOnly,
                onClick = onClearFilters,
                label = { Text("All") },
                modifier = Modifier.testTag("gallery_genre_chip_all")
            )
            FilterChip(
                selected = state.favoritesOnly,
                onClick = onToggleFavoritesOnly,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Favorites") },
                modifier = Modifier.testTag("gallery_filter_favorites")
            )
            state.availableGenres.forEach { genre ->
                FilterChip(
                    selected = state.selectedGenre.equals(genre, ignoreCase = true),
                    onClick = { onSelectGenre(genre) },
                    label = { Text(genre) },
                    modifier = Modifier.testTag("gallery_genre_chip_${genre.lowercase()}")
                )
            }
        }
    }
}

@Composable
fun CreativeProjectGalleryCard(
    project: CreativeProject,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val type = CreativeProjectType.fromKey(project.projectType)
    val status = CreativeProjectStatus.fromKey(project.status)
    val cover = ProjectCoverTheme.fromKey(project.coverTheme)
    val formattedDate = formatCreationDate(project.createdAt ?: project.updatedAt)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gallery_project_card_${project.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(coverThemeGradient(cover)))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = projectTypeIcon(type),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("gallery_favorite_toggle_${project.id}")
                    ) {
                        Icon(
                            imageVector = if (project.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (project.favorite) "Unfavorite project" else "Favorite project",
                            tint = if (project.favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = project.title.ifBlank { "Untitled Project" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = project.genre.ifBlank { "Gospel" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = project.language.ifBlank { "English" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (project.description.isNotBlank()) {
                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Created $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryEmptyState(
    hasActiveFilters: Boolean,
    onAddProject: () -> Unit,
    onImportFromStudio: () -> Unit,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("gallery_empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesomeMosaic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (hasActiveFilters) {
                "No projects match your current filters"
            } else {
                "Your Creative Project Gallery is empty"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasActiveFilters) {
                "Try clearing search or genre filters to view all saved projects."
            } else {
                "Save songs, bilingual Twi/English lyrics, Gospel mixes, or ministry videos with genre & creation date metadata."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        if (hasActiveFilters) {
            Button(
                onClick = onClearFilters,
                modifier = Modifier.testTag("gallery_clear_filters_button")
            ) {
                Text("Clear Filters")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImportFromStudio) {
                    Text("Import from Studio")
                }
                Button(
                    onClick = onAddProject,
                    modifier = Modifier.testTag("gallery_empty_add_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Project")
                }
            }
        }
    }
}
