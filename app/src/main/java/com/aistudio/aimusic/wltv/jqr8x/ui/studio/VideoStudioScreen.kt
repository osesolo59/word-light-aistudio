package com.aistudio.aimusic.wltv.jqr8x.ui.studio

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.media.StudioMediaHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.BilingualCaptionMode
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowAspectRatio
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowTransition
import com.aistudio.aimusic.wltv.jqr8x.data.model.StudioIdeaDraft
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoCodecOption
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoFrameRate
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoResolutionPreset
import com.aistudio.aimusic.wltv.jqr8x.data.model.WordLightBrandingOptions
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingRepository
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.publish.DirectPublishDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun VideoStudioScreen(
    onNavigateToProjects: () -> Unit,
    repositoryOverride: MusicRepository? = null,
    ideaDraft: StudioIdeaDraft? = null,
    ideaDraftFlow: StateFlow<StudioIdeaDraft?>? = null,
    onConsumeIdeaDraft: (String) -> Unit = {},
    viewModelOverride: StudioViewModel? = null,
    onNavigateToConnectedChannels: () -> Unit = {},
    publishingRepositoryOverride: PublishingRepository? = null
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val viewModel: StudioViewModel = viewModelOverride ?: viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return StudioViewModel(repository) as T
            }
        }
    )

    val fallbackFlow = remember(ideaDraft) { MutableStateFlow(ideaDraft) }
    val observedDraft by (ideaDraftFlow ?: fallbackFlow).collectAsStateWithLifecycle()
    val activeDraft = observedDraft ?: ideaDraft

    var videoTitle by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var bibleVerse by rememberSaveable { mutableStateOf("") }
    var captions by rememberSaveable { mutableStateOf("") }
    var selectedAspectRatio by remember { mutableStateOf(SlideshowAspectRatio.LANDSCAPE_16_9) }
    var selectedResolution by remember { mutableStateOf(VideoResolutionPreset.FHD_1080P) }
    var selectedFrameRate by remember { mutableStateOf(VideoFrameRate.FPS_30) }
    var selectedCodec by remember { mutableStateOf(VideoCodecOption.H264) }
    var brandingOptions by remember { mutableStateOf(WordLightBrandingOptions()) }
    var selectedTransition by remember { mutableStateOf(SlideshowTransition.FADE) }
    var durationSecondsPerPhoto by rememberSaveable { mutableIntStateOf(3) }
    var selectedAudioLabel by rememberSaveable { mutableStateOf("") }
    var selectedAudioUri by rememberSaveable { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf<VideoTemplate?>(null) }
    var captionCues by remember { mutableStateOf<List<TimedBilingualCue>>(emptyList()) }
    var captionDisplayMode by remember { mutableStateOf(BilingualCaptionMode.BOTH) }
    var transferredIdea by remember { mutableStateOf<CreativeIdea?>(null) }
    var lastAppliedTransferId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftInitialized by rememberSaveable { mutableStateOf(false) }
    var showDirectPublishDialog by remember { mutableStateOf(false) }

    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val libraryMixes by viewModel.libraryMixes.collectAsStateWithLifecycle()
    val mediaFeedback by viewModel.mediaFeedback.collectAsStateWithLifecycle()
    val pendingDocumentFileName by viewModel.pendingDocumentSaveFileName.collectAsStateWithLifecycle()
    val lastExportedVideoPath by viewModel.lastExportedVideoPath.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!draftInitialized) {
            draftInitialized = true
            val saved = viewModel.loadSavedDraftIfPresent(context, "VIDEO")
            if (saved != null) {
                if (videoTitle.isBlank()) videoTitle = saved.title
                if (prompt.isBlank()) prompt = saved.prompt
                if (bibleVerse.isBlank()) bibleVerse = saved.bibleVerse
                if (captions.isBlank()) captions = saved.captions
                selectedAspectRatio = saved.aspectRatio
                selectedResolution = saved.resolutionPreset
                selectedFrameRate = saved.frameRate
                selectedCodec = saved.videoCodec
                brandingOptions = saved.branding
                selectedTransition = saved.transition
                durationSecondsPerPhoto = saved.durationSecondsPerPhoto
                if (selectedAudioLabel.isBlank()) selectedAudioLabel = saved.audioSourceLabel
                if (selectedAudioUri.isBlank()) selectedAudioUri = saved.audioUriString
                if (captionCues.isEmpty() && saved.captionCues.isNotEmpty()) captionCues = saved.captionCues
                captionDisplayMode = saved.captionDisplayMode
                if (selectedTemplate == null && saved.templateName.isNotBlank()) {
                    selectedTemplate = videoTemplates.firstOrNull { it.name == saved.templateName }
                }
            }
        }
    }

    LaunchedEffect(activeDraft?.transferId) {
        val draft = activeDraft ?: return@LaunchedEffect
        if (draft.transferId == lastAppliedTransferId) return@LaunchedEffect
        val idea = draft.idea
        videoTitle = idea.title
        prompt = idea.videoPrompt.ifBlank { idea.summary }
        if (idea.bibleVerse.isNotBlank()) bibleVerse = idea.bibleVerse
        if (idea.summary.isNotBlank() && captions.isBlank()) captions = idea.summary
        selectedTemplate = videoTemplates.firstOrNull {
            it.name.equals(idea.videoTemplateName, ignoreCase = true)
        } ?: videoTemplates.first()
        transferredIdea = idea
        lastAppliedTransferId = draft.transferId
        onConsumeIdeaDraft(draft.transferId)
    }

    fun persistCurrentDraft() {
        viewModel.persistDraft(
            context = context,
            studioType = "VIDEO",
            title = videoTitle,
            prompt = prompt,
            bibleVerse = bibleVerse,
            captions = captions,
            templateName = selectedTemplate?.name.orEmpty(),
            aspectRatio = selectedAspectRatio,
            resolutionPreset = selectedResolution,
            frameRate = selectedFrameRate,
            videoCodec = selectedCodec,
            branding = brandingOptions,
            transition = selectedTransition,
            durationSecondsPerPhoto = durationSecondsPerPhoto,
            audioSourceLabel = selectedAudioLabel,
            audioUriString = selectedAudioUri,
            captionCues = captionCues,
            captionDisplayMode = captionDisplayMode
        )
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destUri ->
        val path = (uiState as? StudioUiState.VideoSuccess)?.exportedOutput?.outputFilePath
            ?: lastExportedVideoPath
        if (destUri != null && path.isNotBlank()) {
            viewModel.completeDocumentPickerSave(context, path, destUri)
        } else {
            viewModel.dismissPendingDocumentSave()
        }
    }

    LaunchedEffect(pendingDocumentFileName) {
        val name = pendingDocumentFileName ?: return@LaunchedEffect
        createDocumentLauncher.launch(name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Video Studio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Word & Light TV • Full HD 1080p MP4 Slideshow & Gospel Video Production",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        transferredIdea?.let { idea ->
            Spacer(modifier = Modifier.height(10.dp))
            AssistChip(
                onClick = { transferredIdea = null },
                modifier = Modifier.testTag("video_idea_transferred_banner"),
                label = { Text("Loaded idea: ${idea.title}") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_idea_details_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Idea Details (${idea.category})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (idea.topic.isNotBlank()) {
                        Text("Topic: ${idea.topic}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (idea.summary.isNotBlank()) {
                        Text("Description: ${idea.summary}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "Genre: ${idea.genre} • Language: ${idea.language} • Mood: ${idea.mood}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (idea.bibleVerse.isNotBlank()) {
                        Text("Scripture: ${idea.bibleVerse}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (MusicRepository.IS_DEMO_MODE) {
            DemoNotice()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Video Title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = videoTitle,
            onValueChange = {
                videoTitle = it
                persistCurrentDraft()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_title_input"),
            placeholder = { Text("Enter video title") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        PhotoUploadStoryboardSection(
            tagPrefix = "video",
            photos = photos,
            title = videoTitle,
            bibleVerse = bibleVerse,
            captions = captions,
            onBibleVerseChange = {
                bibleVerse = it
                persistCurrentDraft()
            },
            onCaptionsChange = {
                captions = it
                persistCurrentDraft()
            },
            aspectRatio = selectedAspectRatio,
            onAspectRatioChange = {
                selectedAspectRatio = it
                persistCurrentDraft()
            },
            transition = selectedTransition,
            onTransitionChange = {
                selectedTransition = it
                persistCurrentDraft()
            },
            durationSecondsPerPhoto = durationSecondsPerPhoto,
            onDurationChange = {
                durationSecondsPerPhoto = it
                persistCurrentDraft()
            },
            onPhotosPicked = { uris ->
                viewModel.importPickedPhotos(context, uris, "VIDEO") {
                    persistCurrentDraft()
                }
            },
            onMoveLeft = { id ->
                viewModel.movePhotoLeft(id) { persistCurrentDraft() }
            },
            onMoveRight = { id ->
                viewModel.movePhotoRight(id) { persistCurrentDraft() }
            },
            onRemovePhoto = { id ->
                viewModel.removePhoto(id) { persistCurrentDraft() }
            },
            onSetCover = { id ->
                viewModel.setCoverPhoto(id) { persistCurrentDraft() }
            },
            showBibleVerseField = true,
            feedbackMessage = mediaFeedback,
            onDismissFeedback = viewModel::clearFeedback,
            captionCues = captionCues,
            onCaptionCuesChange = {
                captionCues = it
                persistCurrentDraft()
            },
            captionDisplayMode = captionDisplayMode,
            onCaptionDisplayModeChange = {
                captionDisplayMode = it
                persistCurrentDraft()
            },
            resolutionPreset = selectedResolution,
            onResolutionChange = {
                selectedResolution = it
                persistCurrentDraft()
            },
            frameRate = selectedFrameRate,
            onFrameRateChange = {
                selectedFrameRate = it
                persistCurrentDraft()
            },
            videoCodec = selectedCodec,
            onVideoCodecChange = {
                selectedCodec = it
                persistCurrentDraft()
            },
            branding = brandingOptions,
            onBrandingChange = {
                brandingOptions = it
                persistCurrentDraft()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        StudioAudioPickerSection(
            tagPrefix = "video",
            selectedAudioLabel = selectedAudioLabel,
            selectedAudioUri = selectedAudioUri,
            librarySongs = librarySongs,
            presetLabels = listOf("Praise High (Gospel)", "Deep Worship (Ballad)"),
            allowNoAudio = true,
            onSelectPreset = { label, uri ->
                selectedAudioLabel = label
                selectedAudioUri = uri
                persistCurrentDraft()
            },
            onAudioFilePicked = { uri ->
                viewModel.importCustomAudio(context, uri, "VIDEO") { name, localUri ->
                    selectedAudioLabel = name
                    selectedAudioUri = localUri
                    persistCurrentDraft()
                }
            },
            savedAudioMixes = libraryMixes,
            onMixSelected = { mix ->
                if (videoTitle.isBlank() && mix.title.isNotBlank()) videoTitle = mix.title
                val resolvedCues = if (mix.captionCues.isNotEmpty()) {
                    mix.captionCues
                } else {
                    BilingualGospelHelper.generateCuesFromLyrics(
                        formattedLyrics = mix.lyrics,
                        totalDurationSecHint = (mix.totalDurationMs / 1000L).toInt().coerceAtLeast(12)
                    )
                }
                if (resolvedCues.isNotEmpty()) captionCues = resolvedCues
                if (captions.isBlank()) {
                    val cue = resolvedCues.firstOrNull()
                    if (cue != null) {
                        captions = listOf(cue.englishText, cue.twiText).filter { it.isNotBlank() }.joinToString(" / ")
                    }
                }
                persistCurrentDraft()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Text-to-Video Prompt (Template Mode)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it
                persistCurrentDraft()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_prompt_input"),
            placeholder = { Text("e.g. Cinematic sunrise over mountains with a cross") },
            minLines = 2
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Select Template", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videoTemplates) { template ->
                TemplateCard(
                    template = template,
                    isSelected = selectedTemplate == template,
                    onClick = {
                        selectedTemplate = template
                        persistCurrentDraft()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (photos.isNotEmpty()) {
            Button(
                onClick = {
                    viewModel.exportPhotoVideo(
                        context = context,
                        title = videoTitle,
                        prompt = prompt,
                        bibleVerse = bibleVerse,
                        captions = captions,
                        templateName = selectedTemplate?.name ?: "Photo Slideshow",
                        aspectRatio = selectedAspectRatio,
                        transition = selectedTransition,
                        durationSecondsPerPhoto = durationSecondsPerPhoto,
                        audioUriString = selectedAudioUri,
                        audioSourceLabel = selectedAudioLabel,
                        captionCues = captionCues,
                        captionDisplayMode = captionDisplayMode,
                        resolutionPreset = selectedResolution,
                        frameRate = selectedFrameRate,
                        videoCodec = selectedCodec,
                        branding = brandingOptions
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("video_create_mp4_button"),
                enabled = uiState !is StudioUiState.Processing && uiState !is StudioUiState.ExportingMp4
            ) {
                Icon(Icons.Default.Movie, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create MP4 Slideshow (${photos.size} Photos)", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    viewModel.generateVideo(prompt, selectedTemplate?.name ?: "No Template", videoTitle)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = (prompt.isNotBlank() || selectedTemplate != null) &&
                    uiState !is StudioUiState.Processing &&
                    uiState !is StudioUiState.ExportingMp4
            ) {
                Text("Save Demo Template Project Only")
            }
        } else {
            Button(
                onClick = {
                    viewModel.generateVideo(prompt, selectedTemplate?.name ?: "No Template", videoTitle)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("video_generate_demo_button"),
                enabled = (prompt.isNotBlank() || selectedTemplate != null) &&
                    uiState !is StudioUiState.Processing &&
                    uiState !is StudioUiState.ExportingMp4
            ) {
                Text("Generate Video", fontWeight = FontWeight.Bold)
            }
        }

        if (uiState is StudioUiState.ExportingMp4) {
            val pct = (uiState as StudioUiState.ExportingMp4).progressPercent
            Spacer(modifier = Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { (pct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Rendering MP4 slideshow on device with Media3... $pct%",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (uiState is StudioUiState.Processing) {
            Spacer(modifier = Modifier.height(20.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "Saving template project...",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    if (uiState is StudioUiState.VideoSuccess) {
        val successState = uiState as StudioUiState.VideoSuccess
        val exported = successState.exportedOutput
        if (exported != null) {
            ExportedVideoResultDialog(
                tagPrefix = "video",
                title = successState.project.title,
                subtitle = "${photos.size} photos • ${selectedAspectRatio.ratioLabel} • ${selectedResolution.shortLabel} • ${selectedTransition.label}",
                exportedOutput = exported,
                onSaveToDevice = {
                    viewModel.saveVideoToDevice(context, exported.outputFilePath, successState.project.title)
                },
                onShareVideo = {
                    val shareIntent = StudioMediaHelper.createShareVideoIntent(
                        context = context,
                        videoFilePath = exported.outputFilePath,
                        title = successState.project.title
                    )
                    if (shareIntent != null) {
                        context.startActivity(Intent.createChooser(shareIntent, "Share MP4 Video"))
                    }
                },
                onDirectPublish = { showDirectPublishDialog = true },
                onGoToProjects = {
                    viewModel.resetState()
                    onNavigateToProjects()
                },
                onDismiss = { viewModel.resetState() }
            )
            if (showDirectPublishDialog) {
                DirectPublishDialog(
                    initialTitle = successState.project.title,
                    initialBibleVerse = bibleVerse,
                    initialDescription = prompt,
                    localVideoPath = exported.outputFilePath,
                    aspectRatio = selectedAspectRatio.ratioLabel,
                    codecLabel = exported.codecLabel,
                    onDismiss = { showDirectPublishDialog = false },
                    onOpenConnectedChannels = {
                        showDirectPublishDialog = false
                        viewModel.resetState()
                        onNavigateToConnectedChannels()
                    },
                    onFallbackShareClick = {
                        val shareIntent = StudioMediaHelper.createShareVideoIntent(
                            context = context,
                            videoFilePath = exported.outputFilePath,
                            title = successState.project.title
                        )
                        if (shareIntent != null) {
                            context.startActivity(Intent.createChooser(shareIntent, "Share MP4 Video"))
                        }
                    },
                    publishingRepositoryOverride = publishingRepositoryOverride
                )
            }
        } else {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text("Video Generated!") },
                text = {
                    Column {
                        Text("SIMULATED DEMO CONTENT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Template project saved. Upload your own photos above to render a real MP4 slideshow on your device.")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Project: ${successState.project.title}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.resetState()
                        onNavigateToProjects()
                    }) {
                        Text("Go to My Projects")
                    }
                }
            )
        }
    }

    if (uiState is StudioUiState.Error) {
        val error = (uiState as StudioUiState.Error).message
        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            title = { Text("Video Export Notice") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetState() }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
fun TemplateCard(template: VideoTemplate, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(width = 140.dp, height = 88.dp)
            .clickable { onClick() },
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(template.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FormatChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun DemoNotice() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Personal photo MP4 slideshows render locally on your device via Media3 Transformer. Cloud template generation remains in Free Demo Mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

data class VideoTemplate(val name: String, val category: String)

val videoTemplates = listOf(
    VideoTemplate("Morning Worship", "Inspirational"),
    VideoTemplate("Nature Praise", "Background"),
    VideoTemplate("City Grace", "Cinematic"),
    VideoTemplate("Scripture Motion", "Bible Verse"),
    VideoTemplate("Church Event", "Announcement")
)
