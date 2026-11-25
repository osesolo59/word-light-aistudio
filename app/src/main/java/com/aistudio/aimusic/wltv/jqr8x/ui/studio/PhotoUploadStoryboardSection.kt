package com.aistudio.aimusic.wltv.jqr8x.ui.studio

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.aistudio.aimusic.wltv.jqr8x.data.media.Media3SlideshowExporter
import com.aistudio.aimusic.wltv.jqr8x.data.media.SlideshowExportOutput
import com.aistudio.aimusic.wltv.jqr8x.data.media.StudioMediaHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.BilingualCaptionMode
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowAspectRatio
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowTransition
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.model.UploadedPhotoItem
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoCodecOption
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoFrameRate
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoResolutionPreset
import com.aistudio.aimusic.wltv.jqr8x.data.model.WordLightBrandingOptions
import com.aistudio.aimusic.wltv.jqr8x.ui.create.BilingualCaptionCuesEditorCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoUploadStoryboardSection(
    tagPrefix: String,
    photos: List<UploadedPhotoItem>,
    title: String,
    bibleVerse: String,
    captions: String,
    onBibleVerseChange: (String) -> Unit,
    onCaptionsChange: (String) -> Unit,
    aspectRatio: SlideshowAspectRatio,
    onAspectRatioChange: (SlideshowAspectRatio) -> Unit,
    transition: SlideshowTransition,
    onTransitionChange: (SlideshowTransition) -> Unit,
    durationSecondsPerPhoto: Int,
    onDurationChange: (Int) -> Unit,
    onPhotosPicked: (List<Uri>) -> Unit,
    onMoveLeft: (String) -> Unit,
    onMoveRight: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSetCover: (String) -> Unit,
    showBibleVerseField: Boolean = true,
    feedbackMessage: String? = null,
    onDismissFeedback: () -> Unit = {},
    captionCues: List<TimedBilingualCue> = emptyList(),
    onCaptionCuesChange: ((List<TimedBilingualCue>) -> Unit)? = null,
    captionDisplayMode: BilingualCaptionMode = BilingualCaptionMode.BOTH,
    onCaptionDisplayModeChange: ((BilingualCaptionMode) -> Unit)? = null,
    resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.FHD_1080P,
    onResolutionChange: ((VideoResolutionPreset) -> Unit)? = null,
    frameRate: VideoFrameRate = VideoFrameRate.FPS_30,
    onFrameRateChange: ((VideoFrameRate) -> Unit)? = null,
    videoCodec: VideoCodecOption = VideoCodecOption.H264,
    onVideoCodecChange: ((VideoCodecOption) -> Unit)? = null,
    branding: WordLightBrandingOptions = WordLightBrandingOptions(),
    onBrandingChange: ((WordLightBrandingOptions) -> Unit)? = null
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPhotosPicked(uris)
        }
    }

    var isPreviewPlaying by remember { mutableStateOf(false) }
    var activePreviewIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(photos.size, isPreviewPlaying, durationSecondsPerPhoto) {
        if (photos.isEmpty()) {
            isPreviewPlaying = false
            activePreviewIndex = 0
            return@LaunchedEffect
        }
        if (activePreviewIndex >= photos.size) {
            activePreviewIndex = 0
        }
        while (isPreviewPlaying && photos.isNotEmpty()) {
            delay(durationSecondsPerPhoto.coerceIn(2, 10) * 1000L)
            activePreviewIndex = (activePreviewIndex + 1) % photos.size
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_photo_upload_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Personal Photos & Slideshow",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Upload JPG, JPEG, PNG or WebP pictures from your gallery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (photos.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "${photos.size} ${if (photos.size == 1) "photo" else "photos"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("${tagPrefix}_upload_photo_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Upload Photo",
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (photos.isEmpty()) "Upload Photo" else "Add More Photos",
                    fontWeight = FontWeight.Bold
                )
            }

            if (feedbackMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = feedbackMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissFeedback) {
                            Text("OK")
                        }
                    }
                }
            }

            if (photos.isNotEmpty()) {
                val displayPhoto = if (isPreviewPlaying) {
                    photos.getOrElse(activePreviewIndex) { photos.first() }
                } else {
                    photos.getOrElse(activePreviewIndex) {
                        photos.firstOrNull { it.isCover } ?: photos.first()
                    }
                }

                // Live Aspect-Ratio Stage Preview
                val previewRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${tagPrefix}_slideshow_preview_stage"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = if (aspectRatio == SlideshowAspectRatio.PORTRAIT_9_16) 220.dp else 420.dp)
                            .fillMaxWidth()
                            .aspectRatio(previewRatio)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A0733))
                    ) {
                        AsyncImage(
                            model = Uri.parse(displayPhoto.uriString),
                            contentDescription = "Preview slide ${displayPhoto.displayName}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "${aspectRatio.ratioLabel} • ${transition.label} • ${durationSecondsPerPhoto}s/photo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        val elapsedMs = activePreviewIndex.toLong() * durationSecondsPerPhoto.coerceIn(2, 10) * 1000L
                        val activeCueIdx = Media3SlideshowExporter.resolveActiveCueIndex(captionCues, elapsedMs)
                        val activeCue = captionCues.getOrNull(activeCueIdx)
                        val (cuePrimary, cueSecondary) = Media3SlideshowExporter.formatCueLines(
                            cue = activeCue,
                            mode = captionDisplayMode,
                            fallbackCaptions = captions
                        )
                        val showOverlayCard = title.isNotBlank() || bibleVerse.isNotBlank() ||
                            cuePrimary.isNotBlank() || cueSecondary.isNotBlank()

                        if (showOverlayCard) {
                            Surface(
                                color = Color(0xCC300C5C),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp)
                                    .fillMaxWidth(0.92f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (title.isNotBlank()) {
                                        Text(
                                            text = title.trim(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (bibleVerse.isNotBlank()) {
                                        Text(
                                            text = bibleVerse.trim(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontStyle = FontStyle.Italic,
                                            color = Color(0xFFFFD700),
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (cuePrimary.isNotBlank()) {
                                        Text(
                                            text = cuePrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (cueSecondary.isNotBlank()) {
                                        Text(
                                            text = cueSecondary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFEBD9FF),
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { isPreviewPlaying = !isPreviewPlaying },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${tagPrefix}_preview_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPreviewPlaying) "Pause Preview" else "Preview Slideshow")
                    }
                    Text(
                        text = "Total: ${photos.size * durationSecondsPerPhoto}s",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Uploaded photo thumbnail strip with reorder, cover, remove
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${tagPrefix}_photo_preview_list")
                ) {
                    itemsIndexed(photos, key = { _, item -> item.id }) { index, photo ->
                        Card(
                            modifier = Modifier
                                .width(156.dp)
                                .testTag("${tagPrefix}_photo_item_$index"),
                            shape = RoundedCornerShape(12.dp),
                            border = if (photo.isCover) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(92.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1A0733))
                                ) {
                                    AsyncImage(
                                        model = Uri.parse(photo.uriString),
                                        contentDescription = "Uploaded photo ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = if (photo.isCover) "#${index + 1} COVER" else "#${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (photo.isCover) MaterialTheme.colorScheme.secondary else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemovePhoto(photo.id) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(36.dp)
                                            .testTag("${tagPrefix}_remove_photo_$index")
                                    ) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove photo ${index + 1}",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = photo.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onMoveLeft(photo.id) },
                                        enabled = index > 0,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("${tagPrefix}_move_left_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Move photo ${index + 1} earlier"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            activePreviewIndex = index
                                            onSetCover(photo.id)
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("${tagPrefix}_cover_$index")
                                    ) {
                                        Icon(
                                            imageVector = if (photo.isCover) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Set photo ${index + 1} as cover",
                                            tint = if (photo.isCover) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveRight(photo.id) },
                                        enabled = index < photos.lastIndex,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("${tagPrefix}_move_right_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Move photo ${index + 1} later"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showBibleVerseField) {
                OutlinedTextField(
                    value = bibleVerse,
                    onValueChange = onBibleVerseChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${tagPrefix}_bible_verse_input"),
                    label = { Text("Bible Verse Overlay (Optional)") },
                    placeholder = { Text("e.g. Psalm 23:1 — The Lord is my shepherd") },
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = captions,
                onValueChange = onCaptionsChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${tagPrefix}_captions_input"),
                label = { Text("Captions / English & Twi Text Overlay") },
                placeholder = { Text("Add English or Twi worship captions, dedication, or lyric line overlay") },
                minLines = 2
            )

            if (onCaptionDisplayModeChange != null) {
                Text(
                    text = "Video Caption Language Overlay",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BilingualCaptionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = captionDisplayMode == mode,
                            onClick = { onCaptionDisplayModeChange(mode) },
                            modifier = Modifier.testTag("${tagPrefix}_caption_mode_${mode.name.lowercase()}"),
                            label = { Text(mode.label) }
                        )
                    }
                }
            }

            if (onCaptionCuesChange != null) {
                BilingualCaptionCuesEditorCard(
                    tagPrefix = tagPrefix,
                    cues = captionCues,
                    onCuesChange = onCaptionCuesChange
                )
            }

            Text(
                text = "Aspect Ratio",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SlideshowAspectRatio.entries.forEach { ratio ->
                    val ratioTag = "${tagPrefix}_ratio_${ratio.ratioLabel.replace(":", "_")}"
                    FilterChip(
                        selected = aspectRatio == ratio,
                        onClick = { onAspectRatioChange(ratio) },
                        modifier = Modifier.testTag(ratioTag),
                        label = { Text(ratio.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }

            Text(
                text = "Transition Effect",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SlideshowTransition.entries.forEach { style ->
                    FilterChip(
                        selected = transition == style,
                        onClick = { onTransitionChange(style) },
                        modifier = Modifier.testTag("${tagPrefix}_transition_${style.name.lowercase()}"),
                        label = { Text(style.label) }
                    )
                }
            }

            Text(
                text = "Duration per Image",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(2, 3, 4, 5, 7, 10).forEach { seconds ->
                    FilterChip(
                        selected = durationSecondsPerPhoto == seconds,
                        onClick = { onDurationChange(seconds) },
                        modifier = Modifier.testTag("${tagPrefix}_duration_$seconds"),
                        label = { Text("${seconds}s") }
                    )
                }
            }

            if (onResolutionChange != null &&
                onFrameRateChange != null &&
                onVideoCodecChange != null &&
                onBrandingChange != null
            ) {
                ProVideoQualityAndBrandingSection(
                    tagPrefix = tagPrefix,
                    aspectRatio = aspectRatio,
                    resolutionPreset = resolutionPreset,
                    onResolutionChange = onResolutionChange,
                    frameRate = frameRate,
                    onFrameRateChange = onFrameRateChange,
                    videoCodec = videoCodec,
                    onVideoCodecChange = onVideoCodecChange,
                    branding = branding,
                    onBrandingChange = onBrandingChange
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioAudioPickerSection(
    tagPrefix: String,
    selectedAudioLabel: String,
    selectedAudioUri: String,
    librarySongs: List<Song>,
    presetLabels: List<String>,
    allowNoAudio: Boolean,
    onSelectPreset: (label: String, uri: String) -> Unit,
    onAudioFilePicked: (Uri) -> Unit,
    savedAudioMixes: List<AudioMixProject> = emptyList(),
    onMixSelected: ((AudioMixProject) -> Unit)? = null
) {
    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onAudioFilePicked(uri)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio & Song Selection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(
                    onClick = { audioLauncher.launch(arrayOf("audio/*")) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${tagPrefix}_upload_audio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = "Add Own Audio",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Audio")
                }
            }

            if (selectedAudioLabel.isNotBlank()) {
                Text(
                    text = "Selected Audio: $selectedAudioLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (allowNoAudio) {
                    FilterChip(
                        selected = selectedAudioLabel.isBlank() && selectedAudioUri.isBlank(),
                        onClick = { onSelectPreset("", "") },
                        modifier = Modifier.testTag("${tagPrefix}_audio_none"),
                        label = { Text("No Audio") }
                    )
                }

                savedAudioMixes.filter { it.localMp3Path.isNotBlank() }.take(6).forEach { mix ->
                    val extTag = if (mix.localMp3Path.endsWith(".wav", ignoreCase = true)) "Mastered WAV" else "Mastered MP3"
                    val mixLabel = "${mix.title.ifBlank { "Gospel Mix" }} ($extTag)"
                    val mixUri = Uri.fromFile(java.io.File(mix.localMp3Path.removePrefix("file://"))).toString()
                    FilterChip(
                        selected = selectedAudioLabel == mixLabel,
                        onClick = {
                            onSelectPreset(mixLabel, mixUri)
                            onMixSelected?.invoke(mix)
                        },
                        modifier = Modifier.testTag("${tagPrefix}_mix_${mix.id}"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(mixLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }

                librarySongs.take(6).forEach { song ->
                    val label = "${song.title.ifBlank { "Untitled Song" }} (${song.genre.ifBlank { "Gospel" }})"
                    FilterChip(
                        selected = selectedAudioLabel == label,
                        onClick = { onSelectPreset(label, song.audioUrl) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }

                presetLabels.forEach { preset ->
                    FilterChip(
                        selected = selectedAudioLabel == preset,
                        onClick = {
                            onSelectPreset(
                                preset,
                                "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
                            )
                        },
                        label = { Text(preset) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExportedVideoResultDialog(
    tagPrefix: String,
    title: String,
    subtitle: String,
    exportedOutput: SlideshowExportOutput,
    onSaveToDevice: () -> Unit,
    onShareVideo: () -> Unit,
    onGoToProjects: () -> Unit,
    onDismiss: () -> Unit,
    onDirectPublish: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val videoUri = remember(exportedOutput.outputFilePath) {
        Uri.fromFile(java.io.File(exportedOutput.outputFilePath))
    }
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
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
        title = { Text("MP4 Video Ready", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title.ifBlank { "Word & Light Photo Video" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val sizeMb = exportedOutput.fileSizeBytes / (1024f * 1024f)
                Text(
                    text = "${exportedOutput.width}×${exportedOutput.height} • ${exportedOutput.fps} FPS • ${exportedOutput.codecLabel} • ${String.format("%.2f", sizeMb)} MB • ${exportedOutput.totalDurationMs / 1000L}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveToDevice,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("${tagPrefix}_save_device_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", maxLines = 1)
                    }
                    Button(
                        onClick = onShareVideo,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("${tagPrefix}_share_video_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", maxLines = 1)
                    }
                }
                if (onDirectPublish != null) {
                    OutlinedButton(
                        onClick = onDirectPublish,
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("${tagPrefix}_direct_publish_button")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Publish to YouTube / Facebook / TikTok", maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGoToProjects,
                modifier = Modifier.testTag("${tagPrefix}_go_projects_button")
            ) {
                Text("Go to My Projects")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
