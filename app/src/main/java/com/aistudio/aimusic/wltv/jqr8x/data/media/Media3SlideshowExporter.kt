package com.aistudio.aimusic.wltv.jqr8x.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.aistudio.aimusic.wltv.jqr8x.data.model.BilingualCaptionMode
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowAspectRatio
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowTransition
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.model.UploadedPhotoItem
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoCodecOption
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoFrameRate
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoResolutionPreset
import com.aistudio.aimusic.wltv.jqr8x.data.model.WordLightBrandingOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SlideshowExportRequest(
    val studioType: String,
    val title: String,
    val bibleVerse: String,
    val captions: String,
    val photos: List<UploadedPhotoItem>,
    val aspectRatio: SlideshowAspectRatio,
    val transition: SlideshowTransition,
    val durationSecondsPerPhoto: Int,
    val audioUriString: String = "",
    val captionCues: List<TimedBilingualCue> = emptyList(),
    val captionDisplayMode: BilingualCaptionMode = BilingualCaptionMode.BOTH,
    val resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.FHD_1080P,
    val frameRate: VideoFrameRate = VideoFrameRate.FPS_30,
    val videoCodec: VideoCodecOption = VideoCodecOption.H264,
    val branding: WordLightBrandingOptions = WordLightBrandingOptions()
)

data class SlideshowExportOutput(
    val outputFilePath: String,
    val fileSizeBytes: Long,
    val totalDurationMs: Long,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val codecLabel: String = "H.264 MP4"
)

internal enum class VideoTimelinePhase {
    OPENING_SLATE,
    MAIN_CONTENT,
    CLOSING_CREDITS
}

@OptIn(UnstableApi::class)
object Media3SlideshowExporter {

    private val ROYAL_PURPLE = Color.parseColor("#4A148C")
    private val ROYAL_PURPLE_DEEP = Color.parseColor("#2A0854")
    private val GOLD = Color.parseColor("#FFD700")
    private val WHITE = Color.WHITE

    private class FadeRgbMatrix(
        private val itemDurationUs: Long,
        private val fadeDurationUs: Long = 500_000L
    ) : RgbMatrix {
        private val matrix = FloatArray(16)

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val clampedFade = minOf(fadeDurationUs, itemDurationUs / 3L).coerceAtLeast(1L)
            val fadeIn = (presentationTimeUs.toFloat() / clampedFade.toFloat()).coerceIn(0f, 1f)
            val remaining = (itemDurationUs - presentationTimeUs).coerceAtLeast(0L)
            val fadeOut = (remaining.toFloat() / clampedFade.toFloat()).coerceIn(0f, 1f)
            val factor = minOf(fadeIn, fadeOut)
            android.opengl.Matrix.setIdentityM(matrix, 0)
            matrix[0] = factor
            matrix[5] = factor
            matrix[10] = factor
            return matrix
        }
    }

    private class DynamicProStudioOverlay(
        private val request: SlideshowExportRequest,
        private val targetWidth: Int,
        private val totalVideoDurationMs: Long,
        private val staticFallbackOverlay: Bitmap?,
        private val overlaySettings: OverlaySettings
    ) : BitmapOverlay() {
        private val cachedCueBitmaps = mutableMapOf<Int, Bitmap>()
        private var openingBitmap: Bitmap? = null
        private var closingBitmap: Bitmap? = null
        private var emptyBitmap: Bitmap? = null

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val elapsedMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
            when (resolveTimelinePhase(elapsedMs, totalVideoDurationMs, request.branding)) {
                VideoTimelinePhase.OPENING_SLATE -> {
                    return openingBitmap ?: createOpeningTitleBitmap(
                        title = request.title,
                        bibleVerse = request.bibleVerse,
                        branding = request.branding,
                        targetWidth = targetWidth
                    ).also { openingBitmap = it }
                }
                VideoTimelinePhase.CLOSING_CREDITS -> {
                    return closingBitmap ?: createClosingCreditsBitmap(
                        title = request.title,
                        branding = request.branding,
                        targetWidth = targetWidth
                    ).also { closingBitmap = it }
                }
                VideoTimelinePhase.MAIN_CONTENT -> {
                    if (request.captionCues.isNotEmpty()) {
                        val cueIdx = resolveActiveCueIndex(request.captionCues, elapsedMs)
                        cachedCueBitmaps[cueIdx]?.let { return it }
                        val activeCue = request.captionCues.getOrNull(cueIdx)
                        val bmp = createCueOverlayBitmap(
                            title = request.title,
                            bibleVerse = request.bibleVerse,
                            fallbackCaptions = request.captions,
                            activeCue = activeCue,
                            mode = request.captionDisplayMode,
                            targetWidth = targetWidth
                        ) ?: getOrCreateEmptyBitmap()
                        cachedCueBitmaps[cueIdx] = bmp
                        return bmp
                    }
                    return staticFallbackOverlay
                        ?: createTextOverlayBitmap(
                            title = request.title,
                            bibleVerse = request.bibleVerse,
                            captions = request.captions,
                            targetWidth = targetWidth
                        )
                        ?: getOrCreateEmptyBitmap()
                }
            }
        }

        private fun getOrCreateEmptyBitmap(): Bitmap =
            emptyBitmap ?: Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also { emptyBitmap = it }

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

        override fun release() {
            super.release()
            cachedCueBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
            cachedCueBitmaps.clear()
            openingBitmap?.let { if (!it.isRecycled) it.recycle() }
            closingBitmap?.let { if (!it.isRecycled) it.recycle() }
            emptyBitmap?.let { if (!it.isRecycled) it.recycle() }
            openingBitmap = null
            closingBitmap = null
            emptyBitmap = null
        }
    }

    internal fun resolveTargetBitrate(
        resolution: VideoResolutionPreset,
        frameRate: VideoFrameRate
    ): Int {
        val base = when (resolution) {
            VideoResolutionPreset.HD_720P -> 6_500_000
            VideoResolutionPreset.FHD_1080P -> 14_000_000
            VideoResolutionPreset.UHD_4K -> 36_000_000
        }
        return if (frameRate == VideoFrameRate.FPS_60) (base * 1.45f).toInt() else base
    }

    internal fun resolveTimelinePhase(
        elapsedMs: Long,
        totalDurationMs: Long,
        branding: WordLightBrandingOptions
    ): VideoTimelinePhase {
        if (totalDurationMs < 5_000L) return VideoTimelinePhase.MAIN_CONTENT
        val openWindow = if (branding.includeOpeningTitle) {
            minOf(branding.openingDurationSec * 1_000L, totalDurationMs / 3L)
        } else 0L
        val closeWindow = if (branding.includeClosingCredits) {
            minOf(branding.closingDurationSec * 1_000L, totalDurationMs / 3L)
        } else 0L

        return when {
            openWindow > 0L && elapsedMs < openWindow -> VideoTimelinePhase.OPENING_SLATE
            closeWindow > 0L && elapsedMs >= (totalDurationMs - closeWindow) -> VideoTimelinePhase.CLOSING_CREDITS
            else -> VideoTimelinePhase.MAIN_CONTENT
        }
    }

    internal fun buildComposition(
        request: SlideshowExportRequest,
        overlayBitmap: Bitmap?,
        audioDurationMs: Long? = null
    ): Composition {
        val durationPerPhotoMs = request.durationSecondsPerPhoto.coerceIn(2, 10) * 1000L
        val durationPerPhotoUs = durationPerPhotoMs * 1_000L
        val totalVideoDurationMs = durationPerPhotoMs * request.photos.size.coerceAtLeast(1)
        val (outWidth, outHeight) = request.aspectRatio.dimensionsFor(request.resolutionPreset)
        val targetFps = request.frameRate.fps

        val photoEditedItems = request.photos.mapIndexed { index, photo ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(photo.uriString))
                .setImageDurationMs(durationPerPhotoMs)
                .build()

            val effects = buildList<Effect> {
                add(
                    Presentation.createForWidthAndHeight(
                        outWidth,
                        outHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
                when (request.transition) {
                    SlideshowTransition.NONE -> Unit
                    SlideshowTransition.FADE -> add(FadeRgbMatrix(durationPerPhotoUs))
                    SlideshowTransition.ZOOM -> add(
                        MatrixTransformation { presentationTimeUs ->
                            val progress = (presentationTimeUs.toFloat() / durationPerPhotoUs.toFloat())
                                .coerceIn(0f, 1f)
                            val scale = 1.0f + (0.12f * progress)
                            Matrix().apply { postScale(scale, scale) }
                        }
                    )
                    SlideshowTransition.SLIDE_PAN -> add(
                        MatrixTransformation { presentationTimeUs ->
                            val progress = (presentationTimeUs.toFloat() / durationPerPhotoUs.toFloat())
                                .coerceIn(0f, 1f)
                            val direction = if (index % 2 == 0) 1f else -1f
                            val translateX = (-0.045f + 0.09f * progress) * direction
                            Matrix().apply {
                                postScale(1.10f, 1.10f)
                                postTranslate(translateX, 0f)
                            }
                        }
                    )
                }
            }

            EditedMediaItem.Builder(mediaItem)
                .setFrameRate(targetFps)
                .setEffects(Effects(emptyList(), effects))
                .build()
        }

        val videoSequence = EditedMediaItemSequence.Builder(photoEditedItems).build()
        val sequences = mutableListOf(videoSequence)

        if (request.audioUriString.isNotBlank()) {
            val audioMediaItemBuilder = MediaItem.Builder()
                .setUri(Uri.parse(request.audioUriString))
            if (audioDurationMs != null && audioDurationMs > totalVideoDurationMs) {
                audioMediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(totalVideoDurationMs)
                        .build()
                )
            }
            val audioEditedItem = EditedMediaItem.Builder(audioMediaItemBuilder.build())
                .setRemoveVideo(true)
                .build()
            val shouldLoop = audioDurationMs == null || audioDurationMs < totalVideoDurationMs
            val audioSequence = EditedMediaItemSequence.Builder(listOf(audioEditedItem))
                .setIsLooping(shouldLoop)
                .build()
            sequences.add(audioSequence)
        }

        val compositionBuilder = Composition.Builder(sequences)
        val overlays = buildList {
            val bottomBannerSettings = OverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, -0.76f)
                .setOverlayFrameAnchor(0f, 0f)
                .build()

            val needsDynamicOverlay = request.captionCues.isNotEmpty() ||
                request.branding.includeOpeningTitle ||
                request.branding.includeClosingCredits
            if (needsDynamicOverlay) {
                add(
                    DynamicProStudioOverlay(
                        request = request,
                        targetWidth = outWidth,
                        totalVideoDurationMs = totalVideoDurationMs,
                        staticFallbackOverlay = overlayBitmap,
                        overlaySettings = bottomBannerSettings
                    )
                )
            } else if (overlayBitmap != null) {
                add(BitmapOverlay.createStaticBitmapOverlay(overlayBitmap, bottomBannerSettings))
            }

            if (request.branding.includeLogoWatermark) {
                val logoBitmap = createWordLightLogoBitmap(outWidth, request.branding.channelName)
                val logoSettings = OverlaySettings.Builder()
                    .setBackgroundFrameAnchor(0.72f, 0.86f)
                    .setOverlayFrameAnchor(0f, 0f)
                    .build()
                add(BitmapOverlay.createStaticBitmapOverlay(logoBitmap, logoSettings))
            }
        }
        if (overlays.isNotEmpty()) {
            compositionBuilder.setEffects(Effects(emptyList(), listOf(OverlayEffect(overlays))))
        }
        return compositionBuilder.build()
    }

    internal fun resolveActiveCueIndex(cues: List<TimedBilingualCue>, elapsedMs: Long): Int {
        if (cues.isEmpty()) return -1
        val sec = (elapsedMs / 1000L).toInt()
        val exact = cues.indexOfFirst { sec in it.startSec until it.endSec }
        if (exact >= 0) return exact
        val lastEnd = cues.last().endSec.coerceAtLeast(1)
        if (sec >= lastEnd) {
            val wrappedSec = sec % lastEnd
            val wrapped = cues.indexOfFirst { wrappedSec in it.startSec until it.endSec }
            if (wrapped >= 0) return wrapped
        }
        return 0
    }

    internal fun formatCueLines(
        cue: TimedBilingualCue?,
        mode: BilingualCaptionMode,
        fallbackCaptions: String
    ): Pair<String, String> {
        if (cue == null) return "" to fallbackCaptions.trim()
        val eng = cue.englishText.trim()
        val twi = cue.twiText.trim()
        return when (mode) {
            BilingualCaptionMode.BOTH -> {
                val primary = if (twi.isNotEmpty()) twi else eng
                val secondary = if (twi.isNotEmpty() && eng.isNotEmpty()) eng else ""
                primary to secondary
            }
            BilingualCaptionMode.ENGLISH_ONLY -> eng.ifBlank { twi } to ""
            BilingualCaptionMode.TWI_ONLY -> twi.ifBlank { eng } to ""
        }
    }

    internal fun createCueOverlayBitmap(
        title: String,
        bibleVerse: String,
        fallbackCaptions: String,
        activeCue: TimedBilingualCue?,
        mode: BilingualCaptionMode,
        targetWidth: Int
    ): Bitmap? {
        val (primaryLine, secondaryLine) = formatCueLines(activeCue, mode, fallbackCaptions)
        val combinedCaption = listOf(primaryLine, secondaryLine)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return createTextOverlayBitmap(
            title = title,
            bibleVerse = bibleVerse,
            captions = combinedCaption,
            targetWidth = targetWidth
        )
    }

    internal fun createWordLightLogoBitmap(
        targetWidth: Int,
        channelName: String = "Word & Light TV"
    ): Bitmap {
        val pillWidth = (targetWidth * 0.24f).toInt().coerceIn(220, 620)
        val pillHeight = (pillWidth * 0.24f).toInt().coerceIn(52, 130)
        val bitmap = Bitmap.createBitmap(pillWidth, pillHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, pillWidth.toFloat(), pillHeight.toFloat(),
                ROYAL_PURPLE, ROYAL_PURPLE_DEEP, Shader.TileMode.CLAMP
            )
        }
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            style = Paint.Style.STROKE
            strokeWidth = (pillHeight * 0.055f).coerceIn(2f, 6f)
        }
        val radius = pillHeight * 0.45f
        canvas.drawRoundRect(RectF(2f, 2f, pillWidth - 2f, pillHeight - 2f), radius, radius, bgPaint)
        canvas.drawRoundRect(RectF(2f, 2f, pillWidth - 2f, pillHeight - 2f), radius, radius, goldPaint)

        // Small gold emblem dot + light rays
        val emblemX = pillHeight * 0.48f
        val emblemY = pillHeight * 0.5f
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            style = Paint.Style.FILL
        }
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ROYAL_PURPLE_DEEP
            strokeWidth = (pillHeight * 0.06f).coerceIn(2f, 5f)
            strokeCap = Paint.Cap.ROUND
        }
        val emblemR = pillHeight * 0.26f
        canvas.drawCircle(emblemX, emblemY, emblemR, circlePaint)
        canvas.drawLine(emblemX, emblemY - emblemR * 0.6f, emblemX, emblemY + emblemR * 0.6f, crossPaint)
        canvas.drawLine(emblemX - emblemR * 0.5f, emblemY - emblemR * 0.1f, emblemX + emblemR * 0.5f, emblemY - emblemR * 0.1f, crossPaint)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            textSize = (pillHeight * 0.36f).coerceIn(18f, 44f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelY = (pillHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(channelName.ifBlank { "Word & Light TV" }, emblemX + emblemR + 12f, labelY, textPaint)
        return bitmap
    }

    internal fun createOpeningTitleBitmap(
        title: String,
        bibleVerse: String,
        branding: WordLightBrandingOptions,
        targetWidth: Int
    ): Bitmap {
        val header = branding.channelName.ifBlank { "Word & Light TV" }
        val mainTitle = title.trim().ifBlank { "Gospel Tribute & Worship" }
        val scripture = bibleVerse.trim().ifBlank { "${branding.scriptureReference} — Your word is a lamp to my feet" }
        val footer = branding.ministryName.ifBlank { "Christians Strong Foundation Ministry" }

        return renderBrandedCardBitmap(
            header = "$header • Opening",
            title = mainTitle,
            highlight = scripture,
            subtitle = footer,
            targetWidth = targetWidth
        )
    }

    internal fun createClosingCreditsBitmap(
        title: String,
        branding: WordLightBrandingOptions,
        targetWidth: Int
    ): Bitmap {
        val header = branding.channelName.ifBlank { "Word & Light TV" }
        val mainTitle = branding.ministryName.ifBlank { "Christians Strong Foundation Ministry" }
        val scripture = "${branding.scriptureReference} — Your word is a lamp to my feet and a light to my path."
        val footer = if (title.isNotBlank()) "Featured: $title • Word & Light AI STUDIO" else "Produced in Word & Light AI STUDIO"

        return renderBrandedCardBitmap(
            header = "$header • Closing Credits",
            title = mainTitle,
            highlight = scripture,
            subtitle = footer,
            targetWidth = targetWidth
        )
    }

    private fun renderBrandedCardBitmap(
        header: String,
        title: String,
        highlight: String,
        subtitle: String,
        targetWidth: Int
    ): Bitmap {
        val bannerWidth = (targetWidth * 0.88f).toInt().coerceAtLeast(360)
        val hPad = (targetWidth * 0.03f).toInt().coerceIn(28, 84)
        val textWidth = (bannerWidth - hPad * 2).coerceAtLeast(220)

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            textSize = (targetWidth * 0.021f).coerceIn(16f, 44f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            textSize = (targetWidth * 0.034f).coerceIn(24f, 70f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val highlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            textSize = (targetWidth * 0.023f).coerceIn(18f, 48f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F3E5F5")
            textSize = (targetWidth * 0.020f).coerceIn(15f, 42f)
        }

        fun layout(text: String, paint: TextPaint): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(2f, 1.08f)
                .build()

        val layouts = listOf(
            layout(header.take(80), headerPaint),
            layout(title.take(90), titlePaint),
            layout(highlight.take(180), highlightPaint),
            layout(subtitle.take(120), subtitlePaint)
        )
        val spacing = (targetWidth * 0.009f).toInt().coerceIn(8, 22)
        val vPad = (targetWidth * 0.018f).toInt().coerceIn(18, 44)
        val totalTextHeight = layouts.sumOf { it.height } + (layouts.size - 1) * spacing
        val bannerHeight = (totalTextHeight + vPad * 2).coerceAtLeast(110)

        val bitmap = Bitmap.createBitmap(bannerWidth, bannerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, bannerWidth.toFloat(), bannerHeight.toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, bannerWidth.toFloat(), bannerHeight.toFloat(),
                Color.argb(232, 74, 20, 140),
                Color.argb(232, 34, 6, 70),
                Shader.TileMode.CLAMP
            )
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (targetWidth * 0.003f).coerceIn(3f, 8f)
            color = GOLD
        }
        val corner = (targetWidth * 0.02f).coerceIn(18f, 48f)
        canvas.drawRoundRect(rect, corner, corner, bgPaint)
        canvas.drawRoundRect(RectF(3f, 3f, bannerWidth - 3f, bannerHeight - 3f), corner, corner, borderPaint)

        var yOffset = vPad.toFloat()
        layouts.forEach { block ->
            canvas.save()
            canvas.translate(hPad.toFloat(), yOffset)
            block.draw(canvas)
            canvas.restore()
            yOffset += block.height + spacing.toFloat()
        }
        return bitmap
    }

    internal fun createTextOverlayBitmap(
        title: String,
        bibleVerse: String,
        captions: String,
        targetWidth: Int
    ): Bitmap? {
        val cleanTitle = title.trim()
        val cleanVerse = bibleVerse.trim()
        val cleanCaptions = captions.trim()
        if (cleanTitle.isEmpty() && cleanVerse.isEmpty() && cleanCaptions.isEmpty()) return null

        val bannerWidth = (targetWidth * 0.88f).toInt().coerceAtLeast(360)
        val horizontalPadding = (targetWidth * 0.028f).toInt().coerceIn(28, 72)
        val textWidth = (bannerWidth - horizontalPadding * 2).coerceAtLeast(200)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            textSize = (targetWidth * 0.030f).coerceIn(22f, 68f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val versePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            textSize = (targetWidth * 0.023f).coerceIn(18f, 52f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F0FF")
            textSize = (targetWidth * 0.022f).coerceIn(17f, 50f)
        }

        fun staticLayout(text: String, paint: TextPaint): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(2f, 1.08f)
                .build()

        val layouts = buildList {
            if (cleanTitle.isNotEmpty()) add(staticLayout(cleanTitle.take(80), titlePaint))
            if (cleanVerse.isNotEmpty()) add(staticLayout(cleanVerse.take(180), versePaint))
            if (cleanCaptions.isNotEmpty()) add(staticLayout(cleanCaptions.take(260), captionPaint))
        }

        val spacing = (targetWidth * 0.008f).toInt().coerceIn(8, 18)
        val vPad = (targetWidth * 0.016f).toInt().coerceIn(18, 36)
        val totalTextHeight = layouts.sumOf { it.height } + (layouts.size - 1) * spacing
        val bannerHeight = (totalTextHeight + vPad * 2).coerceAtLeast(88)

        val bitmap = Bitmap.createBitmap(bannerWidth, bannerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, bannerWidth.toFloat(), bannerHeight.toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 74, 20, 140)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (targetWidth * 0.0025f).coerceIn(3f, 7f)
            color = Color.argb(235, 255, 215, 0)
        }
        val corner = (targetWidth * 0.018f).coerceIn(20f, 44f)
        canvas.drawRoundRect(rect, corner, corner, bgPaint)
        canvas.drawRoundRect(RectF(2f, 2f, bannerWidth - 2f, bannerHeight - 2f), corner, corner, borderPaint)

        var yOffset = vPad.toFloat()
        layouts.forEach { layout ->
            canvas.save()
            canvas.translate(horizontalPadding.toFloat(), yOffset)
            layout.draw(canvas)
            canvas.restore()
            yOffset += layout.height + spacing.toFloat()
        }
        return bitmap
    }

    suspend fun exportSlideshowMp4(
        context: Context,
        request: SlideshowExportRequest,
        onProgress: (Int) -> Unit = {}
    ): SlideshowExportOutput {
        require(request.photos.isNotEmpty()) { "Please upload at least one photo first." }

        val (outWidth, outHeight) = request.aspectRatio.dimensionsFor(request.resolutionPreset)
        val targetBitrate = resolveTargetBitrate(request.resolutionPreset, request.frameRate)
        val outputFile = StudioMediaHelper.getExportedOutputFile(
            context = context,
            studioType = request.studioType,
            title = request.title
        )
        val audioDurationMs = withContext(Dispatchers.IO) {
            probeAudioDurationMs(context, request.audioUriString)
        }
        val needsDynamic = request.captionCues.isNotEmpty() ||
            request.branding.includeOpeningTitle ||
            request.branding.includeClosingCredits
        val overlayBitmap = if (!needsDynamic) {
            withContext(Dispatchers.Default) {
                createTextOverlayBitmap(
                    title = request.title,
                    bibleVerse = request.bibleVerse,
                    captions = request.captions,
                    targetWidth = outWidth
                )
            }
        } else {
            null
        }
        val durationPerPhotoMs = request.durationSecondsPerPhoto.coerceIn(2, 10) * 1000L
        val expectedDurationMs = durationPerPhotoMs * request.photos.size

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                var progressRunnable: Runnable? = null
                var finished = false

                fun stopPolling() {
                    progressRunnable?.let { handler.removeCallbacks(it) }
                    progressRunnable = null
                }

                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setEnableFallback(true)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(targetBitrate)
                            .build()
                    )
                    .build()

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(request.videoCodec.mimeType)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (finished) return
                            finished = true
                            stopPolling()
                            overlayBitmap?.recycle()
                            onProgress(100)
                            val size = if (exportResult.fileSizeBytes > 0L) {
                                exportResult.fileSizeBytes
                            } else {
                                outputFile.length()
                            }
                            if (continuation.isActive) {
                                continuation.resume(
                                    SlideshowExportOutput(
                                        outputFilePath = outputFile.absolutePath,
                                        fileSizeBytes = size,
                                        totalDurationMs = exportResult.durationMs.takeIf { it > 0L } ?: expectedDurationMs,
                                        width = outWidth,
                                        height = outHeight,
                                        fps = request.frameRate.fps,
                                        codecLabel = request.videoCodec.label
                                    )
                                )
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (finished) return
                            finished = true
                            stopPolling()
                            overlayBitmap?.recycle()
                            outputFile.delete()
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException(
                                        exportException.localizedMessage
                                            ?: "On-device video export failed (${exportException.errorCodeName}).",
                                        exportException
                                    )
                                )
                            }
                        }
                    })
                    .build()

                val composition = buildComposition(
                    request = request,
                    overlayBitmap = overlayBitmap,
                    audioDurationMs = audioDurationMs
                )

                transformer.start(composition, outputFile.absolutePath)

                val holder = ProgressHolder()
                val poller = object : Runnable {
                    override fun run() {
                        if (finished) return
                        val state = transformer.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.coerceIn(0, 99))
                        }
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED && !finished) {
                            handler.postDelayed(this, 250L)
                        }
                    }
                }
                progressRunnable = poller
                handler.postDelayed(poller, 250L)

                continuation.invokeOnCancellation {
                    finished = true
                    stopPolling()
                    transformer.cancel()
                    overlayBitmap?.recycle()
                    outputFile.delete()
                }
            }
        }
    }

    private fun probeAudioDurationMs(context: Context, audioUriString: String): Long? {
        if (audioUriString.isBlank()) return null
        val uri = Uri.parse(audioUriString)
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else if (uri.scheme == "content") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(audioUriString, emptyMap())
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
        }
    }
}
