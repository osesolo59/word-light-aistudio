package com.aistudio.aimusic.wltv.jqr8x.ui.studio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.aimusic.wltv.jqr8x.data.media.Media3SlideshowExporter
import com.aistudio.aimusic.wltv.jqr8x.data.media.PhotoImportResult
import com.aistudio.aimusic.wltv.jqr8x.data.media.SaveToDeviceResult
import com.aistudio.aimusic.wltv.jqr8x.data.media.SlideshowExportOutput
import com.aistudio.aimusic.wltv.jqr8x.data.media.SlideshowExportRequest
import com.aistudio.aimusic.wltv.jqr8x.data.media.StudioMediaHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.BilingualCaptionMode
import com.aistudio.aimusic.wltv.jqr8x.data.model.MusicVideoProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.PhotoSlideshowDraft
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowAspectRatio
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowTransition
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.model.UploadedPhotoItem
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoCodecOption
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoFrameRate
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoResolutionPreset
import com.aistudio.aimusic.wltv.jqr8x.data.model.WordLightBrandingOptions
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface StudioUiState {
    object Idle : StudioUiState
    object Processing : StudioUiState
    data class ExportingMp4(val progressPercent: Int) : StudioUiState
    data class VideoSuccess(val project: VideoProject, val exportedOutput: SlideshowExportOutput? = null) : StudioUiState
    data class MusicVideoSuccess(val project: MusicVideoProject, val exportedOutput: SlideshowExportOutput? = null) : StudioUiState
    data class Error(val message: String) : StudioUiState
}

class StudioViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<StudioUiState>(StudioUiState.Idle)
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _photos = MutableStateFlow<List<UploadedPhotoItem>>(emptyList())
    val photos: StateFlow<List<UploadedPhotoItem>> = _photos.asStateFlow()

    private val _librarySongs = MutableStateFlow<List<Song>>(emptyList())
    val librarySongs: StateFlow<List<Song>> = _librarySongs.asStateFlow()

    private val _libraryMixes = MutableStateFlow<List<AudioMixProject>>(emptyList())
    val libraryMixes: StateFlow<List<AudioMixProject>> = _libraryMixes.asStateFlow()

    private val _mediaFeedback = MutableStateFlow<String?>(null)
    val mediaFeedback: StateFlow<String?> = _mediaFeedback.asStateFlow()

    private val _pendingDocumentSaveFileName = MutableStateFlow<String?>(null)
    val pendingDocumentSaveFileName: StateFlow<String?> = _pendingDocumentSaveFileName.asStateFlow()

    private val _lastExportedVideoPath = MutableStateFlow("")
    val lastExportedVideoPath: StateFlow<String> = _lastExportedVideoPath.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMySongs().collectLatest { items ->
                _librarySongs.value = items
            }
        }
        viewModelScope.launch {
            repository.getMyAudioMixes().collectLatest { mixes ->
                _libraryMixes.value = mixes
            }
        }
    }

    fun loadSavedDraftIfPresent(context: Context, studioType: String): PhotoSlideshowDraft? {
        val loaded = StudioMediaHelper.loadDraft(context, studioType) ?: return null
        if (_photos.value.isEmpty() && loaded.photos.isNotEmpty()) {
            _photos.value = loaded.photos
        }
        if (_lastExportedVideoPath.value.isBlank() && loaded.lastExportedVideoPath.isNotBlank()) {
            _lastExportedVideoPath.value = loaded.lastExportedVideoPath
        }
        return loaded
    }

    fun persistDraft(
        context: Context,
        studioType: String,
        title: String = "",
        prompt: String = "",
        bibleVerse: String = "",
        captions: String = "",
        templateName: String = "",
        aspectRatio: SlideshowAspectRatio = SlideshowAspectRatio.LANDSCAPE_16_9,
        resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.FHD_1080P,
        frameRate: VideoFrameRate = VideoFrameRate.FPS_30,
        videoCodec: VideoCodecOption = VideoCodecOption.H264,
        branding: WordLightBrandingOptions = WordLightBrandingOptions(),
        transition: SlideshowTransition = SlideshowTransition.FADE,
        durationSecondsPerPhoto: Int = 3,
        audioSourceLabel: String = "",
        audioUriString: String = "",
        captionCues: List<TimedBilingualCue> = emptyList(),
        captionDisplayMode: BilingualCaptionMode = BilingualCaptionMode.BOTH
    ) {
        StudioMediaHelper.saveDraft(
            context = context,
            draft = PhotoSlideshowDraft(
                studioType = studioType,
                title = title,
                prompt = prompt,
                bibleVerse = bibleVerse,
                captions = captions,
                templateName = templateName,
                aspectRatio = aspectRatio,
                resolutionPreset = resolutionPreset,
                frameRate = frameRate,
                videoCodec = videoCodec,
                branding = branding,
                transition = transition,
                durationSecondsPerPhoto = durationSecondsPerPhoto,
                audioSourceLabel = audioSourceLabel,
                audioUriString = audioUriString,
                photos = _photos.value,
                captionCues = captionCues,
                captionDisplayMode = captionDisplayMode,
                lastExportedVideoPath = _lastExportedVideoPath.value
            )
        )
    }

    fun importPickedPhotos(
        context: Context,
        uris: List<Uri>,
        studioType: String,
        onUpdated: () -> Unit = {}
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            when (val result = StudioMediaHelper.importPhotosFromUris(context, uris, studioType)) {
                is PhotoImportResult.Success -> {
                    _photos.update { current ->
                        StudioMediaHelper.ensureCoverExists(current + result.imported)
                    }
                    _mediaFeedback.value = buildString {
                        append("Added ${result.imported.size} photo(s)")
                        if (result.skippedUnsupported > 0) {
                            append(" (${result.skippedUnsupported} skipped — only JPG, JPEG, PNG, WebP supported)")
                        }
                    }
                    onUpdated()
                }
                is PhotoImportResult.Error -> {
                    _mediaFeedback.value = result.message
                }
            }
        }
    }

    fun setPhotosForTest(items: List<UploadedPhotoItem>) {
        _photos.value = StudioMediaHelper.ensureCoverExists(items)
    }

    fun movePhotoLeft(photoId: String, onUpdated: () -> Unit = {}) {
        _photos.update { StudioMediaHelper.movePhotoLeft(it, photoId) }
        onUpdated()
    }

    fun movePhotoRight(photoId: String, onUpdated: () -> Unit = {}) {
        _photos.update { StudioMediaHelper.movePhotoRight(it, photoId) }
        onUpdated()
    }

    fun removePhoto(photoId: String, onUpdated: () -> Unit = {}) {
        _photos.update { StudioMediaHelper.removePhoto(it, photoId) }
        _mediaFeedback.value = "Removed photo from storyboard"
        onUpdated()
    }

    fun setCoverPhoto(photoId: String, onUpdated: () -> Unit = {}) {
        _photos.update { StudioMediaHelper.setCoverPhoto(it, photoId) }
        _mediaFeedback.value = "Updated cover image"
        onUpdated()
    }

    fun importCustomAudio(
        context: Context,
        uri: Uri,
        studioType: String,
        onImported: (displayName: String, localUriString: String) -> Unit
    ) {
        viewModelScope.launch {
            val imported = StudioMediaHelper.importAudioFromUri(context, uri, studioType)
            if (imported != null) {
                _mediaFeedback.value = "Audio added: ${imported.first}"
                onImported(imported.first, imported.second)
            } else {
                _mediaFeedback.value = "Could not read selected audio file"
            }
        }
    }

    fun generateVideo(prompt: String, templateName: String, title: String = "") {
        viewModelScope.launch {
            _uiState.value = StudioUiState.Processing
            try {
                val project = repository.generateVideoViaBackend(prompt, templateName, title)
                _uiState.value = StudioUiState.VideoSuccess(project)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = StudioUiState.Error(e.localizedMessage ?: "Video generation failed.")
            }
        }
    }

    fun exportPhotoVideo(
        context: Context,
        title: String,
        prompt: String,
        bibleVerse: String,
        captions: String,
        templateName: String,
        aspectRatio: SlideshowAspectRatio,
        transition: SlideshowTransition,
        durationSecondsPerPhoto: Int,
        audioUriString: String = "",
        audioSourceLabel: String = "",
        captionCues: List<TimedBilingualCue> = emptyList(),
        captionDisplayMode: BilingualCaptionMode = BilingualCaptionMode.BOTH,
        resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.FHD_1080P,
        frameRate: VideoFrameRate = VideoFrameRate.FPS_30,
        videoCodec: VideoCodecOption = VideoCodecOption.H264,
        branding: WordLightBrandingOptions = WordLightBrandingOptions()
    ) {
        val currentPhotos = _photos.value
        if (currentPhotos.isEmpty()) {
            _uiState.value = StudioUiState.Error("Please upload at least one JPG, PNG, or WebP photo first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = StudioUiState.ExportingMp4(0)
            try {
                val output = Media3SlideshowExporter.exportSlideshowMp4(
                    context = context,
                    request = SlideshowExportRequest(
                        studioType = "VIDEO",
                        title = title,
                        bibleVerse = bibleVerse,
                        captions = captions,
                        photos = currentPhotos,
                        aspectRatio = aspectRatio,
                        transition = transition,
                        durationSecondsPerPhoto = durationSecondsPerPhoto,
                        audioUriString = audioUriString,
                        captionCues = captionCues,
                        captionDisplayMode = captionDisplayMode,
                        resolutionPreset = resolutionPreset,
                        frameRate = frameRate,
                        videoCodec = videoCodec,
                        branding = branding
                    ),
                    onProgress = { pct ->
                        if (_uiState.value is StudioUiState.ExportingMp4) {
                            _uiState.value = StudioUiState.ExportingMp4(pct)
                        }
                    }
                )
                _lastExportedVideoPath.value = output.outputFilePath
                persistDraft(
                    context = context,
                    studioType = "VIDEO",
                    title = title,
                    prompt = prompt,
                    bibleVerse = bibleVerse,
                    captions = captions,
                    templateName = templateName,
                    aspectRatio = aspectRatio,
                    resolutionPreset = resolutionPreset,
                    frameRate = frameRate,
                    videoCodec = videoCodec,
                    branding = branding,
                    transition = transition,
                    durationSecondsPerPhoto = durationSecondsPerPhoto,
                    audioSourceLabel = audioSourceLabel,
                    audioUriString = audioUriString,
                    captionCues = captionCues,
                    captionDisplayMode = captionDisplayMode
                )
                val project = repository.generateVideoViaBackend(
                    prompt = prompt,
                    templateName = templateName.ifBlank { "Photo Slideshow (${currentPhotos.size} photos)" },
                    title = title,
                    aspectRatio = aspectRatio.ratioLabel,
                    captions = listOf(bibleVerse.trim(), captions.trim()).filter { it.isNotBlank() }.joinToString(" • "),
                    transitionStyle = transition.label,
                    durationPerImageSec = durationSecondsPerPhoto,
                    coverPhotoUri = StudioMediaHelper.coverPhotoUri(currentPhotos),
                    photoUris = currentPhotos.map { it.uriString },
                    localVideoPath = output.outputFilePath,
                    captionCues = captionCues,
                    captionDisplayMode = captionDisplayMode.name
                )
                _uiState.value = StudioUiState.VideoSuccess(project, output)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = StudioUiState.Error(e.localizedMessage ?: "Failed to export MP4 slideshow.")
            }
        }
    }

    fun createMusicVideo(songId: String, videoId: String, bibleVerse: String, title: String) {
        viewModelScope.launch {
            _uiState.value = StudioUiState.Processing
            try {
                val project = repository.createMusicVideoViaBackend(songId, videoId, bibleVerse, title)
                _uiState.value = StudioUiState.MusicVideoSuccess(project)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = StudioUiState.Error(e.localizedMessage ?: "Music video creation failed.")
            }
        }
    }

    fun exportPhotoMusicVideo(
        context: Context,
        title: String,
        songLabel: String,
        audioUriString: String,
        bibleVerse: String,
        captions: String,
        backgroundLabel: String,
        aspectRatio: SlideshowAspectRatio,
        transition: SlideshowTransition,
        durationSecondsPerPhoto: Int,
        captionCues: List<TimedBilingualCue> = emptyList(),
        captionDisplayMode: BilingualCaptionMode = BilingualCaptionMode.BOTH,
        resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.FHD_1080P,
        frameRate: VideoFrameRate = VideoFrameRate.FPS_30,
        videoCodec: VideoCodecOption = VideoCodecOption.H264,
        branding: WordLightBrandingOptions = WordLightBrandingOptions()
    ) {
        val currentPhotos = _photos.value
        if (currentPhotos.isEmpty()) {
            _uiState.value = StudioUiState.Error("Please upload at least one JPG, PNG, or WebP photo first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = StudioUiState.ExportingMp4(0)
            try {
                val output = Media3SlideshowExporter.exportSlideshowMp4(
                    context = context,
                    request = SlideshowExportRequest(
                        studioType = "MUSIC_VIDEO",
                        title = title,
                        bibleVerse = bibleVerse,
                        captions = captions,
                        photos = currentPhotos,
                        aspectRatio = aspectRatio,
                        transition = transition,
                        durationSecondsPerPhoto = durationSecondsPerPhoto,
                        audioUriString = audioUriString,
                        captionCues = captionCues,
                        captionDisplayMode = captionDisplayMode,
                        resolutionPreset = resolutionPreset,
                        frameRate = frameRate,
                        videoCodec = videoCodec,
                        branding = branding
                    ),
                    onProgress = { pct ->
                        if (_uiState.value is StudioUiState.ExportingMp4) {
                            _uiState.value = StudioUiState.ExportingMp4(pct)
                        }
                    }
                )
                _lastExportedVideoPath.value = output.outputFilePath
                persistDraft(
                    context = context,
                    studioType = "MUSIC_VIDEO",
                    title = title,
                    bibleVerse = bibleVerse,
                    captions = captions,
                    templateName = backgroundLabel,
                    aspectRatio = aspectRatio,
                    resolutionPreset = resolutionPreset,
                    frameRate = frameRate,
                    videoCodec = videoCodec,
                    branding = branding,
                    transition = transition,
                    durationSecondsPerPhoto = durationSecondsPerPhoto,
                    audioSourceLabel = songLabel,
                    audioUriString = audioUriString,
                    captionCues = captionCues,
                    captionDisplayMode = captionDisplayMode
                )
                val project = repository.createMusicVideoViaBackend(
                    songId = songLabel.ifBlank { "Custom Audio" },
                    videoId = backgroundLabel.ifBlank { "Uploaded Photos (${currentPhotos.size})" },
                    bibleVerse = bibleVerse,
                    title = title,
                    aspectRatio = aspectRatio.ratioLabel,
                    captions = captions,
                    transitionStyle = transition.label,
                    durationPerImageSec = durationSecondsPerPhoto,
                    coverPhotoUri = StudioMediaHelper.coverPhotoUri(currentPhotos),
                    photoUris = currentPhotos.map { it.uriString },
                    localVideoPath = output.outputFilePath,
                    captionCues = captionCues,
                    captionDisplayMode = captionDisplayMode.name
                )
                _uiState.value = StudioUiState.MusicVideoSuccess(project, output)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = StudioUiState.Error(e.localizedMessage ?: "Failed to render MP4 music video.")
            }
        }
    }

    fun saveVideoToDevice(context: Context, filePath: String, title: String) {
        viewModelScope.launch {
            when (val res = StudioMediaHelper.saveExportedVideoToDevice(context, filePath, title)) {
                is SaveToDeviceResult.SavedToMediaStore -> {
                    _mediaFeedback.value = "Saved MP4 to device: ${res.displayPath}"
                }
                is SaveToDeviceResult.RequiresDocumentPicker -> {
                    _pendingDocumentSaveFileName.value = res.suggestedFileName
                }
                is SaveToDeviceResult.Error -> {
                    _mediaFeedback.value = res.message
                }
            }
        }
    }

    fun completeDocumentPickerSave(context: Context, sourcePath: String, destUri: Uri) {
        _pendingDocumentSaveFileName.value = null
        viewModelScope.launch {
            val ok = StudioMediaHelper.copyVideoToUri(context, sourcePath, destUri)
            _mediaFeedback.value = if (ok) {
                "MP4 video saved to selected device folder"
            } else {
                "Unable to save MP4 to selected location"
            }
        }
    }

    fun dismissPendingDocumentSave() {
        _pendingDocumentSaveFileName.value = null
    }

    fun clearFeedback() {
        _mediaFeedback.value = null
    }

    fun notifyFeedback(message: String) {
        _mediaFeedback.value = message
    }

    fun resetState() {
        _uiState.value = StudioUiState.Idle
    }
}
