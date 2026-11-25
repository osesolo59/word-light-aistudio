package com.aistudio.aimusic.wltv.jqr8x.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.aimusic.wltv.jqr8x.R
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.model.UserProfile
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.data.social.ConfiguredSocialLink
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinksRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.ideas.IdeaGeneratorViewModel
import com.aistudio.aimusic.wltv.jqr8x.ui.player.LocalMusicPlayer
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.BackgroundWhite
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.GoldAccent
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurpleDark
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurplePrimary
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(
    onNavigateToAudio: () -> Unit,
    onNavigateToVideo: () -> Unit,
    onNavigateToCreator: () -> Unit,
    onNavigateToIdeaGenerator: () -> Unit = {},
    onNavigateToLyricsGenerator: () -> Unit = {},
    repositoryOverride: MusicRepository? = null,
    ideaGeneratorViewModel: IdeaGeneratorViewModel? = null,
    onUseIdea: (CreativeIdea) -> Unit = {},
    onNavigateToAudioMixer: () -> Unit = {},
    onNavigateToConnectWithUs: () -> Unit = {},
    socialLinksRepositoryOverride: SocialLinksRepository? = null
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val socialLinksRepository = remember(socialLinksRepositoryOverride, context) {
        socialLinksRepositoryOverride ?: SocialLinksRepository.getInstance(context)
    }
    val configuredSocialLinks by socialLinksRepository.configuredLinksFlow.collectAsState()
    val homeViewModel: HomeViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, context) as T
        }
    })
    val resolvedIdeaViewModel: IdeaGeneratorViewModel = ideaGeneratorViewModel ?: viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return IdeaGeneratorViewModel(repository) as T
            }
        }
    )

    val ideaState by resolvedIdeaViewModel.uiState.collectAsState()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    
    LaunchedEffect(Unit) {
        repository.getUserProfile().collectLatest {
            profile = it
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_lazy_column"),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Free Demo Mode Banner
        if (MusicRepository.IS_DEMO_MODE) {
            item {
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(AI Backend Disabled)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Welcome and Credits
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, ${profile?.displayName?.split(" ")?.firstOrNull() ?: "there"}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (MusicRepository.IS_DEMO_MODE) "Explore Word & Light Studio" else "What will you create today?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                if (!MusicRepository.IS_DEMO_MODE) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Token, 
                                contentDescription = null, 
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${profile?.credits ?: 0}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item { HeroBanner() }
        
        item { Spacer(modifier = Modifier.height(24.dp)) }
        
        item {
            SectionHeader(title = "Studio Tools")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudioShortcutCard(
                    title = "Audio",
                    icon = Icons.Default.MusicNote,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToAudio
                )
                StudioShortcutCard(
                    title = "Video",
                    icon = Icons.Default.VideoSettings,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToVideo
                )
                StudioShortcutCard(
                    title = "Creator",
                    icon = Icons.Default.Movie,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToCreator
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudioShortcutCard(
                    title = "Gospel Mixer",
                    icon = Icons.Default.GraphicEq,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_studio_shortcut_mixer"),
                    onClick = onNavigateToAudioMixer
                )
                StudioShortcutCard(
                    title = "AI Ideas",
                    icon = Icons.Default.AutoAwesome,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_studio_shortcut_ai_ideas"),
                    onClick = onNavigateToIdeaGenerator
                )
                StudioShortcutCard(
                    title = "AI Lyrics",
                    icon = Icons.Default.Lyrics,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_studio_shortcut_lyrics"),
                    onClick = onNavigateToLyricsGenerator
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            AiIdeaGeneratorLaunchCard(
                savedIdeasCount = ideaState.savedIdeas.size,
                generatedCount = ideaState.generatedIdeas.size,
                onClick = onNavigateToIdeaGenerator
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            ConnectWithUsHomeShortcutCard(
                configuredLinks = configuredSocialLinks,
                onClick = onNavigateToConnectWithUs
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
        
        item {
            SectionHeader(title = "Featured Gospel")
            SongRow(songs = demoSongs.filter { it.genre == "Gospel" })
        }
        
        item { Spacer(modifier = Modifier.height(24.dp)) }
        
        item {
            SectionHeader(title = "Trending Now")
            SongRow(songs = demoSongs)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item { WordLightMinistryBannerCard() }
    }
}

@Composable
fun AiIdeaGeneratorLaunchCard(
    savedIdeasCount: Int,
    generatedCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("home_ai_idea_generator_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Idea Generator",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AI Idea Generator",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Song, Lyrics, Style, Worship, Reggae & Video Ideas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "FREE SPARK AI",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tap to generate 5 creative ideas in English, Twi, Akan, Ga, or Ewe using free Spark AI or offline demo templates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when {
                        generatedCount > 0 -> "$generatedCount ideas ready"
                        savedIdeasCount > 0 -> "$savedIdeasCount saved ideas"
                        else -> "7 categories • 5 languages"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = onClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_open_idea_generator_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Idea Generator", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            Image(
                painter = painterResource(id = R.drawable.hero_music_creation_1794264571321),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Word & Light AI STUDIO",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Word & Light TV • Christians Strong Foundation Ministry",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Psalm 119:105 — Your Word is a lamp to my feet and a light to my path",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun ConnectWithUsHomeShortcutCard(
    configuredLinks: List<ConfiguredSocialLink>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("home_connect_with_us_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(1.5.dp, GoldAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(PurplePrimary, PurplePrimary.copy(alpha = 0.90f))
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "CONNECT WITH US",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldAccent,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Word & Light TV Channels",
                    style = MaterialTheme.typography.titleMedium,
                    color = BackgroundWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (configuredLinks.isEmpty()) {
                        "Configure official ministry channels in Settings"
                    } else {
                        "${configuredLinks.size} verified channel(s): " +
                            configuredLinks.joinToString(", ") { it.platform.displayName }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = BackgroundWhite.copy(alpha = 0.9f)
                )
                if (configuredLinks.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        configuredLinks.take(5).forEach { link ->
                            Surface(
                                color = BackgroundWhite.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    painter = painterResource(link.platform.iconRes),
                                    contentDescription = link.platform.displayName,
                                    tint = GoldAccent,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = PurpleDark
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_open_connect_with_us_button")
            ) {
                Text("Connect", fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Connect With Us",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun WordLightMinistryBannerCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_ministry_branding_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "WORD & LIGHT TV",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Christians Strong Foundation Ministry",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Psalm 119:105 • \"Your word is a lamp for my feet, a light on my path.\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
            )
        }
    }
}

@Composable
fun StudioShortcutCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
fun SongRow(songs: List<DemoSong>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(songs) { song ->
            SongCard(song)
        }
    }
}

@Composable
fun SongCard(song: DemoSong) {
    val player = LocalMusicPlayer.current
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable {
                player?.playSong(song.url, song.title)
            }
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(48.dp).align(Alignment.Center),
                tint = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Text(
                    "DEMO",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text = song.genre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

data class DemoSong(val title: String, val genre: String, val url: String)
val demoSongs = listOf(
    DemoSong("Praise High", "Gospel", "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"),
    DemoSong("Deep Worship", "Worship", "https://storage.googleapis.com/exoplayer-test-media-0/babylon.mp3"),
    DemoSong("African Beats", "Afrobeat", "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"),
    DemoSong("Peaceful Soul", "R&B", "https://storage.googleapis.com/exoplayer-test-media-1/mp3/dizzy.mp3")
)
