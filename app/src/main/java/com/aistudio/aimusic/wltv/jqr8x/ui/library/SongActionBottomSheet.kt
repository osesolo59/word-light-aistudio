package com.aistudio.aimusic.wltv.jqr8x.ui.library

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.model.Playlist
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.home.demoSongs
import com.aistudio.aimusic.wltv.jqr8x.ui.player.LocalMusicPlayer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit = {},
    repositoryOverride: MusicRepository? = null,
    onReuseInStudio: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val playerManager = LocalMusicPlayer.current
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnpublishDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showReuseDialog by remember { mutableStateOf(false) }
    var reportType by remember { mutableStateOf<String?>(null) }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Song Header
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = song.genre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn {
                item {
                    ActionItem(icon = Icons.Default.PlaylistAdd, label = "Add to Playlist") {
                        showPlaylistDialog = true
                    }
                }
                item {
                    ActionItem(
                        icon = if (song.likeStatus == 1) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                        label = "Like",
                        color = if (song.likeStatus == 1) MaterialTheme.colorScheme.primary else null
                    ) {
                        scope.launch {
                            runCatching { repository.updateSongLikeStatus(song.id, if (song.likeStatus == 1) 0 else 1) }
                                .onFailure {
                                    infoDialogTitle = "Permission Notice"
                                    infoDialogMessage = "Only the song owner can modify this track."
                                }
                        }
                    }
                }
                item {
                    ActionItem(
                        icon = if (song.likeStatus == -1) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                        label = "Dislike"
                    ) {
                        scope.launch {
                            runCatching { repository.updateSongLikeStatus(song.id, if (song.likeStatus == -1) 0 else -1) }
                                .onFailure {
                                    infoDialogTitle = "Permission Notice"
                                    infoDialogMessage = "Only the song owner can modify this track."
                                }
                        }
                    }
                }
                item {
                    ActionItem(icon = Icons.Default.Share, label = "Share Song") {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Listen to \"${song.title}\" (${song.genre}) created in Word & Light AI STUDIO."
                            )
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                        }.onFailure {
                            infoDialogTitle = "Share Song"
                            infoDialogMessage = "No share target app was found on this device."
                        }
                    }
                }
                item {
                    ActionItem(icon = Icons.Default.Edit, label = "Edit Song Details") { showEditDialog = true }
                }
                item {
                    ActionItem(icon = Icons.Default.Download, label = "Download Song (Demo)") {
                        infoDialogTitle = "Download Song (Demo-Only)"
                        infoDialogMessage = "Offline audio downloading is simulated in Free Demo Mode. No file was downloaded to your device."
                    }
                }
                item {
                    ActionItem(icon = Icons.Default.QueueMusic, label = "Add to Queue") {
                        playerManager?.addToQueue(song.audioUrl, song.title)
                        infoDialogTitle = "Added to Queue"
                        infoDialogMessage = "\"${song.title}\" has been added to your playback queue."
                    }
                }
                item {
                    ActionItem(icon = Icons.Default.ContentPaste, label = "Reuse Styles and Lyrics") {
                        showReuseDialog = true
                    }
                }
                item {
                    ActionItem(icon = Icons.Default.Transform, label = "Remix (Demo)") {
                        infoDialogTitle = "Remix (Demo-Only)"
                        infoDialogMessage = if (song.allowRemix) {
                            "AI audio remixing requires an eligible AI audio synthesis backend, which is not enabled on Firebase Spark."
                        } else {
                            "Remixing is currently disabled by the track owner."
                        }
                    }
                }
                item {
                    ActionItem(icon = Icons.Default.Mic, label = "Create Voice (Demo)") {
                        infoDialogTitle = "Create Voice (Demo-Only)"
                        infoDialogMessage = "Custom AI voice cloning is a demo-only preview and is disabled in Strict Free Demo Mode."
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    runCatching { repository.toggleAllowRemix(song.id, !song.allowRemix) }
                                        .onFailure {
                                            infoDialogTitle = "Update Failed"
                                            infoDialogMessage = it.localizedMessage ?: "Only the song owner can update remix settings."
                                        }
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "Allow Remix", modifier = Modifier.weight(1f))
                        Switch(
                            checked = song.allowRemix,
                            onCheckedChange = {
                                scope.launch {
                                    runCatching { repository.toggleAllowRemix(song.id, it) }
                                        .onFailure { err ->
                                            infoDialogTitle = "Update Failed"
                                            infoDialogMessage = err.localizedMessage ?: "Only the song owner can update remix settings."
                                        }
                                }
                            }
                        )
                    }
                }
                item {
                    ActionItem(
                        icon = if (song.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        label = if (song.isPinned) "Unpin from Profile" else "Pin to Profile"
                    ) {
                        scope.launch {
                            runCatching { repository.togglePinToProfile(song.id, !song.isPinned) }
                                .onFailure {
                                    infoDialogTitle = "Update Failed"
                                    infoDialogMessage = it.localizedMessage ?: "Only the song owner can pin or unpin this song."
                                }
                        }
                    }
                }
                item {
                    ActionItem(icon = Icons.Default.Radio, label = "Start Song Radio (Demo)") {
                        playerManager?.playSong(song.audioUrl, song.title)
                        demoSongs.filter { it.title != song.title }.forEach { demo ->
                            playerManager?.addToQueue(demo.url, demo.title)
                        }
                        infoDialogTitle = "Song Radio Started (Demo)"
                        infoDialogMessage = "Playing \"${song.title}\" followed by sample demo tracks in your queue."
                    }
                }
                item {
                    ActionItem(
                        icon = Icons.Default.ReportProblem,
                        label = "Report Bad Quality",
                        color = MaterialTheme.colorScheme.error
                    ) {
                        reportType = "Bad Audio Quality"
                    }
                }
                item {
                    ActionItem(
                        icon = Icons.Default.Flag,
                        label = "Report Inappropriate",
                        color = MaterialTheme.colorScheme.error
                    ) {
                        reportType = "Inappropriate Content"
                    }
                }
                if (song.isPublic) {
                    item {
                        ActionItem(icon = Icons.Default.PublicOff, label = "Unpublish Song") {
                            showUnpublishDialog = true
                        }
                    }
                }
                item {
                    ActionItem(
                        icon = Icons.Default.Delete,
                        label = "Delete Song",
                        color = MaterialTheme.colorScheme.error
                    ) {
                        showDeleteDialog = true
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Song") },
            text = { Text("Are you sure you want to delete '${song.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { repository.deleteSong(song.id) }
                                .onSuccess {
                                    showDeleteDialog = false
                                    onDismiss()
                                    onDeleted()
                                }
                                .onFailure {
                                    showDeleteDialog = false
                                    infoDialogTitle = "Delete Failed"
                                    infoDialogMessage = it.localizedMessage ?: "Only the owner can delete this song."
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnpublishDialog) {
        AlertDialog(
            onDismissRequest = { showUnpublishDialog = false },
            title = { Text("Unpublish Song") },
            text = { Text("Make '${song.title}' private and remove it from Explore?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { repository.updateSongDetails(song.id, song.title, false) }
                                .onSuccess {
                                    showUnpublishDialog = false
                                    onDismiss()
                                }
                                .onFailure {
                                    showUnpublishDialog = false
                                    infoDialogTitle = "Unpublish Failed"
                                    infoDialogMessage = it.localizedMessage ?: "Only the owner can unpublish this song."
                                }
                        }
                    }
                ) {
                    Text("Unpublish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpublishDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        var newTitle by remember(song.id, song.title) { mutableStateOf(song.title) }
        var isPublic by remember(song.id, song.isPublic) { mutableStateOf(song.isPublic) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Song Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Song Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPublic, onCheckedChange = { isPublic = it })
                        Text("Make Public (Publish)")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newTitle.isNotBlank(),
                    onClick = {
                        scope.launch {
                            runCatching { repository.updateSongDetails(song.id, newTitle, isPublic) }
                                .onSuccess { showEditDialog = false }
                                .onFailure {
                                    showEditDialog = false
                                    infoDialogTitle = "Update Failed"
                                    infoDialogMessage = it.localizedMessage ?: "Only the owner can edit this song."
                                }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPlaylistDialog) {
        var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
        var newPlaylistName by remember { mutableStateOf("") }

        LaunchedEffect(showPlaylistDialog) {
            repository.getMyPlaylists().collectLatest { playlists = it }
        }

        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Add to Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("New playlist name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = newPlaylistName.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val id = repository.createPlaylist(newPlaylistName)
                                        repository.addSongToPlaylist(id, song.id)
                                        newPlaylistName = ""
                                    }.onFailure {
                                        infoDialogTitle = "Playlist Error"
                                        infoDialogMessage = it.localizedMessage ?: "Unable to create playlist."
                                    }
                                }
                            }
                        ) {
                            Text("Create")
                        }
                    }
                    if (playlists.isEmpty()) {
                        Text("No playlists yet. Create one above.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        playlists.forEach { playlist ->
                            val alreadyAdded = playlist.songIds.contains(song.id)
                            Surface(
                                onClick = {
                                    if (!alreadyAdded) {
                                        scope.launch {
                                            runCatching {
                                                repository.addSongToPlaylist(playlist.id, song.id)
                                            }.onSuccess {
                                                showPlaylistDialog = false
                                                infoDialogTitle = "Playlist Updated"
                                                infoDialogMessage = "Added '${song.title}' to '${playlist.name}'."
                                            }.onFailure {
                                                infoDialogTitle = "Playlist Error"
                                                infoDialogMessage = it.localizedMessage ?: "Unable to update playlist."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlaylistPlay, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(playlist.name, modifier = Modifier.weight(1f))
                                    if (alreadyAdded) {
                                        Icon(Icons.Default.Check, contentDescription = "Added", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showReuseDialog) {
        val summary = buildString {
            appendLine("Genre: ${song.genre}")
            if (song.language.isNotBlank()) appendLine("Language: ${song.language}")
            if (song.mood.isNotBlank()) appendLine("Mood: ${song.mood}")
            if (song.tempo.isNotBlank()) appendLine("Tempo: ${song.tempo}")
            if (song.vocalStyle.isNotBlank()) appendLine("Vocal Style: ${song.vocalStyle}")
            if (song.instruments.isNotEmpty()) appendLine("Instruments: ${song.instruments.joinToString(", ")}")
            if (song.prompt.isNotBlank()) appendLine("Style Prompt: ${song.prompt}")
            if (song.lyrics.isNotBlank()) appendLine("\nLyrics:\n${song.lyrics}")
        }.ifBlank { "Genre: ${song.genre}" }

        AlertDialog(
            onDismissRequest = { showReuseDialog = false },
            title = { Text("Reuse Styles & Lyrics") },
            text = { Text(summary) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        clipboardManager.setText(AnnotatedString(summary))
                        showReuseDialog = false
                        infoDialogTitle = "Copied"
                        infoDialogMessage = "Styles and lyrics copied to your clipboard."
                    }) {
                        Text("Copy")
                    }
                    if (onReuseInStudio != null) {
                        Button(onClick = {
                            showReuseDialog = false
                            onDismiss()
                            onReuseInStudio(song)
                        }) {
                            Text("Load in Studio")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showReuseDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (reportType != null) {
        var details by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reportType = null },
            title = { Text("Report: $reportType") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tell us what went wrong with '${song.title}':", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Details (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    reportType = null
                    infoDialogTitle = "Report Submitted (Demo)"
                    infoDialogMessage = "Thank you for your feedback. Moderation reporting is recorded in demo mode."
                }) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportType = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (infoDialogTitle != null && infoDialogMessage != null) {
        AlertDialog(
            onDismissRequest = {
                infoDialogTitle = null
                infoDialogMessage = null
            },
            title = { Text(infoDialogTitle!!) },
            text = { Text(infoDialogMessage!!) },
            confirmButton = {
                TextButton(onClick = {
                    infoDialogTitle = null
                    infoDialogMessage = null
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}
