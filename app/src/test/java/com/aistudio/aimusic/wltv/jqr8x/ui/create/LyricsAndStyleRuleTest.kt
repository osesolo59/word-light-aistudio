package com.aistudio.aimusic.wltv.jqr8x.ui.create

import com.aistudio.aimusic.wltv.jqr8x.base.FirestoreEmulatorTestBase
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.google.firebase.firestore.FirebaseFirestoreException
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

class LyricsAndStyleRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun repository_saveReuseLyricsAndAllGenresWithStyles_succeeds() {
        runBlocking {
            signInTestUser("lyric_tester_${UUID.randomUUID()}@test.com")
            val repository = MusicRepository(firestore, auth)

            withTimeout(DEFAULT_TIMEOUT_MS) {
                // Verify Twi & English demo lyrics generation clearly states simulated template
                val twiLyrics = repository.generateLyricsViaBackend(
                    topic = "Aseda Nwom",
                    genre = "Highlife",
                    mood = "Joyful",
                    language = "Twi"
                )
                assertTrue(twiLyrics.contains("Twi"))
                assertTrue(twiLyrics.contains("simulated locally"))

                val englishLyrics = repository.generateLyricsViaBackend(
                    topic = "Morning Grace",
                    genre = "Gospel",
                    mood = "Uplifting",
                    language = "English"
                )
                assertTrue(englishLyrics.contains("English"))
                assertTrue(englishLyrics.contains("simulated locally"))

                val gaLyrics = repository.generateLyricsViaBackend("Oyiwala", "Highlife", "Joyful", "Ga")
                assertTrue(gaLyrics.contains("Ga"))
                assertTrue(gaLyrics.contains("Nyɔŋmɔ"))

                val eweLyrics = repository.generateLyricsViaBackend("Akpe", "Worship", "Peaceful", "Ewe")
                assertTrue(eweLyrics.contains("Ewe"))
                assertTrue(eweLyrics.contains("Mawu"))

                val akanLyrics = repository.generateLyricsViaBackend("Aseda", "Gospel", "Joyful", "Akan")
                assertTrue(akanLyrics.contains("Akan"))
                assertTrue(akanLyrics.contains("Onyame"))

                // Save edited Twi lyrics to Firestore /lyrics collection
                val editedLyrics = "$twiLyrics\n[Bridge]\nYesu ye me kyɛm"
                val lyricId = repository.saveLyrics(
                    title = "Aseda Nwom",
                    lyrics = editedLyrics,
                    topic = "Aseda Nwom",
                    genre = "Highlife",
                    mood = "Joyful",
                    language = "Twi"
                )

                val savedLyricsList = withTimeout(FLOW_TIMEOUT_MS) {
                    repository.getMyLyrics().first { list -> list.any { it.id == lyricId } }
                }
                val loadedLyric = savedLyricsList.first { it.id == lyricId }
                assertEquals("Twi", loadedLyric.language)
                assertEquals("Highlife", loadedLyric.genre)
                assertEquals("Joyful", loadedLyric.mood)
                assertTrue(loadedLyric.lyrics.contains("Yesu ye me kyɛm"))

                // Update saved lyrics with edited text and bilingual caption cues
                repository.updateLyrics(
                    lyricId = lyricId,
                    title = "Aseda Nwom (Edited)",
                    lyrics = "$editedLyrics\n[Outro]\nAyeyi nka Awurade",
                    topic = "Aseda Nwom",
                    genre = "Gospel",
                    mood = "Uplifting",
                    language = "Mixed English + Twi",
                    scriptureReference = "Psalm 100:4",
                    generationSource = "AI_GEMINI",
                    sectionLanguages = mapOf("Verse 1" to "Twi", "Chorus" to "Mixed"),
                    captionCues = listOf(
                        com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue(
                            id = "cue_1",
                            startSec = 0,
                            endSec = 5,
                            sectionLabel = "Verse 1",
                            englishText = "Jesus is my shield",
                            twiText = "Yesu ye me kyɛm"
                        )
                    )
                )

                val updatedLyricsList = withTimeout(FLOW_TIMEOUT_MS) {
                    repository.getMyLyrics().first { list ->
                        list.any { it.id == lyricId && it.title == "Aseda Nwom (Edited)" }
                    }
                }
                val updatedLyric = updatedLyricsList.first { it.id == lyricId }
                assertEquals("Gospel", updatedLyric.genre)
                assertEquals("Mixed English + Twi", updatedLyric.language)
                assertEquals("Psalm 100:4", updatedLyric.scriptureReference)
                assertEquals(1, updatedLyric.captionCues.size)
                assertTrue(updatedLyric.lyrics.contains("Ayeyi nka Awurade"))

                assertTrue(MusicRepository.IS_DEMO_MODE)

                // Save full Music Style metadata with song project (Hip-hop, Twi, mood, tempo, instruments, vocalStyle)
                val savedSong = repository.generateMusicViaBackend(
                    title = "Kingdom Anthem",
                    prompt = "",
                    lyrics = editedLyrics,
                    genre = "Hip-hop",
                    modelId = "demo-full",
                    taskId = "task_${UUID.randomUUID()}",
                    language = "Twi",
                    mood = "Energetic",
                    tempo = "Upbeat (120 BPM)",
                    instruments = listOf("Piano", "Talking Drum", "Drums"),
                    vocalStyle = "Call & Response"
                )
                assertTrue(savedSong.isDemo)
                assertTrue(savedSong.audioUrl.startsWith("https://"))

                val mySongs = withTimeout(FLOW_TIMEOUT_MS) {
                    repository.getMySongs().first { list -> list.any { it.id == savedSong.id } }
                }
                val loadedSong = mySongs.first { it.id == savedSong.id }
                assertEquals("Hip-hop", loadedSong.genre)
                assertEquals("Twi", loadedSong.language)
                assertEquals("Energetic", loadedSong.mood)
                assertEquals("Upbeat (120 BPM)", loadedSong.tempo)
                assertEquals(listOf("Piano", "Talking Drum", "Drums"), loadedSong.instruments)
                assertEquals("Call & Response", loadedSong.vocalStyle)

                repository.deleteLyrics(lyricId)
            }
        }
    }

    @Test
    fun lyricsCollection_crossUserAndUnauthenticatedAccess_failsWithPermissionDenied() {
        runBlocking {
            signInTestUser("lyric_owner_${UUID.randomUUID()}@test.com")
            val ownerRepo = MusicRepository(firestore, auth)
            val lyricId = withTimeout(DEFAULT_TIMEOUT_MS) {
                ownerRepo.saveLyrics(
                    title = "Private Praise",
                    lyrics = "Original verse",
                    topic = "Praise",
                    genre = "Worship",
                    mood = "Reverent",
                    language = "English"
                )
            }

            signInTestUser("lyric_other_${UUID.randomUUID()}@test.com")
            val otherRepo = MusicRepository(firestore, auth)
            try {
                withTimeout(DEFAULT_TIMEOUT_MS) {
                    firestore.collection("lyrics").document(lyricId).get().await()
                }
                fail("Expected PERMISSION_DENIED when reading another user's lyrics")
            } catch (e: Exception) {
                val firestoreException = e.unwrapFirestoreException()
                assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
            }

            try {
                withTimeout(DEFAULT_TIMEOUT_MS) {
                    otherRepo.updateLyrics(
                        lyricId = lyricId,
                        title = "Hijacked",
                        lyrics = "Unauthorized edit",
                        topic = "Praise",
                        genre = "Worship",
                        mood = "Reverent",
                        language = "English"
                    )
                }
                fail("Expected PERMISSION_DENIED when updating another user's lyrics")
            } catch (e: Exception) {
                val firestoreException = e.unwrapFirestoreException()
                assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
            }

            auth.signOut()
            try {
                withTimeout(FLOW_TIMEOUT_MS) {
                    firestore.collection("lyrics").get().await()
                }
                fail("Expected PERMISSION_DENIED for unauthenticated lyrics read")
            } catch (e: Exception) {
                val firestoreException = e.unwrapFirestoreException()
                assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
