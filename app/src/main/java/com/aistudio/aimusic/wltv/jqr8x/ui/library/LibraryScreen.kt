package com.aistudio.aimusic.wltv.jqr8x.ui.library

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.net.Uri
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.media.AudioFileShareHelper
import com.aistudio.aimusic.wltv.jqr8x.data.media.SaveToDeviceResult
import com.aistudio.aimusic.wltv.jqr8x.data.media.StudioMediaHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.model.MusicVideoProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.Playlist
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoProject
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingRepository
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.player.LocalMusicPlayer
import com.aistudio.aimusic.wltv.jqr8x.ui.publish.DirectPublishDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

private sealed interface DeleteTarget {
    data class Video(val item: VideoProject) : DeleteTarget
    data class MusicVideo(val item: MusicVideoProject) : DeleteTarget
    data class SavedLyric(val item: Lyric) : DeleteTarget
    data class SavedPlaylist(val item: Playlist) : DeleteTarget
    data class SavedAudioMix(val item: AudioMixProject) : DeleteTarget
}

@Composable
fun LibraryScreen(
    onNavigateToFavorites: () -> Unit,
    repositoryOverride: MusicRepository? = null,
    onReuseSongInStudio: ((Song) -> Unit)? = null,
    onReuseLyricInStudio: ((Lyric) -> Unit)? = null,
    onReopenMixInStudio: ((AudioMixProject) -> Unit)? = null,
    onUseMixInMusicVideo: ((AudioMixProject) -> Unit)? = null,
    onNavigateToConnectedChannels: () -> Unit = {},
    publishingRepositoryOverride: PublishingRepository? = null,
    onNavigateToProjectGallery: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val playerManager = LocalMusicPlayer.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Music", "Videos", "Creator", "Lyrics", "Playlists", "Mixes")

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var videos by remember { mutableStateOf<List<VideoProject>>(emptyList()) }
    var musicVideos by remember { mutableStateOf<List<MusicVideoProject>>(emptyList()) }
    var savedLyrics by remember { mutableStateOf<List<Lyric>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var audioMixes by remember { mutableStateOf<List<AudioMixProject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    var songForActionsId by remember { mutableStateOf<String?>(null) }
    var selectedLyricForView by remember { mutableStateOf<Lyric?>(null) }
    var openLyricInEditMode by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var pendingMixSavePath by remember { mutableStateOf("") }

    val saveMixDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/*")
    ) { destUri ->
        val path = pendingMixSavePath
        pendingMixSavePath = ""
        if (destUri != null && path.isNotBlank()) {
            scope.launch {
                val ok = AudioFileShareHelper.copyAudioToUri(context, path, destUri)
                actionFeedback = if (ok) "Saved audio mix to selected device folder." else "Unable to save audio mix to device."
            }
        }
    }

    var previewTitle by remember { mutableStateOf<String?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var previewSubtitle by remember { mutableStateOf<String?>(null) }
    var previewLocalPath by remember { mutableStateOf("") }

    LaunchedEffect(repository) {
        launch {
            repository.getMySongs().collectLatest {
                songs = it
                if (selectedTab == 0) isLoading = false
            }
        }
        launch {
            repository.getMyVideos().collectLatest {
                videos = it
            }
        }
        launch {
            repository.getMyMusicVideos().collectLatest {
                musicVideos = it
            }
        }
        launch {
            repository.getMyLyrics().collectLatest {
                savedLyrics = it
            }
        }
        launch {
            repository.getMyPlaylists().collectLatest {
                playlists = it
            }
        }
        launch {
            repository.getMyAudioMixes().collectLatest {
                audioMixes = it
            }
        }
        kotlinx.coroutines.delay(500)
        isLoading = false
    }

    val songForActions = songs.firstOrNull { it.id == songForActionsId }
    val songsById = remember(songs) { songs.associateBy { it.id } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Projects",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToProjectGallery,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("library_open_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesomeMosaic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery")
                }
                IconButton(
                    onClick = onNavigateToFavorites,
                    modifier = Modifier.testTag("library_favorites_button")
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Favorites", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag("library_tab_${title.lowercase()}"),
                    text = { Text(title) }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            when (selectedTab) {
                0 -> {
                    if (songs.isEmpty()) EmptyLibrary("No songs yet")
                    else {
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
                                                    actionFeedback = it.localizedMessage ?: "Unable to update favorite status."
                                                }
                                        }
                                    },
                                    onMoreClick = { songForActionsId = song.id }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (videos.isEmpty()) EmptyLibrary("No videos yet")
                    else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(videos, key = { it.id }) { video ->
                                VideoListItem(
                                    video = video,
                                    onClick = {
                                        previewTitle = video.title.ifBlank { "Untitled Video" }
                                        previewSubtitle = buildString {
                                            append("Template: ${video.templateName}")
                                            if (video.photoCount > 0) {
                                                append(" • ${video.photoCount} photos • ${video.aspectRatio}")
                                            }
                                        }
                                        previewLocalPath = video.localVideoPath
                                        previewUrl = video.videoUrl
                                    },
                                    onDeleteClick = { deleteTarget = DeleteTarget.Video(video) }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    if (musicVideos.isEmpty()) EmptyLibrary("No music videos yet")
                    else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(musicVideos, key = { it.id }) { mv ->
                                MusicVideoListItem(
                                    mv = mv,
                                    onClick = {
                                        previewTitle = mv.title.ifBlank { "Untitled Creator Project" }
                                        previewSubtitle = buildString {
                                            append(mv.bibleVerse.ifBlank { "Music Video Preview" })
                                            if (mv.photoCount > 0) {
                                                append(" • ${mv.photoCount} photos • ${mv.aspectRatio}")
                                            }
                                        }
                                        previewLocalPath = mv.localVideoPath
                                        previewUrl = mv.videoUrl
                                    },
                                    onDeleteClick = { deleteTarget = DeleteTarget.MusicVideo(mv) }
                                )
                            }
                        }
                    }
                }
                3 -> {
                    if (savedLyrics.isEmpty()) EmptyLibrary("No saved lyrics yet")
                    else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(savedLyrics, key = { it.id }) { lyric ->
                                SavedLyricListItem(
                                    lyric = lyric,
                                    onViewClick = {
                                        openLyricInEditMode = false
                                        selectedLyricForView = lyric
                                    },
                                    onEditClick = {
                                        openLyricInEditMode = true
                                        selectedLyricForView = lyric
                                    },
                                    onCopyClick = {
                                        clipboardManager.setText(AnnotatedString(formatLyricForShare(lyric)))
                                        actionFeedback = "Copied \"${lyric.title.ifBlank { "Lyrics" }}\" to clipboard."
                                    },
                                    onShareClick = {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, lyric.title)
                                            putExtra(Intent.EXTRA_TEXT, formatLyricForShare(lyric))
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Lyrics"))
                                        }.onFailure {
                                            actionFeedback = "No share target app was found on this device."
                                        }
                                    },
                                    onReuseClick = onReuseLyricInStudio?.let { handler -> { handler(lyric) } },
                                    onDeleteClick = { deleteTarget = DeleteTarget.SavedLyric(lyric) }
                                )
                            }
                        }
                    }
                }
                4 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${playlists.size} Playlist${if (playlists.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            OutlinedButton(
                                onClick = { showCreatePlaylistDialog = true },
                                modifier = Modifier.testTag("library_new_playlist_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Playlist")
                            }
                        }
                        if (playlists.isEmpty()) {
                            EmptyLibrary("No playlists yet")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(playlists, key = { it.id }) { playlist ->
                                    PlaylistCardItem(
                                        playlist = playlist,
                                        songsById = songsById,
                                        onPlaySong = { song ->
                                            playerManager?.playSong(song.audioUrl, song.title)
                                        },
                                        onRemoveSong = { songId ->
                                            scope.launch {
                                                runCatching { repository.removeSongFromPlaylist(playlist.id, songId) }
                                                    .onFailure {
                                                        actionFeedback = it.localizedMessage ?: "Unable to remove song from playlist."
                                                    }
                                            }
                                        },
                                        onDeletePlaylist = { deleteTarget = DeleteTarget.SavedPlaylist(playlist) }
                                    )
                                }
                            }
                        }
                    }
                }
                5 -> {
                    AudioMixesTabContent(
                        mixes = audioMixes,
                        onPlayMix = { mix ->
                            val cleanPath = mix.localMp3Path.removePrefix("file://").trim()
                            if (cleanPath.isNotEmpty() && File(cleanPath).exists()) {
                                val fileUri = Uri.fromFile(File(cleanPath)).toString()
                                playerManager?.playSong(fileUri, mix.title.ifBlank { "Gospel Mix" })
                            } else {
                                actionFeedback = "Reopen this mix in the Gospel Mixer and export an MP3 first."
                            }
                        },
                        onReopenInMixer = { mix ->
                            onReopenMixInStudio?.invoke(mix)
                                ?: run { actionFeedback = "Open the English & Twi Gospel Mixer from Studio to edit." }
                        },
                        onUseInMusicVideo = { mix ->
                            onUseMixInMusicVideo?.invoke(mix)
                                ?: run { actionFeedback = "Open Music Video Creator from Studio to attach this mix." }
                        },
                        onSaveMixToDevice = { mix ->
                            scope.launch {
                                when (val res = AudioFileShareHelper.saveMixedMp3ToDevice(context, mix.localMp3Path, mix.title)) {
                                    is SaveToDeviceResult.SavedToMediaStore -> {
                                        actionFeedback = "Saved MP3 to ${res.displayPath}"
                                    }
                                    is SaveToDeviceResult.RequiresDocumentPicker -> {
                                        pendingMixSavePath = mix.localMp3Path
                                        saveMixDocLauncher.launch(res.suggestedFileName)
                                    }
                                    is SaveToDeviceResult.Error -> {
                                        actionFeedback = res.message
                                    }
                                }
                            }
                        },
                        onShareMix = { mix ->
                            val share = AudioFileShareHelper.createShareAudioIntent(context, mix.localMp3Path, mix.title)
                            if (share != null) {
                                runCatching {
                                    context.startActivity(Intent.createChooser(share, "Share Mixed MP3"))
                                }.onFailure {
                                    actionFeedback = "No share target app found on device."
                                }
                            } else {
                                actionFeedback = "Local MP3 file not found. Reopen mix and export MP3 again."
                            }
                        },
                        onDeleteMix = { mix ->
                            deleteTarget = DeleteTarget.SavedAudioMix(mix)
                        }
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

    if (previewUrl != null && previewTitle != null) {
        VideoPreviewDialog(
            title = previewTitle!!,
            subtitle = previewSubtitle ?: "",
            videoUrl = previewUrl!!,
            localVideoPath = previewLocalPath,
            onNavigateToConnectedChannels = onNavigateToConnectedChannels,
            publishingRepositoryOverride = publishingRepositoryOverride,
            onDismiss = {
                previewUrl = null
                previewTitle = null
                previewSubtitle = null
                previewLocalPath = ""
            }
        )
    }

    if (selectedLyricForView != null) {
        val lyric = selectedLyricForView!!
        LyricDetailAndEditDialog(
            lyric = lyric,
            initialEditMode = openLyricInEditMode,
            onDismiss = {
                selectedLyricForView = null
                openLyricInEditMode = false
            },
            onCopy = { currentLyric ->
                clipboardManager.setText(AnnotatedString(formatLyricForShare(currentLyric)))
                actionFeedback = "Copied \"${currentLyric.title.ifBlank { "Lyrics" }}\" to clipboard."
            },
            onReuseInStudio = onReuseLyricInStudio?.let { handler ->
                { currentLyric ->
                    selectedLyricForView = null
                    openLyricInEditMode = false
                    handler(currentLyric)
                }
            },
            onSaveEdits = { updated ->
                repository.updateLyrics(
                    lyricId = updated.id,
                    title = updated.title,
                    lyrics = updated.lyrics,
                    topic = updated.topic,
                    genre = updated.genre,
                    mood = updated.mood,
                    language = updated.language,
                    scriptureReference = updated.scriptureReference,
                    userInstructions = updated.userInstructions,
                    generationSource = updated.generationSource,
                    sectionLanguages = updated.sectionLanguages,
                    captionCues = updated.captionCues
                )
                selectedLyricForView = updated
                openLyricInEditMode = false
                actionFeedback = "Updated \"${updated.title}\"."
            }
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        val (dialogTitle, dialogBody) = when (target) {
            is DeleteTarget.Video -> "Delete Video Project" to "Delete '${target.item.title.ifBlank { "Untitled Video" }}' from your projects?"
            is DeleteTarget.MusicVideo -> "Delete Music Video" to "Delete '${target.item.title.ifBlank { "Untitled Creator Project" }}' from your projects?"
            is DeleteTarget.SavedLyric -> "Delete Saved Lyrics" to "Delete '${target.item.title.ifBlank { "Untitled Lyrics" }}' from your library?"
            is DeleteTarget.SavedPlaylist -> "Delete Playlist" to "Delete playlist '${target.item.name}'? Songs remain in your Music tab."
            is DeleteTarget.SavedAudioMix -> "Delete Gospel Mix" to "Delete '${target.item.title.ifBlank { "Untitled Gospel Mix" }}' from your projects?"
        }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(dialogTitle) },
            text = { Text(dialogBody) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                when (target) {
                                    is DeleteTarget.Video -> {
                                        repository.deleteVideo(target.item.id)
                                        deleteLocalExportIfOwned(context, target.item.localVideoPath)
                                    }
                                    is DeleteTarget.MusicVideo -> {
                                        repository.deleteMusicVideo(target.item.id)
                                        deleteLocalExportIfOwned(context, target.item.localVideoPath)
                                    }
                                    is DeleteTarget.SavedLyric -> repository.deleteLyrics(target.item.id)
                                    is DeleteTarget.SavedPlaylist -> repository.deletePlaylist(target.item.id)
                                    is DeleteTarget.SavedAudioMix -> {
                                        repository.deleteAudioMix(target.item.id)
                                        AudioFileShareHelper.deleteOwnedMixedAudio(context, target.item.localMp3Path)
                                    }
                                }
                            }.onSuccess {
                                deleteTarget = null
                            }.onFailure {
                                deleteTarget = null
                                actionFeedback = it.localizedMessage ?: "Unable to delete project."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            runCatching { repository.createPlaylist(newName) }
                                .onSuccess { showCreatePlaylistDialog = false }
                                .onFailure {
                                    showCreatePlaylistDialog = false
                                    actionFeedback = it.localizedMessage ?: "Unable to create playlist."
                                }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (actionFeedback != null) {
        AlertDialog(
            onDismissRequest = { actionFeedback = null },
            title = { Text("Notice") },
            text = { Text(actionFeedback!!) },
            confirmButton = {
                TextButton(onClick = { actionFeedback = null }) {
                    Text("OK")
                }
            }
        )
    }
}

private fun deleteLocalExportIfOwned(context: Context, localVideoPath: String) {
    val cleanPath = localVideoPath.removePrefix("file://").trim()
    if (cleanPath.isEmpty()) return
    runCatching {
        val exportDir = File(context.filesDir, "exported_videos").canonicalFile
        val target = File(cleanPath).canonicalFile
        if (target.path.startsWith(exportDir.path + File.separator) && target.exists()) {
            target.delete()
        }
    }
}

private fun formatLyricForShare(lyric: Lyric): String = buildString {
    appendLine(lyric.title.ifBlank { "Word & Light Lyrics" })
    val meta = listOf(lyric.genre, lyric.language, lyric.mood)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
    if (meta.isNotBlank()) appendLine(meta)
    if (lyric.scriptureReference.isNotBlank()) {
        appendLine("Scripture Reference: ${lyric.scriptureReference}")
    }
    appendLine()
    append(lyric.lyrics)
}

@Composable
fun VideoPreviewDialog(
    title: String,
    subtitle: String,
    videoUrl: String,
    localVideoPath: String = "",
    onNavigateToConnectedChannels: () -> Unit = {},
    publishingRepositoryOverride: PublishingRepository? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDirectPublishDialog by remember { mutableStateOf(false) }
    val hasLocalMp4 = remember(localVideoPath) {
        localVideoPath.isNotBlank() && File(localVideoPath.removePrefix("file://")).exists()
    }
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destUri ->
        if (destUri != null && hasLocalMp4) {
            scope.launch {
                val ok = StudioMediaHelper.copyVideoToUri(context, localVideoPath, destUri)
                Toast.makeText(
                    context,
                    if (ok) "Saved MP4 to device" else "Unable to save MP4",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (hasLocalMp4) "LOCAL PHOTO VIDEO MP4" else "DEMO VIDEO PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )
                if (hasLocalMp4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    when (val res = StudioMediaHelper.saveExportedVideoToDevice(context, localVideoPath, title)) {
                                        is SaveToDeviceResult.SavedToMediaStore -> Toast.makeText(
                                            context,
                                            "Saved to ${res.displayPath}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        is SaveToDeviceResult.RequiresDocumentPicker -> createDocLauncher.launch(res.suggestedFileName)
                                        is SaveToDeviceResult.Error -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                        Button(
                            onClick = {
                                val intent = StudioMediaHelper.createShareVideoIntent(context, localVideoPath, title)
                                if (intent != null) {
                                    context.startActivity(Intent.createChooser(intent, "Share Video"))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                    OutlinedButton(
                        onClick = { showDirectPublishDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_video_direct_publish_button")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Publish to YouTube / Facebook / TikTok")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (showDirectPublishDialog && hasLocalMp4) {
        DirectPublishDialog(
            initialTitle = title,
            initialDescription = subtitle,
            localVideoPath = localVideoPath,
            onDismiss = { showDirectPublishDialog = false },
            onOpenConnectedChannels = {
                showDirectPublishDialog = false
                onDismiss()
                onNavigateToConnectedChannels()
            },
            onFallbackShareClick = {
                val intent = StudioMediaHelper.createShareVideoIntent(context, localVideoPath, title)
                if (intent != null) {
                    context.startActivity(Intent.createChooser(intent, "Share Video"))
                }
            },
            publishingRepositoryOverride = publishingRepositoryOverride
        )
    }
}

@Composable
fun SongListItem(
    song: Song,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    val player = LocalMusicPlayer.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                player?.playSong(song.audioUrl, song.title)
            },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (song.isDemo) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                "DEMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = song.genre,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("song_favorite_button_${song.id}")
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.testTag("song_more_button_${song.id}")
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
            }
        }
    }
}

@Composable
fun VideoListItem(
    video: VideoProject,
    onClick: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.VideoSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title.ifBlank { "Untitled Video" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Template: ${video.templateName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            val isLocalMp4 = video.localVideoPath.isNotBlank() || video.photoCount > 0
            if (isLocalMp4) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        "MP4",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (video.isDemo) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        "DEMO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Preview Video",
                tint = MaterialTheme.colorScheme.primary
            )
            if (onDeleteClick != null) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_video_${video.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Video",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun MusicVideoListItem(
    mv: MusicVideoProject,
    onClick: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mv.title.ifBlank { "Untitled Creator Project" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (mv.bibleVerse.isNotBlank()) {
                    Text(
                        text = mv.bibleVerse,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                }
            }
            val isLocalMp4 = mv.localVideoPath.isNotBlank() || mv.photoCount > 0
            if (isLocalMp4) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        "MP4",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (mv.isDemo) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        "DEMO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Preview Music Video",
                tint = MaterialTheme.colorScheme.primary
            )
            if (onDeleteClick != null) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_music_video_${mv.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Music Video",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
internal fun LyricDetailAndEditDialog(
    lyric: Lyric,
    initialEditMode: Boolean,
    onDismiss: () -> Unit,
    onCopy: (Lyric) -> Unit,
    onReuseInStudio: ((Lyric) -> Unit)?,
    onSaveEdits: suspend (Lyric) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isEditing by remember(lyric.id, initialEditMode) { mutableStateOf(initialEditMode) }
    var editTitle by remember(lyric.id) { mutableStateOf(lyric.title) }
    var editGenre by remember(lyric.id) { mutableStateOf(lyric.genre) }
    var editMood by remember(lyric.id) { mutableStateOf(lyric.mood) }
    var editLanguage by remember(lyric.id) { mutableStateOf(lyric.language) }
    var editScripture by remember(lyric.id) { mutableStateOf(lyric.scriptureReference) }
    var editBody by remember(lyric.id) { mutableStateOf(lyric.lyrics) }
    var editCues by remember(lyric.id) { mutableStateOf(lyric.captionCues) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = if (isEditing) "Edit Saved Lyrics" else lyric.title.ifBlank { "Saved Lyrics" },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_lyric_edit_title")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editGenre,
                            onValueChange = { editGenre = it },
                            label = { Text("Genre") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("library_lyric_edit_genre")
                        )
                        OutlinedTextField(
                            value = editMood,
                            onValueChange = { editMood = it },
                            label = { Text("Mood") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("library_lyric_edit_mood")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editLanguage,
                            onValueChange = { editLanguage = it },
                            label = { Text("Language") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("library_lyric_edit_language")
                        )
                        OutlinedTextField(
                            value = editScripture,
                            onValueChange = { editScripture = it },
                            label = { Text("Scripture") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("library_lyric_edit_scripture")
                        )
                    }
                    OutlinedTextField(
                        value = editBody,
                        onValueChange = { editBody = it },
                        label = { Text("Lyrics") },
                        minLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_lyric_edit_body")
                    )
                    OutlinedButton(
                        onClick = {
                            editCues = BilingualGospelHelper.generateCuesFromLyrics(editBody)
                        },
                        enabled = editBody.isNotBlank() && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("library_lyric_edit_sync_cues_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Sync Bilingual Captions (${editCues.size} cues)")
                    }
                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("library_lyric_edit_error")
                        )
                    }
                } else {
                    Text(
                        text = listOf(lyric.genre, lyric.language, lyric.mood)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (lyric.captionCues.isNotEmpty()) {
                        Text(
                            text = "${lyric.captionCues.size} synchronized English/Twi caption cues",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (lyric.scriptureReference.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Scripture Reference: ${lyric.scriptureReference}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    Text(
                        text = lyric.lyrics,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("library_lyric_view_body")
                    )
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                Button(
                    enabled = editTitle.isNotBlank() && editBody.isNotBlank() && !isSaving,
                    onClick = {
                        errorText = null
                        isSaving = true
                        scope.launch {
                            runCatching {
                                val updated = lyric.copy(
                                    title = editTitle.trim(),
                                    genre = editGenre.trim(),
                                    mood = editMood.trim(),
                                    language = editLanguage.trim().ifBlank { "English" },
                                    scriptureReference = editScripture.trim(),
                                    lyrics = editBody.trim(),
                                    captionCues = editCues
                                )
                                onSaveEdits(updated)
                            }.onFailure { err ->
                                errorText = err.localizedMessage ?: "Failed to save lyric changes."
                            }
                            isSaving = false
                        }
                    },
                    modifier = Modifier.testTag("library_lyric_save_button")
                ) {
                    Text(if (isSaving) "Saving..." else "Save Changes")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.testTag("library_lyric_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                    OutlinedButton(onClick = { onCopy(lyric) }) {
                        Text("Copy")
                    }
                    if (onReuseInStudio != null) {
                        Button(onClick = { onReuseInStudio(lyric) }) {
                            Text("Studio")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    if (isEditing && !initialEditMode) {
                        isEditing = false
                    } else {
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("library_lyric_cancel_button")
            ) {
                Text(if (isEditing) "Cancel" else "Close")
            }
        }
    )
}

@Composable
private fun SavedLyricListItem(
    lyric: Lyric,
    onViewClick: () -> Unit,
    onEditClick: () -> Unit,
    onCopyClick: () -> Unit,
    onShareClick: () -> Unit,
    onReuseClick: (() -> Unit)?,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewClick)
            .testTag("saved_lyric_card_${lyric.id}"),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lyric.title.ifBlank { "Untitled Lyrics" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(lyric.genre, lyric.language, lyric.mood)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                val isAi = lyric.generationSource == "AI_GEMINI"
                Surface(
                    color = if (isAi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = if (isAi) "GEMINI AI" else "DEMO",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (lyric.scriptureReference.isNotBlank()) {
                Text(
                    text = "Scripture: ${lyric.scriptureReference}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = lyric.lyrics,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onReuseClick != null) {
                    TextButton(
                        onClick = onReuseClick,
                        modifier = Modifier.testTag("reuse_lyric_${lyric.id}")
                    ) {
                        Text("Edit in Studio")
                    }
                }
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.testTag("edit_lyric_${lyric.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Saved Lyrics")
                }
                IconButton(onClick = onCopyClick) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Lyrics")
                }
                IconButton(onClick = onShareClick) {
                    Icon(Icons.Default.Share, contentDescription = "Share Lyrics")
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_lyric_${lyric.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Lyrics", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PlaylistCardItem(
    playlist: Playlist,
    songsById: Map<String, Song>,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onDeletePlaylist: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name.ifBlank { "Untitled Playlist" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${playlist.songIds.size} track${if (playlist.songIds.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(
                    onClick = onDeletePlaylist,
                    modifier = Modifier.testTag("delete_playlist_${playlist.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (playlist.songIds.isEmpty()) {
                Text(
                    text = "No songs added yet. Tap the menu on any song to add it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HorizontalDivider()
                playlist.songIds.forEach { songId ->
                    val matchedSong = songsById[songId]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = matchedSong?.title?.ifBlank { "Untitled Song" } ?: "Track ($songId)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (matchedSong != null) {
                            IconButton(onClick = { onPlaySong(matchedSong) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play track", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { onRemoveSong(songId) }) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove from playlist")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyLibrary(message: String = "No projects yet") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your creative projects will appear here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
