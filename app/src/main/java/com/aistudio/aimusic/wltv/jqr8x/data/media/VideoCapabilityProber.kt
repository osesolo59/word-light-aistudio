package com.aistudio.aimusic.wltv.jqr8x.data.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowAspectRatio
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoCodecOption
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoFrameRate
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoResolutionPreset

data class DeviceVideoCapabilities(
    val supports1080p: Boolean = true,
    val supports4kUhd: Boolean = false,
    val supports60Fps1080p: Boolean = false,
    val supports60Fps4k: Boolean = false,
    val supportsHevc: Boolean = false,
    val h264EncoderName: String? = null,
    val hevcEncoderName: String? = null,
    val isProbedOnDevice: Boolean = false
) {
    fun isResolutionSupported(
        resolution: VideoResolutionPreset,
        aspectRatio: SlideshowAspectRatio,
        codec: VideoCodecOption = VideoCodecOption.H264
    ): Boolean = when (resolution) {
        VideoResolutionPreset.HD_720P -> true
        VideoResolutionPreset.FHD_1080P -> if (codec == VideoCodecOption.HEVC) supportsHevc && supports1080p else supports1080p
        VideoResolutionPreset.UHD_4K -> supports4kUhd && (codec != VideoCodecOption.HEVC || supportsHevc)
    }

    fun isFrameRateSupported(
        frameRate: VideoFrameRate,
        resolution: VideoResolutionPreset
    ): Boolean = when (frameRate) {
        VideoFrameRate.FPS_30 -> true
        VideoFrameRate.FPS_60 -> when (resolution) {
            VideoResolutionPreset.UHD_4K -> supports60Fps4k
            VideoResolutionPreset.HD_720P, VideoResolutionPreset.FHD_1080P -> supports60Fps1080p
        }
    }

    fun summaryText(): String = buildString {
        append(if (supports1080p) "1080p Ready" else "720p Standard")
        append(" • ")
        append(if (supports4kUhd) "4K HW Supported" else "4K Not Supported")
        append(" • ")
        append(if (supports60Fps1080p) "60 FPS Supported" else "30 FPS Max")
        append(" • ")
        append(if (supportsHevc) "H.265/HEVC Ready" else "H.264 Only")
    }
}

/**
 * Probes actual Android MediaCodecList video encoders on the running device so the app
 * never exposes or claims 4K, 60 FPS, or H.265/HEVC without real encoder capability.
 */
object VideoCapabilityProber {

    fun probe(): DeviceVideoCapabilities {
        return try {
            val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            val h264Encoders = codecInfos.filter { it.isEncoder && supportsMime(it, MediaFormat.MIMETYPE_VIDEO_AVC) }
            val hevcEncoders = codecInfos.filter { it.isEncoder && supportsMime(it, MediaFormat.MIMETYPE_VIDEO_HEVC) }

            val h264Preferred = selectPreferredEncoder(h264Encoders)
            val hevcPreferred = selectPreferredEncoder(hevcEncoders)

            val h264Caps = h264Preferred?.let { getVideoCaps(it, MediaFormat.MIMETYPE_VIDEO_AVC) }
            val hevcCaps = hevcPreferred?.let { getVideoCaps(it, MediaFormat.MIMETYPE_VIDEO_HEVC) }

            val supports1080p = (h264Caps?.isSizeSupported(1920, 1080) == true) ||
                (hevcCaps?.isSizeSupported(1920, 1080) == true) ||
                h264Encoders.isNotEmpty()

            val supports4kH264 = h264Preferred != null && isHardwareAccelerated(h264Preferred) &&
                (h264Caps?.isSizeSupported(3840, 2160) == true)
            val supports4kHevc = hevcPreferred != null && isHardwareAccelerated(hevcPreferred) &&
                (hevcCaps?.isSizeSupported(3840, 2160) == true)

            val supports60Fps1080p = (h264Caps?.areSizeAndRateSupported(1920, 1080, 60.0) == true) ||
                (hevcCaps?.areSizeAndRateSupported(1920, 1080, 60.0) == true)
            val supports60Fps4k = (supports4kH264 && h264Caps?.areSizeAndRateSupported(3840, 2160, 60.0) == true) ||
                (supports4kHevc && hevcCaps?.areSizeAndRateSupported(3840, 2160, 60.0) == true)

            DeviceVideoCapabilities(
                supports1080p = supports1080p,
                supports4kUhd = supports4kH264 || supports4kHevc,
                supports60Fps1080p = supports60Fps1080p,
                supports60Fps4k = supports60Fps4k,
                supportsHevc = hevcPreferred != null && isHardwareAccelerated(hevcPreferred),
                h264EncoderName = h264Preferred?.name,
                hevcEncoderName = hevcPreferred?.name,
                isProbedOnDevice = h264Encoders.isNotEmpty() || hevcEncoders.isNotEmpty()
            )
        } catch (_: Throwable) {
            // Safe conservative fallback when MediaCodecList is unavailable (e.g., plain JVM tests)
            DeviceVideoCapabilities(
                supports1080p = true,
                supports4kUhd = false,
                supports60Fps1080p = false,
                supports60Fps4k = false,
                supportsHevc = false,
                isProbedOnDevice = false
            )
        }
    }

    private fun supportsMime(info: MediaCodecInfo, mimeType: String): Boolean =
        info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }

    private fun getVideoCaps(info: MediaCodecInfo, mimeType: String): MediaCodecInfo.VideoCapabilities? =
        runCatching { info.getCapabilitiesForType(mimeType)?.videoCapabilities }.getOrNull()

    private fun isHardwareAccelerated(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated && !info.isSoftwareOnly
        } else {
            val lower = info.name.lowercase()
            !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.")
        }
    }

    private fun selectPreferredEncoder(candidates: List<MediaCodecInfo>): MediaCodecInfo? {
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { isHardwareAccelerated(it) } ?: candidates.first()
    }
}
