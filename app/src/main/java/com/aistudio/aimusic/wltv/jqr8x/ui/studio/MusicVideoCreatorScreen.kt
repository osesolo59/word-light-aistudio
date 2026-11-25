package com.aistudio.aimusic.wltv.jqr8x.ui.studio

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.aistudio.aimusic.wltv.jqr8x.data.model.StudioAudioMixDraft
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
fun MusicVideoCreatorScreen(
    onNavigateToProjects: () -> Unit,
    repositoryOverride: MusicRepository? = null,
    ideaDraft: StudioIdeaDraft? = null,
    ideaDraftFlow: StateFlow<StudioIdeaDraft?>? = null,
    onConsumeIdeaDraft: (String) -> Unit = {},
    viewModelOverride: StudioViewModel? = null,
    mixDraft: StudioAudioMixDraft? = null,
    mixDraftFlow: StateFlow<StudioAudioMixDraft?>? = null,
    onConsumeMixDraft: (String) -> Unit = {},
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

    val fallbackMixFlow = remember(mixDraft) { MutableStateFlow(mixDraft) }
    val observedMixDraft by (mixDraftFlow ?: fallbackMixFlow).collectAsStateWithLifecycle()
    val activeMixDraft = observedMixDraft ?: mixDraft

    var selectedAudio by rememberSaveable { mutableStateOf("") }
    var selectedAudioUri by rememberSaveable { mutableStateOf("") }
    var selectedVideo by rememberSaveable { mutableStateOf("") }
    var bibleVerse by rememberSaveable { mutableStateOf("") }
    var songTitle by rememberSaveable { mutableStateOf("") }
    var showDirectPublishDialog by remember { mutableStateOf(false) }
    var captions by rememberSaveable { mutableStateOf("") }
    var selectedAspectRatio by remember { mutableStateOf(SlideshowAspectRatio.LANDSCAPE_16_9) }
    var selectedResolution by remember { mutableStateOf(VideoResolutionPreset.FHD_1080P) }
    var selectedFrameRate by remember { mutableStateOf(VideoFrameRate.FPS_30) }
    var selectedCodec by remember { mutableStateOf(VideoCodecOption.H264) }
    var brandingOptions by remember { mutableStateOf(WordLightBrandingOptions()) }
    var selectedTransition by remember { mutableStateOf(SlideshowTransition.FADE) }
    var durationSecondsPerPhoto by rememberSaveable { mutableIntStateOf(3) }
    var captionCues by remember { mutableStateOf<List<TimedBilingualCue>>(emptyList()) }
    var captionDisplayMode by remember { mutableStateOf(BilingualCaptionMode.BOTH) }
    var transferredIdea by remember { mutableStateOf<CreativeIdea?>(null) }
    var lastAppliedTransferId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAppliedMixTransferId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftInitialized by rememberSaveable { mutableStateOf(false) }

    val baseAudioOptions = listOf("Praise High (Gospel)", "Deep Worship (Ballad)", "African Beats (Afrobeat)")
    val baseVideoOptions = listOf("Sunlight Over Cross", "Ocean Waves", "Mountain Majesty", "Church Interior")

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
            val saved = viewModel.loadSavedDraftIfPresent(context, "MUSIC_VIDEO")
            if (saved != null) {
                if (songTitle.isBlank()) songTitle = saved.title
                if (bibleVerse.isBlank()) bibleVerse = saved.bibleVerse
                if (captions.isBlank()) captions = saved.captions
                if (selectedVideo.isBlank()) selectedVideo = saved.templateName
                if (selectedAudio.isBlank()) selectedAudio = saved.audioSourceLabel
                if (selectedAudioUri.isBlank()) selectedAudioUri = saved.audioUriString
                selectedAspectRatio = saved.aspectRatio
                selectedResolution = saved.resolutionPreset
                selectedFrameRate = saved.frameRate
                selectedCodec = saved.videoCodec
                brandingOptions = saved.branding
                selectedTransition = saved.transition
                durationSecondsPerPhoto = saved.durationSecondsPerPhoto
                if (captionCues.isEmpty() && saved.captionCues.isNotEmpty()) captionCues = saved.captionCues
                captionDisplayMode = saved.captionDisplayMode
            }
        }
    }

    fun persistCurrentDraft() {
        viewModel.persistDraft(
            context = context,
            studioType = "MUSIC_VIDEO",
            title = songTitle,
            bibleVerse = bibleVerse,
            captions = captions,
            templateName = selectedVideo,
            aspectRatio = selectedAspectRatio,
            resolutionPreset = selectedResolution,
            frameRate = selectedFrameRate,
            videoCodec = selectedCodec,
            branding = brandingOptions,
            transition = selectedTransition,
            durationSecondsPerPhoto = durationSecondsPerPhoto,
            audioSourceLabel = selectedAudio,
            audioUriString = selectedAudioUri,
            captionCues = captionCues,
            captionDisplayMode = captionDisplayMode
        )
    }

    LaunchedEffect(activeDraft?.transferId) {
        val draft = activeDraft ?: return@LaunchedEffect
        if (draft.transferId == lastAppliedTransferId) return@LaunchedEffect
        val idea = draft.idea
        songTitle = idea.title
        bibleVerse = idea.bibleVerse.ifBlank { idea.summary }
        if (captions.isBlank() && idea.summary.isNotBlank()) captions = idea.summary
        selectedAudio = "${idea.title} (${idea.genre} • ${idea.language})"
        if (selectedAudioUri.isBlank()) {
            selectedAudioUri = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
        }
        selectedVideo = idea.videoTemplateName.ifBlank { "Sunlight Over Cross" }
        transferredIdea = idea
        lastAppliedTransferId = draft.transferId
        onConsumeIdeaDraft(draft.transferId)
    }

    LaunchedEffect(activeMixDraft?.transferId) {
        val draft = activeMixDraft ?: return@LaunchedEffect
        if (draft.transferId == lastAppliedMixTransferId) return@LaunchedEffect
        val cleanTitle = draft.title.ifBlank { "English & Twi Gospel Mix" }
        songTitle = cleanTitle
        val mixExtLabel = if (draft.localMp3Uri.endsWith(".wav", ignoreCase = true)) "Mastered WAV" else "Mastered MP3"
        selectedAudio = "$cleanTitle ($mixExtLabel)"
        if (draft.localMp3Uri.isNotBlank()) {
            selectedAudioUri = draft.localMp3Uri
        }
        val resolvedCues = if (draft.captionCues.isNotEmpty()) {
            draft.captionCues
        } else {
            BilingualGospelHelper.generateCuesFromLyrics(draft.lyrics, totalDurationSecHint = 16)
        }
        if (resolvedCues.isNotEmpty()) {
            captionCues = resolvedCues
        }
        if (captions.isBlank()) {
            val firstCue = resolvedCues.firstOrNull()
            captions = if (firstCue != null) {
                listOf(firstCue.englishText, firstCue.twiText).filter { it.isNotBlank() }.joinToString(" / ")
            } else {
                draft.lyrics.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("[") }
                    .take(2)
                    .joinToString(" / ")
            }
        }
        lastAppliedMixTransferId = draft.transferId
        persistCurrentDraft()
        onConsumeMixDraft(draft.transferId)
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destUri ->
        val path = (uiState as? StudioUiState.MusicVideoSuccess)?.exportedOutput?.outputFilePath
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

    val audioOptions = remember(selectedAudio) {
        if (selectedAudio.isNotBlank() && selectedAudio !in baseAudioOptions) {
            listOf(selectedAudio) + baseAudioOptions
        } else {
            baseAudioOptions
        }
    }
    val videoOptions = remember(selectedVideo) {
        if (selectedVideo.isNotBlank() && selectedVideo !in baseVideoOptions) {
            listOf(selectedVideo) + baseVideoOptions
        } else {
            baseVideoOptions
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Music Video Creator",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Word & Light TV • Full HD 1080p MP4 Gospel Music Video Production",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        transferredIdea?.let { idea ->
            Spacer(modifier = Modifier.height(10.dp))
            AssistChip(
                onClick = { transferredIdea = null },
                modifier = Modifier.testTag("mv_idea_transferred_banner"),
                label = { Text("Loaded idea: ${idea.title}") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mv_idea_details_card"),
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
                    if (idea.videoPrompt.isNotBlank()) {
                        Text("Visual Scene: ${idea.videoPrompt}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (MusicRepository.IS_DEMO_MODE) {
            DemoNotice()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("1. Select Audio Track or Upload Your Own", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        StudioAudioPickerSection(
            tagPrefix = "mv",
            selectedAudioLabel = selectedAudio,
            selectedAudioUri = selectedAudioUri,
            librarySongs = librarySongs,
            presetLabels = audioOptions,
            allowNoAudio = false,
            onSelectPreset = { label, uri ->
                selectedAudio = label
                selectedAudioUri = uri
                persistCurrentDraft()
            },
            onAudioFilePicked = { uri ->
                viewModel.importCustomAudio(context, uri, "MUSIC_VIDEO") { name, localUri ->
                    selectedAudio = name
                    selectedAudioUri = localUri
                    persistCurrentDraft()
                }
            },
            savedAudioMixes = libraryMixes,
            onMixSelected = { mix ->
                if (songTitle.isBlank() && mix.title.isNotBlank()) songTitle = mix.title
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

        Spacer(modifier = Modifier.height(20.dp))

        Text("2. Upload Personal Photos or Select Background", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        PhotoUploadStoryboardSection(
            tagPrefix = "mv",
            photos = photos,
            title = songTitle,
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
                viewModel.importPickedPhotos(context, uris, "MUSIC_VIDEO") {
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
            showBibleVerseField = false,
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

        Spacer(modifier = Modifier.height(14.dp))
        Text("Or Choose Demo Background Scene", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        TrackSelector(
            options = videoOptions,
            selected = selectedVideo,
            onSelected = {
                selectedVideo = it
                persistCurrentDraft()
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("3. Song Title & Bible Verse Overlays", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = songTitle,
            onValueChange = {
                songTitle = it
                persistCurrentDraft()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mv_title_input"),
            label = { Text("Song Title") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = bibleVerse,
            onValueChange = {
                bibleVerse = it
                persistCurrentDraft()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mv_bible_verse_input"),
            label = { Text("Bible Verse or Inspirational Text") },
            minLines = 2
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (photos.isNotEmpty()) {
            Button(
                onClick = {
                    viewModel.exportPhotoMusicVideo(
                        context = context,
                        title = songTitle,
                        songLabel = selectedAudio,
                        audioUriString = selectedAudioUri,
                        bibleVerse = bibleVerse,
                        captions = captions,
                        backgroundLabel = selectedVideo.ifBlank { "Uploaded Photos (${photos.size})" },
                        aspectRatio = selectedAspectRatio,
                        transition = selectedTransition,
                        durationSecondsPerPhoto = durationSecondsPerPhoto,
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
                    .testTag("mv_create_mp4_button"),
                enabled = uiState !is StudioUiState.Processing && uiState !is StudioUiState.ExportingMp4
            ) {
                Icon(Icons.Default.Movie, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create MP4 Music Video (${photos.size} Photos)", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    viewModel.createMusicVideo(
                        songId = selectedAudio.ifBlank { "Custom Song" },
                        videoId = selectedVideo.ifBlank { "Uploaded Photos (${photos.size})" },
                        bibleVerse = bibleVerse,
                        title = songTitle
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = (selectedAudio.isNotBlank() && selectedVideo.isNotBlank()) &&
                    uiState !is StudioUiState.Processing &&
                    uiState !is StudioUiState.ExportingMp4
            ) {
                Text("Save Demo Preview Only")
            }
        } else {
            Button(
                onClick = {
                    viewModel.createMusicVideo(
                        songId = selectedAudio,
                        videoId = selectedVideo,
                        bibleVerse = bibleVerse,
                        title = songTitle
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("mv_preview_demo_button"),
                enabled = selectedAudio.isNotBlank() &&
                    selectedVideo.isNotBlank() &&
                    uiState !is StudioUiState.Processing &&
                    uiState !is StudioUiState.ExportingMp4
            ) {
                Text("Preview & Save Music Video", fontWeight = FontWeight.Bold)
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
                text = "Combining photos & audio into MP4 with Media3... $pct%",
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
                "Assembling your project preview...",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    if (uiState is StudioUiState.MusicVideoSuccess) {
        val successState = uiState as StudioUiState.MusicVideoSuccess
        val project = successState.project
        val exported = successState.exportedOutput
        if (exported != null) {
            ExportedVideoResultDialog(
                tagPrefix = "mv",
                title = project.title,
                subtitle = "Audio: ${selectedAudio.ifBlank { "Visual Only" }} • ${photos.size} photos • ${selectedAspectRatio.ratioLabel} • ${selectedResolution.shortLabel}",
                exportedOutput = exported,
                onSaveToDevice = {
                    viewModel.saveVideoToDevice(context, exported.outputFilePath, project.title)
                },
                onShareVideo = {
                    val shareIntent = StudioMediaHelper.createShareVideoIntent(
                        context = context,
                        videoFilePath = exported.outputFilePath,
                        title = project.title
                    )
                    if (shareIntent != null) {
                        context.startActivity(Intent.createChooser(shareIntent, "Share Music Video MP4"))
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
                    initialTitle = project.title,
                    initialBibleVerse = bibleVerse,
                    initialDescription = selectedAudio.ifBlank { project.title },
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
                            title = project.title
                        )
                        if (shareIntent != null) {
                            context.startActivity(Intent.createChooser(shareIntent, "Share Music Video MP4"))
                        }
                    },
                    publishingRepositoryOverride = publishingRepositoryOverride
                )
            }
        } else {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text("Project Preview") },
                text = {
                    Column {
                        Text("SIMULATED DEMO PREVIEW", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Audio: $selectedAudio")
                        Text("Background: $selectedVideo")
                        if (project.title.isNotBlank()) Text("Title: ${project.title}")
                        if (project.bibleVerse.isNotBlank()) Text("Text: ${project.bibleVerse}")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Upload personal photos above to render a real MP4 music video on your device.")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.resetState()
                        onNavigateToProjects()
                    }) {
                        Text("Save Project")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("Close")
                    }
                }
            )
        }
    }

    if (uiState is StudioUiState.Error) {
        val error = (uiState as StudioUiState.Error).message
        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            title = { Text("Music Video Export Notice") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetState() }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackSelector(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(option) }
            )
        }
    }
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
                text = "Personal photo MP4 music videos render locally on your device via Media3 Transformer. Cloud template generation remains in Free Demo Mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
