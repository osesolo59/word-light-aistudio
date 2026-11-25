package com.aistudio.aimusic.wltv.jqr8x.ui.explore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.library.SongActionBottomSheet
import com.aistudio.aimusic.wltv.jqr8x.ui.library.SongListItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onNavigateBack: (() -> Unit)? = null,
    repositoryOverride: MusicRepository? = null,
    onReuseSongInStudio: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var songForActionsId by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (onNavigateBack != null) {
        BackHandler(onBack = onNavigateBack)
    }

    LaunchedEffect(repository) {
        repository.getFavoriteSongs().collectLatest {
            songs = it
            isLoading = false
        }
    }

    val songForActions = songs.firstOrNull { it.id == songForActionsId }

    Column(modifier = Modifier.fillMaxSize()) {
        if (onNavigateBack != null) {
            TopAppBar(
                title = { Text("Favorites", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("favorites_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        } else {
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (songs.isEmpty()) {
            EmptyFavorites()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        onToggleFavorite = {
                            scope.launch {
                                runCatching { repository.toggleFavorite(song.id, !song.isFavorite) }
                                    .onFailure {
                                        actionError = it.localizedMessage ?: "Unable to update favorite status."
                                    }
                            }
                        },
                        onMoreClick = { songForActionsId = song.id }
                    )
                }
            }
        }
    }

    if (songForActions != null) {
        SongActionBottomSheet(
            song = songForActions,
            onDismiss = { songForActionsId = null },
            onDeleted = { songForActionsId = null },
            repositoryOverride = repository,
            onReuseInStudio = onReuseSongInStudio
        )
    }

    if (actionError != null) {
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text("Notice") },
            text = { Text(actionError!!) },
            confirmButton = {
                TextButton(onClick = { actionError = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun EmptyFavorites() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No favorites yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Songs you heart in My Projects will appear here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

