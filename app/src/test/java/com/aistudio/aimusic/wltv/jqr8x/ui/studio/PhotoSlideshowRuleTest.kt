package com.aistudio.aimusic.wltv.jqr8x.ui.studio

import com.aistudio.aimusic.wltv.jqr8x.base.FirestoreEmulatorTestBase
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class PhotoSlideshowRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun repositoryAndFirestoreRules_persistAndValidateSlideshowMetadata() = runBlocking {
        val uid = signInTestUser("photostudio_${UUID.randomUUID()}@test.com")
        val repository = MusicRepository(firestore, auth)

        withTimeout(10_000L) {
            val video = repository.generateVideoViaBackend(
                prompt = "Youth Choir Fellowship",
                templateName = "Photo Slideshow (2 photos)",
                title = "Youth Sunday Highlight",
                aspectRatio = "9:16",
                captions = "Psalm 150:6 • Joyful praise",
                transitionStyle = "Smooth Zoom",
                durationPerImageSec = 5,
                coverPhotoUri = "file:///photos/cover.jpg",
                photoUris = listOf("file:///photos/cover.jpg", "file:///photos/second.png"),
                localVideoPath = "/data/user/0/app/files/exported_videos/youth.mp4"
            )
            assertEquals("9:16", video.aspectRatio)
            assertEquals(2, video.photoCount)
            assertEquals("Smooth Zoom", video.transitionStyle)
            assertEquals(5, video.durationPerImageSec)

            val fetchedVideos = repository.getMyVideos().first { list -> list.any { it.id == video.id } }
            val storedVideo = fetchedVideos.first { it.id == video.id }
            assertEquals(2, storedVideo.photoUris.size)
            assertEquals("/data/user/0/app/files/exported_videos/youth.mp4", storedVideo.localVideoPath)

            val mv = repository.createMusicVideoViaBackend(
                songId = "Praise High (Gospel)",
                videoId = "Uploaded Photos (2)",
                bibleVerse = "Isaiah 40:31",
                title = "Soar on Wings",
                aspectRatio = "1:1",
                captions = "Sanctuary Gallery",
                transitionStyle = "Fade",
                durationPerImageSec = 4,
                coverPhotoUri = "file:///photos/cover.jpg",
                photoUris = listOf("file:///photos/cover.jpg", "file:///photos/second.png"),
                localVideoPath = "/data/user/0/app/files/exported_videos/soar.mp4"
            )
            val fetchedMvs = repository.getMyMusicVideos().first { list -> list.any { it.id == mv.id } }
            val storedMv = fetchedMvs.first { it.id == mv.id }
            assertEquals("1:1", storedMv.aspectRatio)
            assertEquals(2, storedMv.photoCount)
            assertEquals("Isaiah 40:31", storedMv.bibleVerse)

            try {
                firestore.collection("videos").add(
                    mapOf(
                        "userId" to uid,
                        "title" to "Bad Metadata",
                        "prompt" to "Test",
                        "templateName" to "Test",
                        "videoUrl" to "https://example.com/v.mp4",
                        "createdAt" to com.google.firebase.Timestamp.now(),
                        "isDemo" to true,
                        "creatorName" to "Test Creator",
                        "durationPerImageSec" to 999
                    )
                ).await()
                fail("Expected PERMISSION_DENIED for invalid durationPerImageSec")
            } catch (e: Exception) {
                val fse = e.unwrapFirestoreException()
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fse?.code)
            }
        }
    }
}
