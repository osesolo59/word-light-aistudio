package com.aistudio.aimusic.wltv.jqr8x.ui.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.ideas.IdeaGeneratorViewModel

@Composable
fun StudioSelectionScreen(
    onNavigateToAudio: () -> Unit,
    onNavigateToVideo: () -> Unit,
    onNavigateToCreator: () -> Unit,
    onNavigateToIdeaGenerator: () -> Unit = {},
    onNavigateToLyricsGenerator: () -> Unit = {},
    repositoryOverride: MusicRepository? = null,
    ideaGeneratorViewModel: IdeaGeneratorViewModel? = null,
    onUseIdea: (CreativeIdea) -> Unit = {},
    onNavigateToAudioMixer: () -> Unit = {},
    onNavigateToProjectGallery: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val resolvedIdeaViewModel: IdeaGeneratorViewModel = ideaGeneratorViewModel ?: viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return IdeaGeneratorViewModel(repository) as T
            }
        }
    )
    val ideaState by resolvedIdeaViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AI Studio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Word & Light TV • Christians Strong Foundation Ministry • Psalm 119:105",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (MusicRepository.IS_DEMO_MODE) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "FREE DEMO MODE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("hub_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                val ideaCountSuffix = if (ideaState.generatedIdeas.isNotEmpty()) {
                    " (${ideaState.generatedIdeas.size} ready)"
                } else {
                    ""
                }
                StudioOptionCard(
                    title = "AI Idea Generator",
                    description = "Generate 5 instant Song, Lyrics, Style, Worship, Reggae & Video ideas in English, Twi, Akan, Ga or Ewe$ideaCountSuffix.",
                    icon = Icons.Outlined.AutoAwesome,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("hub_ai_idea_generator_card"),
                    onClick = onNavigateToIdeaGenerator
                )
            }
            item {
                StudioOptionCard(
                    title = "AI Lyrics Generator",
                    description = "Generate structured song lyrics with Gemini AI from your topic, genre, and mood, then copy, save, or open in Audio Studio.",
                    icon = Icons.Outlined.EditNote,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("hub_ai_lyrics_generator_card"),
                    onClick = onNavigateToLyricsGenerator
                )
            }
            item {
                StudioOptionCard(
                    title = "Audio Studio",
                    description = "Compose gospel music, bilingual English & Twi lyrics, and vocal styles.",
                    icon = Icons.Outlined.MusicNote,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("hub_audio_studio_card"),
                    onClick = onNavigateToAudio
                )
            }
            item {
                StudioOptionCard(
                    title = "English & Twi Gospel Mixer",
                    description = "Mix English & Twi tracks, balance vocals & instrumentals, master with EQ/reverb & export WAV 44.1/48kHz or MP3 up to 320kbps.",
                    icon = Icons.Outlined.GraphicEq,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("hub_audio_mixer_card"),
                    onClick = onNavigateToAudioMixer
                )
            }
            item {
                StudioOptionCard(
                    title = "Video Studio",
                    description = "Create Full HD 1080p (or hardware-supported 4K) MP4 slideshows with English & Twi captions, opening slates & Word & Light TV watermark.",
                    icon = Icons.Outlined.VideoSettings,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("hub_video_studio_card"),
                    onClick = onNavigateToVideo
                )
            }
            item {
                StudioOptionCard(
                    title = "Music Video Creator",
                    description = "Combine mastered gospel audio with high-res photos, transitions, Bible verses & Word & Light TV closing credits.",
                    icon = Icons.Outlined.Movie,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag("hub_music_video_creator_card"),
                    onClick = onNavigateToCreator
                )
            }
            item {
                StudioOptionCard(
                    title = "Project Gallery",
                    description = "Store, search, filter by genre, and organize your creative projects with creation date metadata.",
                    icon = Icons.Outlined.AutoAwesomeMosaic,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("hub_project_gallery_card"),
                    onClick = onNavigateToProjectGallery
                )
            }
        }
    }
}

@Composable
fun StudioOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                color = color.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = color
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open $title",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
