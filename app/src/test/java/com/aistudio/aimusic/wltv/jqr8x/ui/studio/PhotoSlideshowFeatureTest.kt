package com.aistudio.aimusic.wltv.jqr8x.ui.studio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.data.media.Media3SlideshowExporter
import com.aistudio.aimusic.wltv.jqr8x.data.media.PhotoImportResult
import com.aistudio.aimusic.wltv.jqr8x.data.media.SlideshowExportRequest
import com.aistudio.aimusic.wltv.jqr8x.data.media.StudioMediaHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.PhotoSlideshowDraft
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowAspectRatio
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowTransition
import com.aistudio.aimusic.wltv.jqr8x.data.model.UploadedPhotoItem
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoSlideshowFeatureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var testRepository: MusicRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "photo-ui-test-app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.aimusic.wltv.jqr8x")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build(),
                "photo-ui-test-app"
            ).also { initialized ->
                val db = FirebaseFirestore.getInstance(
                    initialized,
                    "ai-studio-android-wordligh-5ca335cd-2f1f-497a-b7bb-2021e1d6149e"
                )
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        val firestore = FirebaseFirestore.getInstance(
            app,
            "ai-studio-android-wordligh-5ca335cd-2f1f-497a-b7bb-2021e1d6149e"
        )
        val auth = FirebaseAuth.getInstance(app)
        auth.signOut()
        testRepository = MusicRepository(firestore, auth)
    }

    private fun createTempImageUri(
        context: Context,
        fileName: String,
        format: Bitmap.CompressFormat,
        color: Int = Color.MAGENTA
    ): Uri {
        val dir = File(context.cacheDir, "test_picker_images").apply { mkdirs() }
        val file = File(dir, fileName)
        val bmp = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        FileOutputStream(file).use { out ->
            bmp.compress(format, 90, out)
        }
        bmp.recycle()
        return Uri.fromFile(file)
    }

    @Test
    fun imageFormatValidation_supportsJpgJpegPngWebp_andRejectsOthers() {
        assertTrue(StudioMediaHelper.isSupportedImageFormat("image/jpeg", "photo.jpg"))
        assertTrue(StudioMediaHelper.isSupportedImageFormat("image/jpg", "photo.JPEG"))
        assertTrue(StudioMediaHelper.isSupportedImageFormat("image/png", "worship.png"))
        assertTrue(StudioMediaHelper.isSupportedImageFormat("image/webp", "choir.webp"))
        assertTrue(StudioMediaHelper.isSupportedImageFormat(null, "content://picker/1/sample.JPEG"))

        assertFalse(StudioMediaHelper.isSupportedImageFormat("image/gif", "anim.gif"))
        assertFalse(StudioMediaHelper.isSupportedImageFormat("image/bmp", "raw.bmp"))
        assertFalse(StudioMediaHelper.isSupportedImageFormat("video/mp4", "clip.mp4"))
        assertFalse(StudioMediaHelper.isSupportedImageFormat("text/plain", "notes.txt"))
    }

    @Test
    fun importPhotosFromUris_copiesSupportedImagesLocally_andSkipsUnsupported() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val jpgUri = createTempImageUri(context, "p1.jpg", Bitmap.CompressFormat.JPEG, Color.RED)
        val jpegUri = createTempImageUri(context, "p2.jpeg", Bitmap.CompressFormat.JPEG, Color.GREEN)
        val pngUri = createTempImageUri(context, "p3.png", Bitmap.CompressFormat.PNG, Color.BLUE)
        @Suppress("DEPRECATION")
        val webpUri = createTempImageUri(context, "p4.webp", Bitmap.CompressFormat.WEBP, Color.YELLOW)
        val badFile = File(context.cacheDir, "unsupported.txt").apply { writeText("not an image") }

        val result = StudioMediaHelper.importPhotosFromUris(
            context = context,
            uris = listOf(jpgUri, jpegUri, pngUri, webpUri, Uri.fromFile(badFile)),
            studioType = "VIDEO_TEST"
        )

        assertTrue(result is PhotoImportResult.Success)
        val success = result as PhotoImportResult.Success
        assertEquals(4, success.imported.size)
        assertEquals(1, success.skippedUnsupported)
        success.imported.forEach { item ->
            val savedPath = Uri.parse(item.uriString).path
            assertNotNull(savedPath)
            assertTrue("Imported photo file should exist: $savedPath", File(savedPath!!).exists())
        }

        val onlyBad = StudioMediaHelper.importPhotosFromUris(
            context = context,
            uris = listOf(Uri.fromFile(badFile)),
            studioType = "VIDEO_TEST"
        )
        assertTrue(onlyBad is PhotoImportResult.Error)
    }

    @Test
    fun storyboardPhotoOperations_reorderRemoveAndCoverSelection_preserveIntegrity() {
        val items = StudioMediaHelper.ensureCoverExists(
            listOf(
                UploadedPhotoItem("a", "file:///a.jpg", "a.jpg", "image/jpeg"),
                UploadedPhotoItem("b", "file:///b.png", "b.png", "image/png"),
                UploadedPhotoItem("c", "file:///c.webp", "c.webp", "image/webp")
            )
        )
        assertEquals(listOf(true, false, false), items.map { it.isCover })

        val movedRight = StudioMediaHelper.movePhotoRight(items, "a")
        assertEquals(listOf("b", "a", "c"), movedRight.map { it.id })

        val movedLeft = StudioMediaHelper.movePhotoLeft(movedRight, "c")
        assertEquals(listOf("b", "c", "a"), movedLeft.map { it.id })

        val withNewCover = StudioMediaHelper.setCoverPhoto(movedLeft, "c")
        assertEquals("file:///c.webp", StudioMediaHelper.coverPhotoUri(withNewCover))
        assertEquals(1, withNewCover.count { it.isCover })

        val afterRemoveCover = StudioMediaHelper.removePhoto(withNewCover, "c")
        assertEquals(listOf("b", "a"), afterRemoveCover.map { it.id })
        assertTrue(afterRemoveCover.first().isCover)
    }

    @Test
    fun draftPersistence_savesAndLoadsPhotosSettingsAndExportPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val existingPhotoFile = File(context.filesDir, "draft_test_photo.jpg").apply {
            createNewFile()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val exportedMp4 = File(context.filesDir, "exported_videos/draft_video.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6))
        }

        val draft = PhotoSlideshowDraft(
            studioType = "UNIT_DRAFT",
            title = "Amazing Grace Moments",
            prompt = "Sunrise sanctuary photos",
            bibleVerse = "Psalm 100:4",
            captions = "Enter His gates with thanksgiving",
            templateName = "Morning Worship",
            aspectRatio = SlideshowAspectRatio.PORTRAIT_9_16,
            transition = SlideshowTransition.ZOOM,
            durationSecondsPerPhoto = 5,
            audioSourceLabel = "Grace Hymn.mp3",
            audioUriString = "file:///audio/grace.mp3",
            photos = listOf(
                UploadedPhotoItem(
                    id = "p1",
                    uriString = Uri.fromFile(existingPhotoFile).toString(),
                    displayName = "sanctuary.jpg",
                    mimeType = "image/jpeg",
                    isCover = true
                ),
                UploadedPhotoItem(
                    id = "missing",
                    uriString = "file:///does_not_exist_999.jpg",
                    displayName = "missing.jpg",
                    mimeType = "image/jpeg",
                    isCover = false
                )
            ),
            lastExportedVideoPath = exportedMp4.absolutePath
        )

        StudioMediaHelper.saveDraft(context, draft)
        val loaded = StudioMediaHelper.loadDraft(context, "UNIT_DRAFT")

        assertNotNull(loaded)
        assertEquals("Amazing Grace Moments", loaded!!.title)
        assertEquals("Psalm 100:4", loaded.bibleVerse)
        assertEquals("Enter His gates with thanksgiving", loaded.captions)
        assertEquals(SlideshowAspectRatio.PORTRAIT_9_16, loaded.aspectRatio)
        assertEquals(SlideshowTransition.ZOOM, loaded.transition)
        assertEquals(5, loaded.durationSecondsPerPhoto)
        assertEquals("Grace Hymn.mp3", loaded.audioSourceLabel)
        assertEquals(1, loaded.photos.size)
        assertEquals("p1", loaded.photos.first().id)
        assertTrue(loaded.photos.first().isCover)
        assertEquals(exportedMp4.absolutePath, loaded.lastExportedVideoPath)
    }

    @OptIn(UnstableApi::class)
    @Test
    fun media3Exporter_buildsCompositionAndOverlayBitmap_forAllAspectRatiosAndTransitions() {
        assertNull(Media3SlideshowExporter.createTextOverlayBitmap("", "", "", 1280))

        val overlay = Media3SlideshowExporter.createTextOverlayBitmap(
            title = "Morning Praise",
            bibleVerse = "Psalm 23:1",
            captions = "Church Choir Reunion",
            targetWidth = 1080
        )
        assertNotNull(overlay)
        assertTrue(overlay!!.width >= 360)
        assertTrue(overlay.height >= 88)

        val photos = listOf(
            UploadedPhotoItem("1", "file:///tmp/1.jpg", "1.jpg", "image/jpeg", true),
            UploadedPhotoItem("2", "file:///tmp/2.png", "2.png", "image/png", false)
        )

        val comp16x9 = Media3SlideshowExporter.buildComposition(
            request = SlideshowExportRequest(
                studioType = "VIDEO",
                title = "Worship",
                bibleVerse = "Psalm 23:1",
                captions = "Sunday Service",
                photos = photos,
                aspectRatio = SlideshowAspectRatio.LANDSCAPE_16_9,
                transition = SlideshowTransition.FADE,
                durationSecondsPerPhoto = 4,
                audioUriString = ""
            ),
            overlayBitmap = overlay,
            audioDurationMs = null
        )
        assertEquals(1, comp16x9.sequences.size)
        assertEquals(2, comp16x9.sequences[0].editedMediaItems.size)
        assertEquals(1, comp16x9.effects.videoEffects.size)

        val compShortAudio = Media3SlideshowExporter.buildComposition(
            request = SlideshowExportRequest(
                studioType = "MUSIC_VIDEO",
                title = "Twi Praise",
                bibleVerse = "",
                captions = "",
                photos = photos,
                aspectRatio = SlideshowAspectRatio.PORTRAIT_9_16,
                transition = SlideshowTransition.ZOOM,
                durationSecondsPerPhoto = 5,
                audioUriString = "file:///tmp/short_song.mp3"
            ),
            overlayBitmap = null,
            audioDurationMs = 4_000L
        )
        assertEquals(2, compShortAudio.sequences.size)
        assertTrue(compShortAudio.sequences[1].isLooping)

        val compLongAudio = Media3SlideshowExporter.buildComposition(
            request = SlideshowExportRequest(
                studioType = "MUSIC_VIDEO",
                title = "Highlife Anthem",
                bibleVerse = "John 3:16",
                captions = "Gospel Celebration",
                photos = photos,
                aspectRatio = SlideshowAspectRatio.SQUARE_1_1,
                transition = SlideshowTransition.SLIDE_PAN,
                durationSecondsPerPhoto = 3,
                audioUriString = "file:///tmp/long_song.mp3"
            ),
            overlayBitmap = overlay,
            audioDurationMs = 90_000L
        )
        assertEquals(2, compLongAudio.sequences.size)
        assertFalse(compLongAudio.sequences[1].isLooping)
        assertEquals(
            6_000L,
            compLongAudio.sequences[1].editedMediaItems[0].mediaItem.clippingConfiguration.endPositionMs
        )
        overlay.recycle()
    }

    @Test
    fun shareVideoIntentAndCopyToUri_handleExportedMp4FileSafely() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exportedFile = StudioMediaHelper.getExportedOutputFile(context, "VIDEO", "GraceMoments")
        exportedFile.writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112))

        val shareIntent = StudioMediaHelper.createShareVideoIntent(
            context = context,
            videoFilePath = exportedFile.absolutePath,
            title = "Grace Moments"
        )
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent!!.action)
        assertEquals("video/mp4", shareIntent.type)
        @Suppress("DEPRECATION")
        val streamUri = shareIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull(streamUri)
        assertTrue(streamUri.toString().contains(".fileprovider"))
        assertTrue((shareIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        val destFile = File(context.cacheDir, "copied_out.mp4")
        val copied = StudioMediaHelper.copyVideoToUri(context, exportedFile.absolutePath, Uri.fromFile(destFile))
        assertTrue(copied)
        assertEquals(exportedFile.length(), destFile.length())
    }

    @Test
    fun videoStudioScreen_displaysPhotoUploadPreviewControlsAndUpdatesOrderCoverRatio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = StudioViewModel(testRepository)
        val uri1 = createTempImageUri(context, "ui_v1.jpg", Bitmap.CompressFormat.JPEG, Color.CYAN)
        val uri2 = createTempImageUri(context, "ui_v2.png", Bitmap.CompressFormat.PNG, Color.MAGENTA)

        viewModel.setPhotosForTest(
            listOf(
                UploadedPhotoItem("id1", uri1.toString(), "FirstPraise.jpg", "image/jpeg", isCover = true),
                UploadedPhotoItem("id2", uri2.toString(), "SecondChoir.png", "image/png", isCover = false)
            )
        )

        composeRule.setContent {
            WordAndLightTheme {
                VideoStudioScreen(
                    onNavigateToProjects = {},
                    repositoryOverride = testRepository,
                    viewModelOverride = viewModel
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("video_upload_photo_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("video_slideshow_preview_stage").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("video_photo_preview_list").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("video_photo_preview_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("video_photo_item_0").assertIsDisplayed()
        composeRule.onNodeWithTag("video_photo_preview_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("video_photo_item_1").assertIsDisplayed()

        // Reorder first photo to second place
        composeRule.onNodeWithTag("video_photo_preview_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("video_move_right_0").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("id2", "id1"), viewModel.photos.value.map { it.id })

        // Select second photo as cover
        composeRule.onNodeWithTag("video_photo_preview_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("video_cover_1").performClick()
        composeRule.waitForIdle()
        assertTrue(viewModel.photos.value[1].isCover)
        assertFalse(viewModel.photos.value[0].isCover)

        // Update optional title, Bible verse, captions, aspect ratio, transition, and duration
        composeRule.onNodeWithTag("video_title_input").performScrollTo().performTextInput("Grace Slideshow")
        composeRule.onNodeWithTag("video_bible_verse_input").performScrollTo().performTextInput("Psalm 23:1")
        composeRule.onNodeWithTag("video_captions_input").performScrollTo().performTextInput("Choir Praise")
        composeRule.onNodeWithTag("video_ratio_9_16").performScrollTo().performClick()
        composeRule.onNodeWithTag("video_transition_zoom").performScrollTo().performClick()
        composeRule.onNodeWithTag("video_duration_5").performScrollTo().performClick()
        composeRule.onNodeWithTag("video_upload_audio_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("video_create_mp4_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Create MP4 Slideshow (2 Photos)", substring = true)

        // Remove first photo and verify cover persists cleanly on remaining photo
        composeRule.onNodeWithTag("video_photo_preview_list").performScrollTo()
        composeRule.onNodeWithTag("video_photo_preview_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("video_remove_photo_0").performClick()
        composeRule.waitForIdle()
        assertEquals(1, viewModel.photos.value.size)
        assertTrue(viewModel.photos.value.first().isCover)
    }

    @Test
    fun musicVideoCreatorScreen_supportsPhotoUploadAudioPickerAndExportCta() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = StudioViewModel(testRepository)
        val uri1 = createTempImageUri(context, "mv_1.webp", Bitmap.CompressFormat.PNG, Color.YELLOW)
        val uri2 = createTempImageUri(context, "mv_2.jpeg", Bitmap.CompressFormat.JPEG, Color.GREEN)

        viewModel.setPhotosForTest(
            listOf(
                UploadedPhotoItem("m1", uri1.toString(), "WorshipStage.webp", "image/webp", isCover = true),
                UploadedPhotoItem("m2", uri2.toString(), "Congregation.jpeg", "image/jpeg", isCover = false)
            )
        )

        composeRule.setContent {
            WordAndLightTheme {
                MusicVideoCreatorScreen(
                    onNavigateToProjects = {},
                    repositoryOverride = testRepository,
                    viewModelOverride = viewModel
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mv_upload_audio_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mv_upload_photo_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mv_slideshow_preview_stage").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mv_photo_preview_list").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mv_photo_preview_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("mv_photo_item_0").assertIsDisplayed()
        composeRule.onNodeWithTag("mv_photo_preview_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("mv_photo_item_1").assertIsDisplayed()
        composeRule.onNodeWithTag("mv_captions_input").performScrollTo().performTextInput("Hallelujah Chorus")
        composeRule.onNodeWithTag("mv_ratio_1_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("mv_transition_slide_pan").performScrollTo().performClick()
        composeRule.onNodeWithTag("mv_duration_7").performScrollTo().performClick()
        composeRule.onNodeWithTag("mv_title_input").performScrollTo().performTextInput("Twi Worship Anthem")
        composeRule.onNodeWithTag("mv_bible_verse_input").performScrollTo().performTextInput("Psalm 118:24")
        composeRule.onNodeWithTag("mv_create_mp4_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Create MP4 Music Video (2 Photos)", substring = true)
    }
}
