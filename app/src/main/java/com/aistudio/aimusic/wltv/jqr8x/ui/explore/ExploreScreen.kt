package com.aistudio.aimusic.wltv.jqr8x.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.player.LocalMusicPlayer
import com.aistudio.aimusic.wltv.jqr8x.ui.library.SongActionBottomSheet
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExploreScreen(
    repositoryOverride: MusicRepository? = null,
    onReuseSongInStudio: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var songForActionsId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        repository.getTrendingSongs().collectLatest {
            songs = it
            isLoading = false
        }
    }

    val songForActions = songs.firstOrNull { it.id == songForActionsId }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Explore Music",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No public tracks yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Publish a song from My Projects using Edit Song Details to share it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    ExploreSongCard(
                        song = song,
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
}

@Composable
fun ExploreSongCard(song: Song, onMoreClick: () -> Unit) {
    val player = LocalMusicPlayer.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                player?.playSong(song.audioUrl, song.title)
            },
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (song.isDemo) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                "DEMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = song.genre,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
