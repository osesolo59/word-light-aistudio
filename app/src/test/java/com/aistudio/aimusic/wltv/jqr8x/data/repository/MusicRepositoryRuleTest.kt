package com.aistudio.aimusic.wltv.jqr8x.data.repository

import com.aistudio.aimusic.wltv.jqr8x.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class MusicRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfileExists_andDemoSongLifecycle_succeedsForAuthenticatedOwner() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = MusicRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfileExists()
            val profile = repository.getUserProfile().first { it != null }
            assertEquals(5, profile?.credits)

            val song = repository.generateMusicViaBackend(
                title = "Light of Dawn",
                prompt = "Warm gospel praise",
                lyrics = "Shine your light",
                genre = "Gospel",
                modelId = "demo",
                taskId = "task_${UUID.randomUUID()}"
            )
            assertTrue(song.isDemo)
            assertTrue(song.id.isNotBlank())

            val mySongs = withTimeout(FLOW_TIMEOUT_MS) {
                repository.getMySongs().first { list -> list.any { it.id == song.id } }
            }
            assertTrue(mySongs.any { it.id == song.id })

            repository.toggleFavorite(song.id, true)
            val favSongs = withTimeout(FLOW_TIMEOUT_MS) {
                repository.getFavoriteSongs().first { list -> list.any { it.id == song.id } }
            }
            assertTrue(favSongs.any { it.id == song.id })

            repository.updateSongDetails(song.id, "Light of Dawn (Live)", true)
            val trending = withTimeout(FLOW_TIMEOUT_MS) {
                repository.getTrendingSongs().first { list -> list.any { it.id == song.id } }
            }
            assertTrue(trending.any { it.id == song.id && it.title == "Light of Dawn (Live)" })

            repository.deleteSong(song.id)
        }
    }

    @Test
    fun videoMusicVideoAndPlaylist_crud_succeedsForOwner() = runBlocking {
        signInTestUser("creator_${UUID.randomUUID()}@test.com")
        val repository = MusicRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            val video = repository.generateVideoViaBackend("Heavenly clouds", "Heavenly Clouds")
            val myVideos = withTimeout(FLOW_TIMEOUT_MS) {
                repository.getMyVideos().first { list -> list.any { it.id == video.id } }
            }
            assertTrue(myVideos.any { it.id == video.id })

            val mv = repository.createMusicVideoViaBackend(
                songId = "song_demo_1",
                videoId = video.id,
                bibleVerse = "Psalm 23:1",
                title = "Shepherd MV"
            )
            val myMvs = withTimeout(FLOW_TIMEOUT_MS) {
                repository.getMyMusicVideos().first { list -> list.any { it.id == mv.id } }
            }
            assertTrue(myMvs.any { it.id == mv.id })

            val playlistId = repository.createPlaylist("Sunday Setlist")
            repository.addSongToPlaylist(playlistId, "song_demo_1")
            val playlists = withTimeout(FLOW_TIMEOUT_MS) {
                repository.getMyPlaylists().first { list ->
                    list.any { it.id == playlistId && it.songIds.contains("song_demo_1") }
                }
            }
            assertTrue(playlists.any { it.id == playlistId })

            repository.removeSongFromPlaylist(playlistId, "song_demo_1")
            val updatedPlaylists = withTimeout(FLOW_TIMEOUT_MS) {
                repository.getMyPlaylists().first { list ->
                    list.any { it.id == playlistId && !it.songIds.contains("song_demo_1") }
                }
            }
            assertTrue(updatedPlaylists.any { it.id == playlistId })

            repository.deletePlaylist(playlistId)
            repository.deleteMusicVideo(mv.id)
            repository.deleteVideo(video.id)

            val remainingVideos = withTimeout(FLOW_TIMEOUT_MS) {
                repository.getMyVideos().first { list -> list.none { it.id == video.id } }
            }
            assertTrue(remainingVideos.none { it.id == video.id })
        }
    }

    @Test
    fun usersCollection_crossUserRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_priv_${UUID.randomUUID()}@test.com")
        val aliceRepo = MusicRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfileExists()
        }

        signInTestUser("bob_priv_${UUID.randomUUID()}@test.com")
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when reading another user's private profile")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun songsCollection_unauthenticatedRead_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("songs")
                    .whereEqualTo("isPublic", true)
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated read on songs")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
