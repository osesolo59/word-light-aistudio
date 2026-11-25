package com.aistudio.aimusic.wltv.jqr8x.data.media

import androidx.annotation.OptIn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.common.util.UnstableApi
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowAspectRatio
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowTransition
import com.aistudio.aimusic.wltv.jqr8x.data.model.UploadedPhotoItem
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoCodecOption
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoFrameRate
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoResolutionPreset
import com.aistudio.aimusic.wltv.jqr8x.data.model.WordLightBrandingOptions
import com.aistudio.aimusic.wltv.jqr8x.ui.studio.ProVideoQualityAndBrandingSection
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProVideoBrandingAndCodecTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aspectRatios_resolveExactFullHdAnd4kDimensions() {
        assertEquals(1920 to 1080, SlideshowAspectRatio.LANDSCAPE_16_9.dimensionsFor(VideoResolutionPreset.FHD_1080P))
        assertEquals(1080 to 1920, SlideshowAspectRatio.PORTRAIT_9_16.dimensionsFor(VideoResolutionPreset.FHD_1080P))
        assertEquals(1080 to 1080, SlideshowAspectRatio.SQUARE_1_1.dimensionsFor(VideoResolutionPreset.FHD_1080P))

        assertEquals(3840 to 2160, SlideshowAspectRatio.LANDSCAPE_16_9.dimensionsFor(VideoResolutionPreset.UHD_4K))
        assertEquals(2160 to 3840, SlideshowAspectRatio.PORTRAIT_9_16.dimensionsFor(VideoResolutionPreset.UHD_4K))
        assertEquals(2160 to 2160, SlideshowAspectRatio.SQUARE_1_1.dimensionsFor(VideoResolutionPreset.UHD_4K))
    }

    @Test
    fun deviceCapabilities_gate4k60fpsAndHevcHonestly() {
        val restricted = DeviceVideoCapabilities(
            supports1080p = true,
            supports4kUhd = false,
            supports60Fps1080p = false,
            supports60Fps4k = false,
            supportsHevc = false
        )
        assertTrue(restricted.isResolutionSupported(VideoResolutionPreset.FHD_1080P, SlideshowAspectRatio.LANDSCAPE_16_9))
        assertFalse(restricted.isResolutionSupported(VideoResolutionPreset.UHD_4K, SlideshowAspectRatio.LANDSCAPE_16_9))
        assertFalse(restricted.isFrameRateSupported(VideoFrameRate.FPS_60, VideoResolutionPreset.FHD_1080P))

        val hardwarePro = DeviceVideoCapabilities(
            supports1080p = true,
            supports4kUhd = true,
            supports60Fps1080p = true,
            supports60Fps4k = false,
            supportsHevc = true
        )
        assertTrue(hardwarePro.isResolutionSupported(VideoResolutionPreset.UHD_4K, SlideshowAspectRatio.LANDSCAPE_16_9, VideoCodecOption.HEVC))
        assertTrue(hardwarePro.isFrameRateSupported(VideoFrameRate.FPS_60, VideoResolutionPreset.FHD_1080P))
        assertFalse(hardwarePro.isFrameRateSupported(VideoFrameRate.FPS_60, VideoResolutionPreset.UHD_4K))
    }

    @OptIn(UnstableApi::class)
    @Test
    fun media3Exporter_rendersWordLightBrandingBitmapsTimelinePhasesAnd60FpsComposition() {
        val branding = WordLightBrandingOptions()
        assertEquals("Word & Light TV", branding.channelName)
        assertEquals("Christians Strong Foundation Ministry", branding.ministryName)
        assertEquals("Psalm 119:105", branding.scriptureReference)

        // 15 second total duration: [0..3s) Opening -> [3..12s) Main -> [12..15s] Closing
        assertEquals(VideoTimelinePhase.OPENING_SLATE, Media3SlideshowExporter.resolveTimelinePhase(1_200L, 15_000L, branding))
        assertEquals(VideoTimelinePhase.MAIN_CONTENT, Media3SlideshowExporter.resolveTimelinePhase(6_000L, 15_000L, branding))
        assertEquals(VideoTimelinePhase.CLOSING_CREDITS, Media3SlideshowExporter.resolveTimelinePhase(13_500L, 15_000L, branding))

        // Bitrate scaling increases cleanly from 720p -> 1080p -> 4K and for 60 FPS
        val br1080_30 = Media3SlideshowExporter.resolveTargetBitrate(VideoResolutionPreset.FHD_1080P, VideoFrameRate.FPS_30)
        val br1080_60 = Media3SlideshowExporter.resolveTargetBitrate(VideoResolutionPreset.FHD_1080P, VideoFrameRate.FPS_60)
        val br4k_30 = Media3SlideshowExporter.resolveTargetBitrate(VideoResolutionPreset.UHD_4K, VideoFrameRate.FPS_30)
        assertTrue(br1080_30 >= 12_000_000)
        assertTrue(br1080_60 > br1080_30)
        assertTrue(br4k_30 > br1080_60)

        val logoBmp = Media3SlideshowExporter.createWordLightLogoBitmap(1920)
        val openBmp = Media3SlideshowExporter.createOpeningTitleBitmap("Light Unto My Path", "Psalm 119:105", branding, 1920)
        val closeBmp = Media3SlideshowExporter.createClosingCreditsBitmap("Light Unto My Path", branding, 1920)
        assertTrue(logoBmp.width in 220..620 && logoBmp.height >= 52)
        assertTrue(openBmp.width >= 1000 && openBmp.height >= 110)
        assertTrue(closeBmp.width >= 1000 && closeBmp.height >= 110)
        logoBmp.recycle()
        openBmp.recycle()
        closeBmp.recycle()

        val photos = listOf(
            UploadedPhotoItem("1", "file:///tmp/p1.jpg", "p1.jpg", "image/jpeg", true),
            UploadedPhotoItem("2", "file:///tmp/p2.png", "p2.png", "image/png", false),
            UploadedPhotoItem("3", "file:///tmp/p3.webp", "p3.webp", "image/webp", false)
        )
        val comp = Media3SlideshowExporter.buildComposition(
            request = SlideshowExportRequest(
                studioType = "VIDEO",
                title = "Psalm 119 Tribute",
                bibleVerse = "Psalm 119:105",
                captions = "Word & Light Praise",
                photos = photos,
                aspectRatio = SlideshowAspectRatio.LANDSCAPE_16_9,
                transition = SlideshowTransition.ZOOM,
                durationSecondsPerPhoto = 4,
                resolutionPreset = VideoResolutionPreset.FHD_1080P,
                frameRate = VideoFrameRate.FPS_60,
                videoCodec = VideoCodecOption.H264,
                branding = branding
            ),
            overlayBitmap = null,
            audioDurationMs = null
        )
        assertEquals(60, comp.sequences[0].editedMediaItems[0].frameRate)
        assertEquals(1, comp.effects.videoEffects.size)
    }

    @Test
    fun proVideoSection_disablesUnsupported4kHevcAndEnablesSupportedOptions() {
        var selectedResolution = VideoResolutionPreset.FHD_1080P
        var selectedBranding = WordLightBrandingOptions()
        val limitedCaps = DeviceVideoCapabilities(
            supports1080p = true,
            supports4kUhd = false,
            supports60Fps1080p = true,
            supports60Fps4k = false,
            supportsHevc = false
        )

        composeRule.setContent {
            WordAndLightTheme {
                ProVideoQualityAndBrandingSection(
                    tagPrefix = "test",
                    aspectRatio = SlideshowAspectRatio.LANDSCAPE_16_9,
                    resolutionPreset = selectedResolution,
                    onResolutionChange = { selectedResolution = it },
                    frameRate = VideoFrameRate.FPS_30,
                    onFrameRateChange = {},
                    videoCodec = VideoCodecOption.H264,
                    onVideoCodecChange = {},
                    branding = selectedBranding,
                    onBrandingChange = { selectedBranding = it },
                    capabilitiesOverride = limitedCaps
                )
            }
        }

        composeRule.onNodeWithTag("test_pro_broadcast_card").assertIsDisplayed()
        composeRule.onNodeWithTag("test_resolution_fhd_1080p").assertIsEnabled()
        composeRule.onNodeWithTag("test_resolution_uhd_4k").assertIsNotEnabled()
        composeRule.onNodeWithTag("test_fps_60").assertIsEnabled()
        composeRule.onNodeWithTag("test_codec_hevc").assertIsNotEnabled()

        composeRule.onNodeWithTag("test_brand_logo_switch").performClick()
        assertFalse(selectedBranding.includeLogoWatermark)
    }
}
