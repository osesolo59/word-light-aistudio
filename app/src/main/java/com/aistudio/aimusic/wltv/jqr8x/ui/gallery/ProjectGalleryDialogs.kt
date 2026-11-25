package com.aistudio.aimusic.wltv.jqr8x.ui.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectStatus
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectType
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.model.MusicVideoProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.ProjectCoverTheme
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoProject
import com.aistudio.aimusic.wltv.jqr8x.data.repository.ProjectGalleryRepository
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

data class StudioImportCandidate(
    val title: String,
    val genre: String,
    val projectType: CreativeProjectType,
    val language: String,
    val description: String,
    val linkedSourceCollection: String,
    val linkedSourceId: String
)

internal fun projectTypeIcon(type: CreativeProjectType): ImageVector = when (type) {
    CreativeProjectType.SONG -> Icons.Default.MusicNote
    CreativeProjectType.LYRICS -> Icons.Default.Lyrics
    CreativeProjectType.AUDIO_MIX -> Icons.Default.GraphicEq
    CreativeProjectType.VIDEO -> Icons.Default.VideoSettings
    CreativeProjectType.MUSIC_VIDEO -> Icons.Default.Movie
    CreativeProjectType.MINISTRY -> Icons.Default.WorkspacePremium
}

@Composable
internal fun coverThemeGradient(theme: ProjectCoverTheme): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return when (theme) {
        ProjectCoverTheme.PURPLE_GOLD -> listOf(scheme.primary, scheme.primary.copy(alpha = 0.78f))
        ProjectCoverTheme.GOLD_RADIANCE -> listOf(scheme.secondary.copy(alpha = 0.92f), scheme.primary.copy(alpha = 0.85f))
        ProjectCoverTheme.ROYAL_NIGHT -> listOf(scheme.primary.copy(alpha = 0.95f), scheme.tertiary)
        ProjectCoverTheme.SANCTUARY_MINT -> listOf(scheme.tertiary, scheme.primary)
    }
}

internal fun formatCreationDate(timestamp: Timestamp?, includeTime: Boolean = false): String {
    val date = (timestamp ?: Timestamp.now()).toDate()
    val pattern = if (includeTime) "MMM d, yyyy • h:mm a" else "MMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}

@Composable
fun ProjectDetailDialog(
    project: CreativeProject,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenInStudio: ((CreativeProjectType) -> Unit)? = null
) {
    val type = CreativeProjectType.fromKey(project.projectType)
    val status = CreativeProjectStatus.fromKey(project.status)
    val cover = ProjectCoverTheme.fromKey(project.coverTheme)
    val createdLabel = formatCreationDate(project.createdAt ?: project.updatedAt, includeTime = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("gallery_detail_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.title.ifBlank { "Untitled Project" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("gallery_detail_favorite_button")
                ) {
                    Icon(
                        imageVector = if (project.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (project.favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (project.favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(coverThemeGradient(cover)))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = projectTypeIcon(type),
                                contentDescription = type.label,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "${type.label} • ${status.label}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = project.genre,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                MetadataDetailRow(
                    icon = Icons.Default.Category,
                    label = "Genre",
                    value = project.genre.ifBlank { "Gospel" }
                )
                MetadataDetailRow(
                    icon = Icons.Default.Language,
                    label = "Language",
                    value = project.language.ifBlank { "English" }
                )
                MetadataDetailRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Created",
                    value = createdLabel
                )

                if (project.description.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Project Notes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = project.description,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (onOpenInStudio != null && type != CreativeProjectType.MINISTRY) {
                    OutlinedButton(
                        onClick = { onOpenInStudio(type) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gallery_detail_open_studio_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open ${type.label} Studio")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDeleteRequest,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gallery_detail_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                    Button(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gallery_detail_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("gallery_detail_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun MetadataDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectEditorDialog(
    initialProject: CreativeProject?,
    initialCandidate: StudioImportCandidate? = null,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        genre: String,
        projectType: CreativeProjectType,
        language: String,
        description: String,
        status: CreativeProjectStatus,
        coverTheme: ProjectCoverTheme,
        favorite: Boolean,
        linkedSourceCollection: String,
        linkedSourceId: String
    ) -> Unit
) {
    val isEditing = initialProject != null
    var title by remember(initialProject, initialCandidate) {
        mutableStateOf(initialProject?.title ?: initialCandidate?.title ?: "")
    }
    var genre by remember(initialProject, initialCandidate) {
        mutableStateOf(initialProject?.genre ?: initialCandidate?.genre ?: "Gospel")
    }
    var selectedType by remember(initialProject, initialCandidate) {
        mutableStateOf(
            initialProject?.let { CreativeProjectType.fromKey(it.projectType) }
                ?: initialCandidate?.projectType
                ?: CreativeProjectType.SONG
        )
    }
    var language by remember(initialProject, initialCandidate) {
        mutableStateOf(initialProject?.language ?: initialCandidate?.language ?: "English")
    }
    var status by remember(initialProject) {
        mutableStateOf(
            initialProject?.let { CreativeProjectStatus.fromKey(it.status) }
                ?: CreativeProjectStatus.IN_PROGRESS
        )
    }
    var coverTheme by remember(initialProject) {
        mutableStateOf(
            initialProject?.let { ProjectCoverTheme.fromKey(it.coverTheme) }
                ?: ProjectCoverTheme.PURPLE_GOLD
        )
    }
    var description by remember(initialProject, initialCandidate) {
        mutableStateOf(initialProject?.description ?: initialCandidate?.description ?: "")
    }
    var favorite by remember(initialProject) {
        mutableStateOf(initialProject?.favorite ?: false)
    }
    var showTitleError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        modifier = Modifier.testTag("gallery_editor_dialog"),
        title = {
            Text(
                text = if (isEditing) "Edit Project" else "Save Creative Project",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it.take(120)
                        if (showTitleError && it.isNotBlank()) showTitleError = false
                    },
                    label = { Text("Project Title *") },
                    placeholder = { Text("e.g., Psalm 119 Dawn Anthem") },
                    isError = showTitleError,
                    supportingText = if (showTitleError) {
                        { Text("Please enter a project title") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gallery_editor_title_input")
                )

                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it.take(60) },
                    label = { Text("Genre *") },
                    placeholder = { Text("Gospel, Worship, Highlife...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gallery_editor_genre_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProjectGalleryRepository.DEFAULT_GENRES.forEach { presetGenre ->
                        FilterChip(
                            selected = genre.equals(presetGenre, ignoreCase = true),
                            onClick = { genre = presetGenre },
                            label = { Text(presetGenre) },
                            modifier = Modifier.testTag("gallery_editor_genre_chip_${presetGenre.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "Project Type",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CreativeProjectType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            leadingIcon = {
                                Icon(
                                    imageVector = projectTypeIcon(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(type.label) },
                            modifier = Modifier.testTag("gallery_editor_type_${type.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "Language",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProjectGalleryRepository.DEFAULT_LANGUAGES.forEach { lang ->
                        FilterChip(
                            selected = language.equals(lang, ignoreCase = true),
                            onClick = { language = lang },
                            label = { Text(lang) }
                        )
                    }
                }

                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CreativeProjectStatus.entries.forEach { entry ->
                        FilterChip(
                            selected = status == entry,
                            onClick = { status = entry },
                            label = { Text(entry.label) }
                        )
                    }
                }

                Text(
                    text = "Gallery Card Theme",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProjectCoverTheme.entries.forEach { themeOption ->
                        val selected = coverTheme == themeOption
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { coverTheme = themeOption }
                                .testTag("gallery_editor_theme_${themeOption.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.linearGradient(coverThemeGradient(themeOption)))
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(1000) },
                    label = { Text("Description / Scripture & Notes") },
                    placeholder = { Text("Add instrumentation, choir notes, or scripture verse...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gallery_editor_description_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mark as Favorite",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = favorite,
                        onCheckedChange = { favorite = it },
                        modifier = Modifier.testTag("gallery_editor_favorite_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                    } else {
                        onSave(
                            title.trim(),
                            genre.trim().ifBlank { "Gospel" },
                            selectedType,
                            language.trim().ifBlank { "English" },
                            description.trim(),
                            status,
                            coverTheme,
                            favorite,
                            initialProject?.linkedSourceCollection
                                ?: initialCandidate?.linkedSourceCollection
                                ?: "",
                            initialProject?.linkedSourceId
                                ?: initialCandidate?.linkedSourceId
                                ?: ""
                        )
                    }
                },
                modifier = Modifier.testTag("gallery_editor_save_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isEditing) "Update" else "Save Project")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onDismiss,
                modifier = Modifier.testTag("gallery_editor_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ImportFromStudioDialog(
    songs: List<Song>,
    lyrics: List<Lyric>,
    audioMixes: List<AudioMixProject>,
    videos: List<VideoProject>,
    musicVideos: List<MusicVideoProject>,
    onDismiss: () -> Unit,
    onSelectCandidate: (StudioImportCandidate) -> Unit
) {
    val candidates = remember(songs, lyrics, audioMixes, videos, musicVideos) {
        buildList {
            songs.forEach { song ->
                add(
                    StudioImportCandidate(
                        title = song.title.ifBlank { "Untitled Song" },
                        genre = song.genre.ifBlank { "Gospel" },
                        projectType = CreativeProjectType.SONG,
                        language = song.language.ifBlank { "English" },
                        description = song.prompt.ifBlank { song.mood },
                        linkedSourceCollection = "songs",
                        linkedSourceId = song.id
                    )
                )
            }
            lyrics.forEach { lyric ->
                add(
                    StudioImportCandidate(
                        title = lyric.title.ifBlank { "Untitled Lyrics" },
                        genre = lyric.genre.ifBlank { "Worship" },
                        projectType = CreativeProjectType.LYRICS,
                        language = lyric.language.ifBlank { "English" },
                        description = listOf(lyric.topic, lyric.scriptureReference)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        linkedSourceCollection = "lyrics",
                        linkedSourceId = lyric.id
                    )
                )
            }
            audioMixes.forEach { mix ->
                add(
                    StudioImportCandidate(
                        title = mix.title.ifBlank { "Gospel Mix" },
                        genre = "Gospel Mix",
                        projectType = CreativeProjectType.AUDIO_MIX,
                        language = mix.language.ifBlank { "Mixed English + Twi" },
                        description = "${mix.tracks.size} audio track(s) mixed",
                        linkedSourceCollection = "audio_mixes",
                        linkedSourceId = mix.id
                    )
                )
            }
            videos.forEach { video ->
                add(
                    StudioImportCandidate(
                        title = video.title.ifBlank { "Untitled Video" },
                        genre = video.templateName.ifBlank { "Ministry Video" },
                        projectType = CreativeProjectType.VIDEO,
                        language = "English",
                        description = video.prompt.ifBlank { video.captions },
                        linkedSourceCollection = "videos",
                        linkedSourceId = video.id
                    )
                )
            }
            musicVideos.forEach { mv ->
                add(
                    StudioImportCandidate(
                        title = mv.title.ifBlank { "Music Video" },
                        genre = "Gospel Music Video",
                        projectType = CreativeProjectType.MUSIC_VIDEO,
                        language = "English",
                        description = mv.bibleVerse.ifBlank { mv.captions },
                        linkedSourceCollection = "music_videos",
                        linkedSourceId = mv.id
                    )
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("gallery_import_dialog"),
        title = { Text("Import from Studio", fontWeight = FontWeight.Bold) },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    text = "Create a song, lyrics sheet, Gospel mix, or video in the Studios first, or add a new project directly.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(candidates) { candidate ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCandidate(candidate) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = projectTypeIcon(candidate.projectType),
                                    contentDescription = candidate.projectType.label,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${candidate.projectType.label} • ${candidate.genre} • ${candidate.language}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
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
}
