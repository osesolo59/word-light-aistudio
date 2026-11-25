package com.aistudio.aimusic.wltv.jqr8x.ui.ideas

import com.aistudio.aimusic.wltv.jqr8x.base.FirestoreEmulatorTestBase
import com.aistudio.aimusic.wltv.jqr8x.data.demo.IdeaGeneratorEngine
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class IdeaGeneratorEngineAndRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun engine_generates5DistinctDemoIdeasWithoutOptionalFilters_andSupportsAllCategoriesAndLanguages() {
        assertEquals(
            listOf(
                "Song Ideas",
                "Lyrics Ideas",
                "Music Style Ideas",
                "Gospel Worship Ideas",
                "Praise and Reggae Ideas",
                "Music Video Ideas",
                "Christian Video Ideas"
            ),
            IdeaGeneratorEngine.CATEGORIES
        )
        assertEquals(
            listOf("English", "Twi", "Mixed English + Twi", "Akan", "Ga", "Ewe"),
            IdeaGeneratorEngine.LANGUAGES
        )

        // Default / optional filters omitted -> 5 distinct suggestions across categories
        val defaultBatch = IdeaGeneratorEngine.generateIdeas(
            category = null,
            genre = null,
            mood = null,
            language = "English",
            batchSeed = 0
        )
        assertEquals(5, defaultBatch.size)
        assertTrue(defaultBatch.all { it.isDemo && it.title.isNotBlank() && it.summary.isNotBlank() })
        assertEquals(5, defaultBatch.map { it.title }.distinct().size)

        // Generate More (batchSeed = 1) produces a fresh set
        val nextBatch = IdeaGeneratorEngine.generateIdeas(
            category = null,
            genre = null,
            mood = null,
            language = "English",
            batchSeed = 1
        )
        assertEquals(5, nextBatch.size)
        assertNotEquals(defaultBatch.map { it.title }, nextBatch.map { it.title })

        // Verify all 5 languages & optional genre/mood filters
        for (lang in IdeaGeneratorEngine.LANGUAGES) {
            val localized = IdeaGeneratorEngine.generateIdeas(
                category = "Praise and Reggae Ideas",
                genre = "Reggae",
                mood = "Joyful",
                language = lang,
                batchSeed = 0
            )
            assertEquals(5, localized.size)
            assertTrue(localized.all {
                it.language == lang &&
                    it.genre == "Reggae" &&
                    it.mood == "Joyful" &&
                    it.isDemo
            })
        }

        // Verify target studios map properly
        assertEquals(
            "LYRICS",
            IdeaGeneratorEngine.generateIdeas(category = "Lyrics Ideas", language = "Twi").first().targetStudio
        )
        assertEquals(
            "VIDEO",
            IdeaGeneratorEngine.generateIdeas(category = "Christian Video Ideas", language = "Ga").first().targetStudio
        )
        assertEquals(
            "MUSIC_VIDEO",
            IdeaGeneratorEngine.generateIdeas(category = "Music Video Ideas", language = "Ewe").first().targetStudio
        )
    }

    @Test
    fun repository_saveAndDeleteIdeasInFirestore_respectsOwnerSecurityRules() {
        runBlocking {
            signInTestUser("idea_owner_${UUID.randomUUID()}@test.com")
            val ownerRepo = MusicRepository(firestore, auth)

            val generated = ownerRepo.generateDemoIdeas(
                category = "Gospel Worship Ideas",
                genre = "Worship",
                mood = "Reverent",
                language = "Twi",
                batchSeed = 0
            )
            val ideaToSave = generated.first()

            val ideaId = withTimeout(DEFAULT_TIMEOUT_MS) {
                ownerRepo.saveIdea(ideaToSave)
            }

            val savedList = withTimeout(FLOW_TIMEOUT_MS) {
                ownerRepo.getMySavedIdeas().first { list -> list.any { it.id == ideaId } }
            }
            val loaded = savedList.first { it.id == ideaId }
            assertEquals(ideaToSave.title, loaded.title)
            assertEquals("Twi", loaded.language)
            assertTrue(loaded.isDemo)

            // Cross-user read must fail with PERMISSION_DENIED
            signInTestUser("idea_other_${UUID.randomUUID()}@test.com")
            try {
                withTimeout(DEFAULT_TIMEOUT_MS) {
                    firestore.collection("ideas").document(ideaId).get().await()
                }
                fail("Expected PERMISSION_DENIED when reading another user's saved idea")
            } catch (e: Exception) {
                val firestoreException = e.unwrapFirestoreException()
                assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
            }

            // Unauthenticated access must fail with PERMISSION_DENIED
            auth.signOut()
            try {
                withTimeout(FLOW_TIMEOUT_MS) {
                    firestore.collection("ideas").get().await()
                }
                fail("Expected PERMISSION_DENIED for unauthenticated ideas query")
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
