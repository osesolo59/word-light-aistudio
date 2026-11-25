package com.aistudio.aimusic.wltv.jqr8x.ui.mixer

import com.aistudio.aimusic.wltv.jqr8x.base.FirestoreEmulatorTestBase
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class AudioMixAndBilingualRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun audioMixesAndBilingualLyrics_saveReadDeleteAndEnforceOwnerRules(): Unit = runBlocking {
        val emailA = "mixer_a_${UUID.randomUUID()}@test.com"
        val uidA = signInTestUser(emailA)
        val repoA = MusicRepository(firestore, auth)

        val cues = listOf(
            TimedBilingualCue(
                id = "cue_1",
                startSec = 0,
                endSec = 5,
                sectionLabel = "Verse 1",
                englishText = "All honor to Your name",
                twiText = "Ayeyi nka Wo din"
            )
        )
        val tracks = listOf(
            AudioMixTrack(
                id = "t_1",
                displayName = "choir_lead.m4a",
                formatLabel = "M4A",
                localUriString = "file:///app/files/mixer_inputs/choir_lead.m4a",
                durationMs = 12_000L,
                trimStartMs = 500L,
                trimEndMs = 11_000L,
                volume = 0.9f,
                isMuted = false,
                offsetMs = 0L
            )
        )

        val savedMix = withTimeout(10_000L) {
            val savedLyricId = repoA.saveLyrics(
                title = "Aseda Ne Ayeyi",
                lyrics = "[Verse 1 — Twi]\nAyeyi nka Onyankopɔn",
                topic = "Gratitude",
                genre = "Highlife",
                mood = "Joyful",
                language = BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI,
                sectionLanguages = BilingualGospelHelper.defaultSectionLanguages(BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI),
                captionCues = cues
            )
            val savedLyric = repoA.getMyLyrics().first { list -> list.any { it.id == savedLyricId } }
                .first { it.id == savedLyricId }
            assertEquals(1, savedLyric.captionCues.size)
            assertEquals("Twi", savedLyric.sectionLanguages["Verse 1"])

            val mix = repoA.saveAudioMixProject(
                title = "Aseda Bilingual Master",
                language = BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI,
                sectionLanguages = BilingualGospelHelper.defaultSectionLanguages(BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI),
                lyrics = "[Verse 1 — Twi]\nAyeyi nka Onyankopɔn",
                captionCues = cues,
                tracks = tracks,
                totalDurationMs = 10_500L,
                localMp3Path = "/data/user/0/app/files/mixed_audio/aseda.mp3"
            )

            val myMixes = repoA.getMyAudioMixes().first { list -> list.any { it.id == mix.id } }
            val loaded = myMixes.first { it.id == mix.id }
            assertEquals("Aseda Bilingual Master", loaded.title)
            assertEquals(1, loaded.tracks.size)
            assertEquals("M4A", loaded.tracks.first().formatLabel)
            assertEquals(1, loaded.captionCues.size)
            assertEquals("Ayeyi nka Wo din", loaded.captionCues.first().twiText)
            mix
        }

        // Switch to User B and verify zero-trust isolation on /audio_mixes
        signInTestUser("mixer_b_${UUID.randomUUID()}@test.com")
        try {
            firestore.collection("audio_mixes").document(savedMix.id).get().await()
            fail("Expected PERMISSION_DENIED reading another user's audio mix")
        } catch (e: Exception) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
        }

        // Verify unowned create is rejected
        try {
            firestore.collection("audio_mixes").add(
                mapOf(
                    "userId" to uidA,
                    "title" to "Spoofed Mix",
                    "language" to "English",
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "isDemo" to true
                )
            ).await()
            fail("Expected PERMISSION_DENIED creating audio mix for another uid")
        } catch (e: Exception) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
        }

        // Switch back to User A and verify delete removes own mix
        signInTestUser(emailA)
        withTimeout(10_000L) {
            repoA.deleteAudioMix(savedMix.id)
            val remaining = repoA.getMyAudioMixes().first { list -> list.none { it.id == savedMix.id } }
            assertFalse(remaining.any { it.id == savedMix.id })
        }
    }
}
