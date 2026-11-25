package com.aistudio.aimusic.wltv.jqr8x.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val credits: Int = 0,
    val appearance: String = "System Default", // "Light", "Dark", "System Default"
    val language: String = "English",
    val isPublicProfile: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Song(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val prompt: String = "",
    val lyrics: String = "",
    val genre: String = "",
    val language: String = "English",
    val mood: String = "",
    val tempo: String = "",
    val instruments: List<String> = emptyList(),
    val vocalStyle: String = "",
    val audioUrl: String = "",
    val isPublic: Boolean = false,
    val isDemo: Boolean = false,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val allowRemix: Boolean = true,
    val likeStatus: Int = 0, // 1 for like, -1 for dislike, 0 for neutral
    val creatorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TimedBilingualCue(
    val id: String = "",
    val startSec: Int = 0,
    val endSec: Int = 4,
    val sectionLabel: String = "",
    val englishText: String = "",
    val twiText: String = ""
)

enum class BilingualCaptionMode(val label: String) {
    BOTH("English + Twi"),
    ENGLISH_ONLY("English Only"),
    TWI_ONLY("Twi Only");

    companion object {
        fun fromLabel(value: String?): BilingualCaptionMode =
            entries.firstOrNull { it.label == value || it.name == value } ?: BOTH
    }
}

@IgnoreExtraProperties
data class Lyric(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val lyrics: String = "",
    val topic: String = "",
    val genre: String = "",
    val mood: String = "",
    val language: String = "English",
    val scriptureReference: String = "",
    val userInstructions: String = "",
    val generationSource: String = "DEMO",
    val sectionLanguages: Map<String, String> = emptyMap(),
    val captionCues: List<TimedBilingualCue> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val prompt: String = "",
    val videoUrl: String = "",
    val templateName: String = "",
    val aspectRatio: String = "16:9",
    val captions: String = "",
    val transitionStyle: String = "Fade",
    val durationPerImageSec: Int = 3,
    val photoCount: Int = 0,
    val coverPhotoUri: String = "",
    val photoUris: List<String> = emptyList(),
    val localVideoPath: String = "",
    val captionCues: List<TimedBilingualCue> = emptyList(),
    val captionDisplayMode: String = "BOTH",
    val isDemo: Boolean = true,
    val creatorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MusicVideoProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val songId: String = "",
    val videoId: String = "",
    val bibleVerse: String = "",
    val videoUrl: String = "",
    val aspectRatio: String = "16:9",
    val captions: String = "",
    val transitionStyle: String = "Fade",
    val durationPerImageSec: Int = 3,
    val photoCount: Int = 0,
    val coverPhotoUri: String = "",
    val photoUris: List<String> = emptyList(),
    val localVideoPath: String = "",
    val captionCues: List<TimedBilingualCue> = emptyList(),
    val captionDisplayMode: String = "BOTH",
    val isDemo: Boolean = true,
    val creatorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class SlideshowAspectRatio(
    val label: String,
    val ratioLabel: String,
    val width: Int,
    val height: Int
) {
    LANDSCAPE_16_9("Landscape 16:9", "16:9", 1920, 1080),
    PORTRAIT_9_16("Portrait 9:16", "9:16", 1080, 1920),
    SQUARE_1_1("Square 1:1", "1:1", 1080, 1080);

    fun dimensionsFor(resolution: VideoResolutionPreset): Pair<Int, Int> = when (this) {
        LANDSCAPE_16_9 -> when (resolution) {
            VideoResolutionPreset.HD_720P -> 1280 to 720
            VideoResolutionPreset.FHD_1080P -> 1920 to 1080
            VideoResolutionPreset.UHD_4K -> 3840 to 2160
        }
        PORTRAIT_9_16 -> when (resolution) {
            VideoResolutionPreset.HD_720P -> 720 to 1280
            VideoResolutionPreset.FHD_1080P -> 1080 to 1920
            VideoResolutionPreset.UHD_4K -> 2160 to 3840
        }
        SQUARE_1_1 -> when (resolution) {
            VideoResolutionPreset.HD_720P -> 720 to 720
            VideoResolutionPreset.FHD_1080P -> 1080 to 1080
            VideoResolutionPreset.UHD_4K -> 2160 to 2160
        }
    }

    companion object {
        fun fromRatioLabel(value: String?): SlideshowAspectRatio =
            entries.firstOrNull { it.ratioLabel == value } ?: LANDSCAPE_16_9
    }
}

enum class VideoResolutionPreset(val label: String, val shortLabel: String) {
    HD_720P("HD 720p", "720p"),
    FHD_1080P("Full HD 1080p", "1080p"),
    UHD_4K("4K Ultra HD", "4K UHD");

    companion object {
        fun fromNameOrLabel(value: String?): VideoResolutionPreset =
            entries.firstOrNull { it.name == value || it.label == value || it.shortLabel == value } ?: FHD_1080P
    }
}

enum class VideoFrameRate(val fps: Int, val label: String) {
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS");

    companion object {
        fun fromFps(fps: Int): VideoFrameRate = if (fps >= 60) FPS_60 else FPS_30
    }
}

enum class VideoCodecOption(val label: String, val mimeType: String) {
    H264("H.264 MP4", "video/avc"),
    HEVC("H.265 / HEVC", "video/hevc");

    companion object {
        fun fromName(value: String?): VideoCodecOption =
            entries.firstOrNull { it.name == value || it.label == value } ?: H264
    }
}

data class WordLightBrandingOptions(
    val includeLogoWatermark: Boolean = true,
    val includeOpeningTitle: Boolean = true,
    val includeClosingCredits: Boolean = true,
    val openingDurationSec: Int = 3,
    val closingDurationSec: Int = 3,
    val channelName: String = "Word & Light TV",
    val ministryName: String = "Christians Strong Foundation Ministry",
    val scriptureReference: String = "Psalm 119:105"
)

enum class SlideshowTransition(val label: String) {
    NONE("Clean Cut"),
    FADE("Fade"),
    ZOOM("Smooth Zoom"),
    SLIDE_PAN("Gentle Pan");

    companion object {
        fun fromLabel(value: String?): SlideshowTransition =
            entries.firstOrNull { it.label == value || it.name == value } ?: FADE
    }
}

data class UploadedPhotoItem(
    val id: String,
    val uriString: String,
    val displayName: String = "Photo",
    val mimeType: String = "image/jpeg",
    val isCover: Boolean = false
)

data class PhotoSlideshowDraft(
    val studioType: String, // "VIDEO" or "MUSIC_VIDEO"
    val title: String = "",
    val prompt: String = "",
    val bibleVerse: String = "",
    val captions: String = "",
    val templateName: String = "",
    val aspectRatio: SlideshowAspectRatio = SlideshowAspectRatio.LANDSCAPE_16_9,
    val resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.FHD_1080P,
    val frameRate: VideoFrameRate = VideoFrameRate.FPS_30,
    val videoCodec: VideoCodecOption = VideoCodecOption.H264,
    val branding: WordLightBrandingOptions = WordLightBrandingOptions(),
    val transition: SlideshowTransition = SlideshowTransition.FADE,
    val durationSecondsPerPhoto: Int = 3,
    val audioSourceLabel: String = "",
    val audioUriString: String = "",
    val photos: List<UploadedPhotoItem> = emptyList(),
    val captionCues: List<TimedBilingualCue> = emptyList(),
    val captionDisplayMode: BilingualCaptionMode = BilingualCaptionMode.BOTH,
    val lastExportedVideoPath: String = "",
    val lastExportedMediaStoreUri: String = ""
)

enum class AudioTrackRole(val label: String) {
    GENERAL("Mix"),
    VOCAL("Vocal"),
    INSTRUMENTAL("Instrumental")
}

enum class AudioExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val sampleRateHz: Int,
    val bitRateKbps: Int
) {
    WAV_44100("WAV 44.1 kHz", "wav", "audio/wav", 44_100, 0),
    WAV_48000("WAV 48 kHz", "wav", "audio/wav", 48_000, 0),
    MP3_192("MP3 192 kbps", "mp3", "audio/mpeg", 44_100, 192),
    MP3_256("MP3 256 kbps", "mp3", "audio/mpeg", 44_100, 256),
    MP3_320("MP3 320 kbps", "mp3", "audio/mpeg", 44_100, 320)
}

enum class MasteringPreset(val label: String) {
    WARM_GOSPEL("Warm Gospel"),
    BROADCAST_CRISP("Broadcast Crisp"),
    LIVE_SANCTUARY("Live Sanctuary"),
    FLAT_NATURAL("Natural / Flat"),
    CUSTOM("Custom")
}

data class AudioMasteringSettings(
    val enabled: Boolean = true,
    val preset: MasteringPreset = MasteringPreset.WARM_GOSPEL,
    val eqLowGainDb: Float = 2.0f,
    val eqMidGainDb: Float = 1.5f,
    val eqHighGainDb: Float = 2.0f,
    val noiseReductionEnabled: Boolean = true,
    val noiseReductionAmount: Float = 0.35f,
    val compressorEnabled: Boolean = true,
    val compressorAmount: Float = 0.45f,
    val reverbEnabled: Boolean = false,
    val reverbMix: Float = 0.18f,
    val vocalBalanceOffset: Float = 0f, // -1f = Instrumental focus, 0f = Balanced, +1f = Vocal focus
    val masterGainDb: Float = 0f
) {
    companion object {
        fun fromPreset(preset: MasteringPreset, vocalBalanceOffset: Float = 0f): AudioMasteringSettings =
            when (preset) {
                MasteringPreset.WARM_GOSPEL -> AudioMasteringSettings(
                    enabled = true,
                    preset = preset,
                    eqLowGainDb = 2.5f,
                    eqMidGainDb = 1.5f,
                    eqHighGainDb = 1.8f,
                    noiseReductionEnabled = true,
                    noiseReductionAmount = 0.35f,
                    compressorEnabled = true,
                    compressorAmount = 0.45f,
                    reverbEnabled = false,
                    reverbMix = 0.16f,
                    vocalBalanceOffset = vocalBalanceOffset
                )
                MasteringPreset.BROADCAST_CRISP -> AudioMasteringSettings(
                    enabled = true,
                    preset = preset,
                    eqLowGainDb = 1.0f,
                    eqMidGainDb = 3.0f,
                    eqHighGainDb = 3.5f,
                    noiseReductionEnabled = true,
                    noiseReductionAmount = 0.50f,
                    compressorEnabled = true,
                    compressorAmount = 0.65f,
                    reverbEnabled = false,
                    reverbMix = 0.10f,
                    vocalBalanceOffset = vocalBalanceOffset
                )
                MasteringPreset.LIVE_SANCTUARY -> AudioMasteringSettings(
                    enabled = true,
                    preset = preset,
                    eqLowGainDb = 2.0f,
                    eqMidGainDb = 2.0f,
                    eqHighGainDb = 2.2f,
                    noiseReductionEnabled = true,
                    noiseReductionAmount = 0.30f,
                    compressorEnabled = true,
                    compressorAmount = 0.50f,
                    reverbEnabled = true,
                    reverbMix = 0.24f,
                    vocalBalanceOffset = vocalBalanceOffset
                )
                MasteringPreset.FLAT_NATURAL -> AudioMasteringSettings(
                    enabled = false,
                    preset = preset,
                    eqLowGainDb = 0f,
                    eqMidGainDb = 0f,
                    eqHighGainDb = 0f,
                    noiseReductionEnabled = false,
                    noiseReductionAmount = 0f,
                    compressorEnabled = false,
                    compressorAmount = 0f,
                    reverbEnabled = false,
                    reverbMix = 0f,
                    vocalBalanceOffset = vocalBalanceOffset
                )
                MasteringPreset.CUSTOM -> AudioMasteringSettings(
                    enabled = true,
                    preset = preset,
                    vocalBalanceOffset = vocalBalanceOffset
                )
            }
    }
}

@IgnoreExtraProperties
data class AudioMixTrack(
    val id: String = "",
    val displayName: String = "",
    val formatLabel: String = "MP3",
    val localUriString: String = "",
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val offsetMs: Long = 0L,
    val role: AudioTrackRole = AudioTrackRole.GENERAL
)

@IgnoreExtraProperties
data class AudioMixProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val language: String = "Mixed English + Twi",
    val sectionLanguages: Map<String, String> = emptyMap(),
    val lyrics: String = "",
    val captionCues: List<TimedBilingualCue> = emptyList(),
    val tracks: List<AudioMixTrack> = emptyList(),
    val totalDurationMs: Long = 0L,
    val localMp3Path: String = "",
    val isDemo: Boolean = true,
    val creatorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class StudioAudioMixDraft(
    val transferId: String,
    val title: String,
    val language: String,
    val localMp3Uri: String,
    val lyrics: String = "",
    val captionCues: List<TimedBilingualCue> = emptyList()
)

@IgnoreExtraProperties
data class Playlist(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val songIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CreativeIdea(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "",
    val title: String = "",
    val summary: String = "",
    val topic: String = "",
    val genre: String = "Gospel",
    val mood: String = "Uplifting",
    val language: String = "English",
    val tempo: String = "Moderate (95 BPM)",
    val instruments: List<String> = emptyList(),
    val vocalStyle: String = "Gospel Choir",
    val lyricsPreview: String = "",
    val videoPrompt: String = "",
    val videoTemplateName: String = "",
    val bibleVerse: String = "",
    val targetStudio: String = "AUDIO", // AUDIO, LYRICS, VIDEO, MUSIC_VIDEO
    val isDemo: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class StudioIdeaDraft(
    val transferId: String,
    val idea: CreativeIdea
)
