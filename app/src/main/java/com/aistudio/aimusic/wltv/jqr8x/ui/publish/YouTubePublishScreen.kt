package com.aistudio.aimusic.wltv.jqr8x.ui.publish

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aistudio.aimusic.wltv.jqr8x.R
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishPlatform
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishPrivacy
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishVideoMetadata
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingRepository
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.YouTubeAuthOutcome
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.YouTubeUploadUiState
import com.aistudio.aimusic.wltv.jqr8x.data.social.SecureExternalLinkOpener
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.BackgroundWhite
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.GoldAccent
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurpleDark
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurpleLight
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurplePrimary
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.SurfacePurple
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private data class SelectedVideoSummary(
    val uriOrPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeUploadScreen(
    onNavigateBack: () -> Unit,
    onOpenConnectedChannels: () -> Unit,
    publishingRepositoryOverride: PublishingRepository? = null,
    initialVideoUriForTest: String = "",
    initialThumbnailUriForTest: String = ""
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val repository = remember(publishingRepositoryOverride, context) {
        publishingRepositoryOverride ?: PublishingRepository.getInstance(context)
    }

    val sessions by repository.sessionsFlow.collectAsState()
    val youtubeSession = sessions[PublishPlatform.YOUTUBE]
    val uploadState by repository.youTubeUploadState.collectAsState()
    val feedback by repository.lastAuthFeedback.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedVideo by remember(initialVideoUriForTest) {
        mutableStateOf(
            if (initialVideoUriForTest.isNotBlank()) {
                inspectSelectedVideo(context, initialVideoUriForTest)
            } else {
                null
            }
        )
    }
    var selectedThumbnailUri by remember(initialThumbnailUriForTest) {
        mutableStateOf(initialThumbnailUriForTest)
    }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("#WordAndLightTV #GospelMusic #ChristianWorship") }
    var selectedPrivacy by remember { mutableStateOf(PublishPrivacy.PRIVATE) }
    var privacyDropdownExpanded by remember { mutableStateOf(false) }

    val youtubeResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        repository.handleYouTubeResolutionResult(result.data)
    }

    val onVideoPicked: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val info = inspectSelectedVideo(context, uri.toString())
            selectedVideo = info
            if (title.isBlank()) {
                title = info.displayName.substringBeforeLast('.').take(100)
            }
            repository.resetYouTubeUploadState()
        }
    }

    val systemFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = onVideoPicked
    )

    val galleryVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = onVideoPicked
    )

    val thumbnailPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedThumbnailUri = uri.toString()
        }
    }

    LaunchedEffect(feedback) {
        val msg = feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        repository.clearAuthFeedback()
    }

    val isUploading = uploadState is YouTubeUploadUiState.Uploading
    val canStartUpload = !isUploading && youtubeSession != null && selectedVideo != null

    val launchYouTubeAuth: () -> Unit = {
        val activity = context.findYouTubeHostActivity()
        if (activity == null) {
            onOpenConnectedChannels()
        } else {
            scope.launch {
                when (val outcome = repository.authorizeYouTube(activity)) {
                    is YouTubeAuthOutcome.NeedsUserResolution -> {
                        val req = IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build()
                        youtubeResolutionLauncher.launch(req)
                    }
                    else -> Unit
                }
            }
        }
    }

    val triggerUpload: () -> Unit = {
        val video = selectedVideo
        if (video == null) {
            scope.launch {
                snackbarHostState.showSnackbar("Select a video file from your phone first.")
            }
        } else {
            val effectiveTitle = title.ifBlank {
                video.displayName.substringBeforeLast('.').ifBlank { "Word & Light TV Broadcast" }
            }
            repository.startYouTubeDirectUpload(
                PublishVideoMetadata(
                    localVideoPath = video.uriOrPath,
                    title = effectiveTitle,
                    description = description,
                    hashtags = tags,
                    privacy = selectedPrivacy,
                    localThumbnailPath = selectedThumbnailUri
                )
            )
        }
    }

    Scaffold(
        containerColor = BackgroundWhite,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.youtube_publish_title),
                        color = BackgroundWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_publish_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BackgroundWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PurplePrimary,
                    titleContentColor = BackgroundWhite
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundWhite)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .testTag("youtube_publish_scroll_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    YouTubeChannelStatusHeader(
                        channelName = youtubeSession?.effectiveDisplayLabel()
                            ?: PublishPlatform.YOUTUBE.officialTargetName,
                        isAuthorized = youtubeSession != null,
                        onViewChannel = {
                            SecureExternalLinkOpener.openExternalUrl(
                                context,
                                PublishPlatform.YOUTUBE.verifiedUrl
                            )
                        },
                        onAuthorizeClick = launchYouTubeAuth
                    )
                }

                item {
                    VideoSelectionCard(
                        selectedVideo = selectedVideo,
                        enabled = !isUploading,
                        onPickFromFiles = {
                            systemFilePickerLauncher.launch(arrayOf("video/*"))
                        },
                        onPickFromGallery = {
                            galleryVideoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                }

                item {
                    ThumbnailSelectionCard(
                        selectedThumbnailUri = selectedThumbnailUri,
                        enabled = !isUploading,
                        onPickThumbnail = {
                            thumbnailPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearThumbnail = {
                            selectedThumbnailUri = ""
                        }
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
                        border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Video Details & Visibility",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PurplePrimary
                            )

                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it.take(100) },
                                enabled = !isUploading,
                                label = { Text("Video Title") },
                                placeholder = { Text("Word & Light TV Gospel Broadcast") },
                                supportingText = { Text("${title.length}/100 characters") },
                                singleLine = true,
                                colors = youTubeTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("youtube_title_input")
                            )

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it.take(4500) },
                                enabled = !isUploading,
                                label = { Text("Video Description") },
                                placeholder = { Text("Share sermon notes, scripture references, or song lyrics...") },
                                minLines = 3,
                                maxLines = 6,
                                colors = youTubeTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("youtube_description_input")
                            )

                            OutlinedTextField(
                                value = tags,
                                onValueChange = { tags = it },
                                enabled = !isUploading,
                                label = { Text("Tags (comma or # separated)") },
                                placeholder = { Text("Gospel, Worship, WordAndLightTV") },
                                singleLine = true,
                                colors = youTubeTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("youtube_tags_input")
                            )

                            Text(
                                text = "Privacy Status",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = PurplePrimary
                            )

                            val privacyDisplayLabel = when (selectedPrivacy) {
                                PublishPrivacy.PRIVATE -> "Private (Only you)"
                                PublishPrivacy.UNLISTED -> "Unlisted (Anyone with link)"
                                PublishPrivacy.PUBLIC -> "Public (Everyone)"
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    onClick = { privacyDropdownExpanded = !privacyDropdownExpanded },
                                    enabled = !isUploading,
                                    shape = RoundedCornerShape(4.dp),
                                    color = BackgroundWhite,
                                    border = BorderStroke(
                                        width = if (privacyDropdownExpanded) 2.dp else 1.dp,
                                        color = if (privacyDropdownExpanded) PurplePrimary else PurplePrimary.copy(alpha = 0.55f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("youtube_privacy_dropdown")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = privacyDisplayLabel,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = PurpleDark
                                        )
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = privacyDropdownExpanded
                                        )
                                    }
                                }

                                if (privacyDropdownExpanded && !isUploading) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .testTag("youtube_privacy_menu"),
                                        shape = RoundedCornerShape(12.dp),
                                        color = BackgroundWhite,
                                        shadowElevation = 6.dp,
                                        border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.25f))
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            listOf(
                                                PublishPrivacy.PRIVATE to "Private (Only you)",
                                                PublishPrivacy.UNLISTED to "Unlisted (Anyone with link)",
                                                PublishPrivacy.PUBLIC to "Public (Everyone)"
                                            ).forEach { (privacy, optionLabel) ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = optionLabel,
                                                            fontWeight = if (selectedPrivacy == privacy) {
                                                                FontWeight.Bold
                                                            } else {
                                                                FontWeight.Medium
                                                            },
                                                            color = PurpleDark
                                                        )
                                                    },
                                                    trailingIcon = if (selectedPrivacy == privacy) {
                                                        {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = PurplePrimary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                    onClick = {
                                                        selectedPrivacy = privacy
                                                        privacyDropdownExpanded = false
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("youtube_privacy_${privacy.youtubeStatus}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Uses official YouTube Data API v3 videos.insert with existing youtube.upload permission only. Note: Unverified Google OAuth projects may temporarily restrict uploaded videos to Private until verification.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                when (val state = uploadState) {
                    is YouTubeUploadUiState.Uploading -> item {
                        YouTubeUploadProgressCard(
                            progressPercent = state.progressPercent,
                            stepLabel = state.stepLabel,
                            onCancelClick = { repository.cancelYouTubeUpload() }
                        )
                    }
                    is YouTubeUploadUiState.Success -> item {
                        YouTubeUploadSuccessCard(
                            state = state,
                            onWatchVideo = {
                                SecureExternalLinkOpener.openExternalUrl(context, state.videoUrl)
                            },
                            onCopyLink = {
                                clipboardManager.setText(AnnotatedString(state.videoUrl))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied YouTube link to clipboard.")
                                }
                            },
                            onUploadAnother = {
                                repository.resetYouTubeUploadState()
                                selectedVideo = null
                                selectedThumbnailUri = ""
                                title = ""
                                description = ""
                            }
                        )
                    }
                    is YouTubeUploadUiState.Cancelled -> item {
                        YouTubeUploadCancelledCard(
                            message = state.message,
                            onDismiss = { repository.resetYouTubeUploadState() }
                        )
                    }
                    is YouTubeUploadUiState.Error -> item {
                        YouTubeUploadErrorCard(
                            state = state,
                            canRetry = selectedVideo != null && youtubeSession != null,
                            onRetry = triggerUpload,
                            onReauthorize = launchYouTubeAuth
                        )
                    }
                    YouTubeUploadUiState.Idle -> Unit
                }

                item {
                    Button(
                        onClick = triggerUpload,
                        enabled = canStartUpload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurplePrimary,
                            contentColor = GoldAccent,
                            disabledContainerColor = PurplePrimary.copy(alpha = 0.35f),
                            disabledContentColor = BackgroundWhite.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (canStartUpload) GoldAccent else GoldAccent.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_upload_submit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUploading) "Uploading to YouTube..." else "Upload to YouTube",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun youTubeTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PurplePrimary,
    focusedLabelColor = PurplePrimary,
    cursorColor = PurplePrimary,
    unfocusedBorderColor = PurplePrimary.copy(alpha = 0.35f)
)

@Composable
private fun YouTubeChannelStatusHeader(
    channelName: String,
    isAuthorized: Boolean,
    onViewChannel: () -> Unit,
    onAuthorizeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("youtube_publish_channel_header"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, GoldAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(PurplePrimary, PurpleLight)))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = GoldAccent,
                        shape = CircleShape
                    ) {
                        Icon(
                            painter = painterResource(PublishPlatform.YOUTUBE.iconRes),
                            contentDescription = "YouTube",
                            tint = PurpleDark,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BackgroundWhite
                        )
                        Text(
                            text = if (isAuthorized) {
                                "Authorized • youtube.upload scope only"
                            } else {
                                "Authorization required before uploading"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAccent
                        )
                    }
                    Surface(
                        color = if (isAuthorized) GoldAccent else BackgroundWhite,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isAuthorized) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = PurpleDark,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAuthorized) "Authorized" else "Locked",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PurpleDark
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewChannel,
                        border = BorderStroke(1.dp, GoldAccent),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_publish_view_channel_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Channel", color = GoldAccent, fontWeight = FontWeight.Bold)
                    }

                    if (!isAuthorized) {
                        Button(
                            onClick = onAuthorizeClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldAccent,
                                contentColor = PurpleDark
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("youtube_publish_authorize_button")
                        ) {
                            Text("Authorize YouTube", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSelectionCard(
    selectedVideo: SelectedVideoSummary?,
    enabled: Boolean,
    onPickFromFiles: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("youtube_video_selection_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (selectedVideo != null) GoldAccent else PurplePrimary.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "1. Select Video From Phone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PurplePrimary
            )

            if (selectedVideo != null) {
                Surface(
                    color = SurfacePurple,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_selected_video_summary")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Selected video",
                            tint = PurplePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedVideo.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PurpleDark,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sizeLabel = if (selectedVideo.sizeBytes > 0L) {
                                formatReadableSize(selectedVideo.sizeBytes)
                            } else {
                                "Provider stream"
                            }
                            Text(
                                text = "${selectedVideo.mimeType} • $sizeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No video selected yet. Pick any MP4 or recorded video using Android's system file picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPickFromFiles,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = GoldAccent
                    ),
                    border = BorderStroke(1.dp, GoldAccent),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("youtube_pick_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedVideo == null) "Select Video File" else "Change File",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onPickFromGallery,
                    enabled = enabled,
                    border = BorderStroke(1.dp, PurplePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("youtube_pick_gallery_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = PurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery Video", color = PurplePrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThumbnailSelectionCard(
    selectedThumbnailUri: String,
    enabled: Boolean,
    onPickThumbnail: () -> Unit,
    onClearThumbnail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("youtube_thumbnail_selection_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. Custom Thumbnail (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary
                )
                if (selectedThumbnailUri.isNotBlank()) {
                    TextButton(
                        onClick = onClearThumbnail,
                        enabled = enabled,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_clear_thumbnail_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Remove thumbnail",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (selectedThumbnailUri.isNotBlank()) {
                AsyncImage(
                    model = selectedThumbnailUri,
                    contentDescription = "Selected video thumbnail preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("youtube_thumbnail_preview")
                )
            } else {
                Text(
                    text = "Optional JPG/PNG cover image (< 2 MB). Requires your YouTube channel to have custom thumbnails enabled.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            OutlinedButton(
                onClick = onPickThumbnail,
                enabled = enabled,
                border = BorderStroke(1.dp, PurplePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("youtube_pick_thumbnail_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = PurplePrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedThumbnailUri.isBlank()) "Select Thumbnail Image" else "Change Thumbnail",
                    color = PurplePrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun YouTubeUploadProgressCard(
    progressPercent: Int,
    stepLabel: String,
    onCancelClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("youtube_upload_progress_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePurple),
        border = BorderStroke(1.5.dp, PurplePrimary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Uploading to Word & Light TV",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary
                )
                Text(
                    text = "${progressPercent.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = PurpleDark
                )
            }

            LinearProgressIndicator(
                progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                color = PurplePrimary,
                trackColor = BackgroundWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Text(
                text = stepLabel,
                style = MaterialTheme.typography.bodySmall,
                color = PurpleDark
            )

            OutlinedButton(
                onClick = onCancelClick,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("youtube_cancel_upload_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancel Upload", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun YouTubeUploadSuccessCard(
    state: YouTubeUploadUiState.Success,
    onWatchVideo: () -> Unit,
    onCopyLink: () -> Unit,
    onUploadAnother: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("youtube_upload_success_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePurple),
        border = BorderStroke(2.dp, GoldAccent)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Upload succeeded",
                    tint = PurplePrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Video Uploaded to YouTube!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = PurplePrimary
                )
            }

            Text(
                text = state.visibilityNote,
                style = MaterialTheme.typography.bodySmall,
                color = PurpleDark
            )

            Surface(
                color = BackgroundWhite,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.videoUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PurplePrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onWatchVideo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = GoldAccent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("youtube_watch_uploaded_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Watch on YouTube", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCopyLink,
                    border = BorderStroke(1.dp, PurplePrimary),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("youtube_copy_link_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        tint = PurplePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", color = PurplePrimary, fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = onUploadAnother,
                modifier = Modifier
                    .align(Alignment.End)
                    .minimumInteractiveComponentSize()
                    .testTag("youtube_upload_another_button")
            ) {
                Text("Upload Another Video", color = PurplePrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun YouTubeUploadCancelledCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("youtube_upload_cancelled_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePurple),
        border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = null,
                tint = PurplePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = PurpleDark,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = PurplePrimary)
            }
        }
    }
}

@Composable
private fun YouTubeUploadErrorCard(
    state: YouTubeUploadUiState.Error,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onReauthorize: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("youtube_upload_error_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Upload error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = PurpleDark
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.requiresReauth) {
                    Button(
                        onClick = onReauthorize,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurplePrimary,
                            contentColor = GoldAccent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_reauth_button")
                    ) {
                        Text("Re-authorize YouTube", fontWeight = FontWeight.Bold)
                    }
                }
                if (canRetry) {
                    OutlinedButton(
                        onClick = onRetry,
                        border = BorderStroke(1.dp, PurplePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_retry_upload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry Upload", color = PurplePrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun inspectSelectedVideo(context: Context, uriOrPath: String): SelectedVideoSummary {
    val trimmed = uriOrPath.trim()
    if (trimmed.startsWith("content://", ignoreCase = true)) {
        val uri = Uri.parse(trimmed)
        var name = ""
        var size = -1L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx).orEmpty()
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        val mime = context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() } ?: "video/mp4"
        return SelectedVideoSummary(
            uriOrPath = trimmed,
            displayName = name.ifBlank { uri.lastPathSegment ?: "Selected Video.mp4" },
            sizeBytes = size,
            mimeType = mime
        )
    }

    val file = File(trimmed.removePrefix("file://").trim())
    return SelectedVideoSummary(
        uriOrPath = trimmed,
        displayName = file.name.ifBlank { "Selected Video.mp4" },
        sizeBytes = if (file.exists()) file.length() else -1L,
        mimeType = "video/mp4"
    )
}

private fun formatReadableSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

private tailrec fun Context.findYouTubeHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findYouTubeHostActivity()
    else -> null
}

@Composable
fun YouTubePublishScreen(
    onNavigateBack: () -> Unit,
    onOpenConnectedChannels: () -> Unit,
    publishingRepositoryOverride: PublishingRepository? = null,
    initialVideoUriForTest: String = "",
    initialThumbnailUriForTest: String = ""
) {
    YouTubeUploadScreen(
        onNavigateBack = onNavigateBack,
        onOpenConnectedChannels = onOpenConnectedChannels,
        publishingRepositoryOverride = publishingRepositoryOverride,
        initialVideoUriForTest = initialVideoUriForTest,
        initialThumbnailUriForTest = initialThumbnailUriForTest
    )
}


