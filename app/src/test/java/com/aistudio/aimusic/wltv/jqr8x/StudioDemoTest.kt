package com.aistudio.aimusic.wltv.jqr8x

import com.aistudio.aimusic.wltv.jqr8x.base.FirestoreEmulatorTestBase
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StudioDemoTest : FirestoreEmulatorTestBase() {

    private lateinit var repository: MusicRepository

    @Before
    fun setup() {
        repository = MusicRepository(firestore, auth)
    }

    @Test
    fun `confirm strict free demo mode is active`() {
        assertTrue("MusicRepository.IS_DEMO_MODE should be true", MusicRepository.IS_DEMO_MODE)
    }

    @Test
    fun `confirm MusicRepository has no FirebaseFunctions or FirebaseAI fields`() {
        val fieldTypes = MusicRepository::class.java.declaredFields.map { it.type.name }
        assertFalse(fieldTypes.any { it.contains("FirebaseFunctions") })
        assertFalse(fieldTypes.any { it.contains("FirebaseAI") || it.contains("GenerativeModel") })
        assertNotNull(repository)
    }

    @Test
    fun `verify local demo lyrics templates support English Twi Akan Ga and Ewe`() = runBlocking {
        for (lang in listOf("English", "Twi", "Akan", "Ga", "Ewe")) {
            val lyrics = repository.generateLyricsViaBackend(
                topic = "Praise and Light",
                genre = "Gospel",
                mood = "Joyful",
                language = lang
            )
            assertTrue("Expected language $lang in demo lyrics", lyrics.contains(lang))
            assertTrue("Expected simulated template notice", lyrics.contains("simulated locally"))
        }
    }
}
