package com.aistudio.aimusic.wltv.jqr8x.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.aistudio.aimusic.wltv.jqr8x.ui.home.HomeScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.library.LibraryScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.profile.ProfileScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.create.AiLyricsGeneratorScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.create.CreateScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.explore.ExploreScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.explore.FavoritesScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.player.MusicPlayerManager
import com.aistudio.aimusic.wltv.jqr8x.ui.player.MiniPlayer
import com.aistudio.aimusic.wltv.jqr8x.ui.player.LocalMusicPlayer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectType
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.data.repository.ProjectGalleryDataSource
import com.aistudio.aimusic.wltv.jqr8x.data.repository.ProjectGalleryRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.gallery.ProjectGalleryScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.gallery.ProjectGalleryViewModel
import com.aistudio.aimusic.wltv.jqr8x.ui.ideas.AiIdeaGeneratorScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.ideas.IdeaGeneratorViewModel
import com.aistudio.aimusic.wltv.jqr8x.ui.studio.StudioSelectionScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.studio.VideoStudioScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.studio.MusicVideoCreatorScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.mixer.AudioMixerScreen
import android.net.Uri
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingRepository
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinksRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.connect.ConnectWithUsScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.publish.ConnectedChannelsScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.publish.YouTubeUploadScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.profile.SettingsScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.profile.EditProfileScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.player.ExpandedPlayer
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.StateFlow

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    object Explore : Screen("explore", "Explore", Icons.Outlined.Explore, Icons.Filled.Explore)
    object IdeaGenerator : Screen("idea_generator", "AI Ideas", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome)
    object LyricsGenerator : Screen("lyrics_generator", "AI Lyrics", Icons.Outlined.EditNote, Icons.Filled.EditNote)
    object AudioStudio : Screen("audio_studio", "Audio Studio", Icons.Outlined.MusicNote, Icons.Filled.MusicNote)
    object AudioMixer : Screen("audio_mixer", "Gospel Mixer", Icons.Outlined.GraphicEq, Icons.Filled.GraphicEq)
    object VideoStudio : Screen("video_studio", "Video Studio", Icons.Outlined.VideoSettings, Icons.Filled.VideoSettings)
    object MusicVideoCreator : Screen("music_video_creator", "Video Creator", Icons.Outlined.Movie, Icons.Filled.Movie)
    object Studio : Screen("studio", "Studio", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome)
    object MyProjects : Screen("my_projects", "Projects", Icons.Outlined.Folder, Icons.Filled.Folder)
    object Profile : Screen("profile", "Profile", Icons.Outlined.Person, Icons.Filled.Person)
    object Favorites : Screen("favorites", "Favorites", Icons.Outlined.Favorite, Icons.Filled.Favorite)
    object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
    object EditProfile : Screen("edit_profile", "Edit Profile", Icons.Outlined.Edit, Icons.Filled.Edit)
    object ConnectWithUs : Screen("connect_with_us", "Connect With Us", Icons.Outlined.Public, Icons.Filled.Public)
    object ConnectedChannels : Screen("connected_channels", "Direct Publishing", Icons.Outlined.CloudUpload, Icons.Filled.CloudUpload)
    object YouTubePublish : Screen("youtube_publish", "YouTube Publish", Icons.Outlined.CloudUpload, Icons.Filled.CloudUpload)
    object ProjectGallery : Screen("project_gallery", "Project Gallery", Icons.Outlined.AutoAwesomeMosaic, Icons.Filled.AutoAwesomeMosaic)
}

val items = listOf(
    Screen.Home,
    Screen.Explore,
    Screen.Studio,
    Screen.MyProjects,
    Screen.Profile
)

@Composable
fun AppNavigation(
    uid: String,
    repositoryOverride: MusicRepository? = null,
    playerManagerOverride: MusicPlayerManager? = null,
    pendingOAuthUriFlow: StateFlow<Uri?>? = null,
    onConsumeOAuthUri: () -> Unit = {},
    projectGalleryRepositoryOverride: ProjectGalleryDataSource? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val playerManager = remember(playerManagerOverride) { playerManagerOverride ?: MusicPlayerManager(context) }
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val ideaGeneratorViewModel: IdeaGeneratorViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return IdeaGeneratorViewModel(repository) as T
            }
        }
    )

    val socialLinksRepository = remember(context) { SocialLinksRepository.getInstance(context) }
    val publishingRepository = remember(context) { PublishingRepository.getInstance(context) }
    val pendingOAuthUri by (pendingOAuthUriFlow?.collectAsState() ?: remember { mutableStateOf<Uri?>(null) })

    LaunchedEffect(pendingOAuthUri) {
        val uri = pendingOAuthUri ?: return@LaunchedEffect
        publishingRepository.handleOAuthRedirectUri(uri)
        onConsumeOAuthUri()
    }

    val navigateToIdeaGenerator: () -> Unit = {
        navController.navigate(Screen.IdeaGenerator.route) {
            launchSingleTop = true
        }
    }

    val navigateToLyricsGenerator: () -> Unit = {
        navController.navigate(Screen.LyricsGenerator.route) {
            launchSingleTop = true
        }
    }

    val navigateToAudioMixer: () -> Unit = {
        navController.navigate(Screen.AudioMixer.route) {
            launchSingleTop = true
        }
    }

    val navigateToConnectWithUs: () -> Unit = {
        navController.navigate(Screen.ConnectWithUs.route) {
            launchSingleTop = true
        }
    }

    val navigateToConnectedChannels: () -> Unit = {
        navController.navigate(Screen.ConnectedChannels.route) {
            launchSingleTop = true
        }
    }

    val navigateToProjectGallery: () -> Unit = {
        navController.navigate(Screen.ProjectGallery.route) {
            launchSingleTop = true
        }
    }

    val handleUseIdea: (CreativeIdea) -> Unit = { idea ->
        val draft = ideaGeneratorViewModel.prepareTransferToStudio(idea)
        val destinationRoute = when (draft.idea.targetStudio) {
            "VIDEO" -> Screen.VideoStudio.route
            "MUSIC_VIDEO" -> Screen.MusicVideoCreator.route
            else -> Screen.AudioStudio.route
        }
        navController.navigate(destinationRoute) {
            launchSingleTop = true
        }
    }

    val handleReuseSongInStudio: (Song) -> Unit = { song ->
        val idea = CreativeIdea(
            id = "reuse_song_${song.id}",
            title = song.title,
            summary = song.prompt,
            topic = song.title,
            genre = song.genre.ifBlank { "Gospel" },
            mood = song.mood.ifBlank { "Uplifting" },
            language = song.language.ifBlank { "English" },
            tempo = song.tempo.ifBlank { "Moderate (95 BPM)" },
            instruments = song.instruments,
            vocalStyle = song.vocalStyle.ifBlank { "Gospel Choir" },
            lyricsPreview = song.lyrics,
            targetStudio = "AUDIO",
            isDemo = song.isDemo
        )
        ideaGeneratorViewModel.prepareTransferToStudio(idea)
        navController.navigate(Screen.AudioStudio.route) {
            launchSingleTop = true
        }
    }

    val handleReuseLyricInStudio: (Lyric) -> Unit = { lyric ->
        val idea = CreativeIdea(
            id = "reuse_lyric_${lyric.id}",
            title = lyric.title,
            summary = lyric.topic.ifBlank { lyric.title },
            topic = lyric.topic.ifBlank { lyric.title },
            genre = lyric.genre.ifBlank { "Worship" },
            mood = lyric.mood.ifBlank { "Uplifting" },
            language = lyric.language.ifBlank { "English" },
            lyricsPreview = lyric.lyrics,
            bibleVerse = lyric.scriptureReference,
            targetStudio = "LYRICS",
            isDemo = lyric.generationSource != "AI_GEMINI"
        )
        ideaGeneratorViewModel.prepareTransferToStudio(idea)
        navController.navigate(Screen.AudioStudio.route) {
            launchSingleTop = true
        }
    }

    val currentSongTitle by playerManager.currentSongTitle.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val playbackError by playerManager.playbackError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExpandedPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(playbackError) {
        val message = playbackError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        playerManager.clearPlaybackError()
    }

    if (showExpandedPlayer && currentSongTitle != null) {
        BackHandler {
            showExpandedPlayer = false
        }
    }

    CompositionLocalProvider(LocalMusicPlayer provides playerManager) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                Column {
                    if (currentSongTitle != null) {
                        MiniPlayer(
                            title = currentSongTitle!!,
                            isPlaying = isPlaying,
                            onTogglePlay = {
                                if (isPlaying) playerManager.pause() else playerManager.resume()
                            },
                            onClick = { showExpandedPlayer = true },
                            onClose = {
                                showExpandedPlayer = false
                                playerManager.stop()
                            }
                        )
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        items.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = { Icon(if (selected) screen.selectedIcon else screen.icon, contentDescription = null) },
                                label = { Text(screen.label) },
                                selected = selected,
                                onClick = {
                                    val shouldRestoreTabState = screen.route != Screen.Studio.route
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = shouldRestoreTabState
                                        }
                                        launchSingleTop = true
                                        restoreState = shouldRestoreTabState
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToAudio = { navController.navigate(Screen.AudioStudio.route) },
                        onNavigateToVideo = { navController.navigate(Screen.VideoStudio.route) },
                        onNavigateToCreator = { navController.navigate(Screen.MusicVideoCreator.route) },
                        onNavigateToIdeaGenerator = navigateToIdeaGenerator,
                        onNavigateToLyricsGenerator = navigateToLyricsGenerator,
                        repositoryOverride = repository,
                        ideaGeneratorViewModel = ideaGeneratorViewModel,
                        onUseIdea = handleUseIdea,
                        onNavigateToAudioMixer = navigateToAudioMixer,
                        onNavigateToConnectWithUs = navigateToConnectWithUs,
                        socialLinksRepositoryOverride = socialLinksRepository,
                        onNavigateToProjectGallery = navigateToProjectGallery
                    )
                }
                composable(Screen.Explore.route) {
                    ExploreScreen(
                        repositoryOverride = repository,
                        onReuseSongInStudio = handleReuseSongInStudio
                    )
                }

                composable(Screen.Studio.route) {
                    StudioSelectionScreen(
                        onNavigateToAudio = { navController.navigate(Screen.AudioStudio.route) },
                        onNavigateToVideo = { navController.navigate(Screen.VideoStudio.route) },
                        onNavigateToCreator = { navController.navigate(Screen.MusicVideoCreator.route) },
                        onNavigateToIdeaGenerator = navigateToIdeaGenerator,
                        onNavigateToLyricsGenerator = navigateToLyricsGenerator,
                        repositoryOverride = repository,
                        ideaGeneratorViewModel = ideaGeneratorViewModel,
                        onUseIdea = handleUseIdea,
                        onNavigateToAudioMixer = navigateToAudioMixer,
                        onNavigateToProjectGallery = navigateToProjectGallery
                    )
                }

                composable(Screen.IdeaGenerator.route) {
                    AiIdeaGeneratorScreen(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Home.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onUseIdea = handleUseIdea,
                        ideaGeneratorViewModel = ideaGeneratorViewModel,
                        repositoryOverride = repository
                    )
                }

                composable(Screen.LyricsGenerator.route) {
                    AiLyricsGeneratorScreen(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Home.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onUseLyricsInAudioStudio = handleUseIdea,
                        repositoryOverride = repository
                    )
                }

                composable(Screen.AudioStudio.route) {
                    CreateScreen(
                        onNavigateToLibrary = { navController.navigate(Screen.MyProjects.route) },
                        repositoryOverride = repository,
                        ideaDraftFlow = ideaGeneratorViewModel.studioDraft,
                        onConsumeIdeaDraft = ideaGeneratorViewModel::consumeStudioDraft,
                        onNavigateToAudioMixer = navigateToAudioMixer
                    )
                }

                composable(Screen.AudioMixer.route) {
                    AudioMixerScreen(
                        onNavigateToMusicVideoCreator = { title, language, localMp3Uri, lyrics, captionCues ->
                            ideaGeneratorViewModel.prepareAudioMixTransfer(
                                title = title,
                                language = language,
                                localMp3Uri = localMp3Uri,
                                lyrics = lyrics,
                                captionCues = captionCues
                            )
                            navController.navigate(Screen.MusicVideoCreator.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToProjects = {
                            navController.navigate(Screen.MyProjects.route) {
                                launchSingleTop = true
                            }
                        },
                        repositoryOverride = repository,
                        reopenMixFlow = ideaGeneratorViewModel.reopenMixProject,
                        onConsumeReopenMix = ideaGeneratorViewModel::consumeMixToReopen
                    )
                }

                composable(Screen.VideoStudio.route) {
                    VideoStudioScreen(
                        onNavigateToProjects = { navController.navigate(Screen.MyProjects.route) },
                        repositoryOverride = repository,
                        ideaDraftFlow = ideaGeneratorViewModel.studioDraft,
                        onConsumeIdeaDraft = ideaGeneratorViewModel::consumeStudioDraft,
                        onNavigateToConnectedChannels = navigateToConnectedChannels,
                        publishingRepositoryOverride = publishingRepository
                    )
                }

                composable(Screen.MusicVideoCreator.route) {
                    MusicVideoCreatorScreen(
                        onNavigateToProjects = { navController.navigate(Screen.MyProjects.route) },
                        repositoryOverride = repository,
                        ideaDraftFlow = ideaGeneratorViewModel.studioDraft,
                        onConsumeIdeaDraft = ideaGeneratorViewModel::consumeStudioDraft,
                        mixDraftFlow = ideaGeneratorViewModel.audioMixDraft,
                        onConsumeMixDraft = ideaGeneratorViewModel::consumeAudioMixDraft,
                        onNavigateToConnectedChannels = navigateToConnectedChannels,
                        publishingRepositoryOverride = publishingRepository
                    )
                }

                composable(Screen.MyProjects.route) {
                    LibraryScreen(
                        onNavigateToFavorites = { navController.navigate(Screen.Favorites.route) },
                        repositoryOverride = repository,
                        onReuseSongInStudio = handleReuseSongInStudio,
                        onReuseLyricInStudio = handleReuseLyricInStudio,
                        onReopenMixInStudio = { mix ->
                            ideaGeneratorViewModel.setMixToReopen(mix)
                            navigateToAudioMixer()
                        },
                        onUseMixInMusicVideo = { mix ->
                            val uriStr = if (mix.localMp3Path.isNotBlank()) {
                                android.net.Uri.fromFile(java.io.File(mix.localMp3Path.removePrefix("file://"))).toString()
                            } else {
                                ""
                            }
                            ideaGeneratorViewModel.prepareAudioMixTransfer(
                                title = mix.title,
                                language = mix.language,
                                localMp3Uri = uriStr,
                                lyrics = mix.lyrics,
                                captionCues = mix.captionCues
                            )
                            navController.navigate(Screen.MusicVideoCreator.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToConnectedChannels = navigateToConnectedChannels,
                        publishingRepositoryOverride = publishingRepository,
                        onNavigateToProjectGallery = navigateToProjectGallery
                    )
                }
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) },
                        repositoryOverride = repository
                    )
                }
                composable(Screen.Favorites.route) {
                    FavoritesScreen(
                        onNavigateBack = { navController.popBackStack() },
                        repositoryOverride = repository,
                        onReuseSongInStudio = handleReuseSongInStudio
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) },
                        repositoryOverride = repository,
                        onNavigateToConnectWithUs = navigateToConnectWithUs,
                        socialLinksRepositoryOverride = socialLinksRepository,
                        onNavigateToConnectedChannels = navigateToConnectedChannels,
                        publishingRepositoryOverride = publishingRepository
                    )
                }

                composable(Screen.EditProfile.route) {
                    EditProfileScreen(
                        onNavigateBack = { navController.popBackStack() },
                        repositoryOverride = repository
                    )
                }

                composable(Screen.ConnectWithUs.route) {
                    ConnectWithUsScreen(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Home.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        socialLinksRepositoryOverride = socialLinksRepository
                    )
                }

                composable(Screen.ConnectedChannels.route) {
                    ConnectedChannelsScreen(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Settings.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        publishingRepositoryOverride = publishingRepository,
                        onOpenYouTubePublisher = {
                            navController.navigate(Screen.YouTubePublish.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(Screen.YouTubePublish.route) {
                    YouTubeUploadScreen(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.ConnectedChannels.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenConnectedChannels = navigateToConnectedChannels,
                        publishingRepositoryOverride = publishingRepository
                    )
                }

                composable(Screen.ProjectGallery.route) {
                    val projectGalleryRepository = remember(projectGalleryRepositoryOverride, context) {
                        projectGalleryRepositoryOverride ?: ProjectGalleryRepository(context)
                    }
                    val galleryViewModel: ProjectGalleryViewModel = viewModel(
                        key = "project_gallery_$uid",
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                return ProjectGalleryViewModel(projectGalleryRepository, uid) as T
                            }
                        }
                    )
                    ProjectGalleryScreen(
                        viewModel = galleryViewModel,
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.MyProjects.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenProjectInStudio = { type ->
                            val destination = when (type) {
                                CreativeProjectType.SONG -> Screen.AudioStudio.route
                                CreativeProjectType.LYRICS -> Screen.LyricsGenerator.route
                                CreativeProjectType.AUDIO_MIX -> Screen.AudioMixer.route
                                CreativeProjectType.VIDEO -> Screen.VideoStudio.route
                                CreativeProjectType.MUSIC_VIDEO -> Screen.MusicVideoCreator.route
                                CreativeProjectType.MINISTRY -> Screen.Home.route
                            }
                            navController.navigate(destination) {
                                launchSingleTop = true
                            }
                        },
                        musicRepositoryOverride = repository
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showExpandedPlayer && currentSongTitle != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessLow)
            )
        ) {
            ExpandedPlayer(
                title = currentSongTitle ?: "",
                isPlaying = isPlaying,
                onTogglePlay = {
                    if (isPlaying) playerManager.pause() else playerManager.resume()
                },
                onDismiss = { showExpandedPlayer = false }
            )
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            playerManager.release()
        }
    }
}
