package com.aistudio.aimusic.wltv.jqr8x.ui.create

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiFailureReason
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiGenerationException
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiIdeasResult
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsRequest
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsResult
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiTextGenerator
import com.aistudio.aimusic.wltv.jqr8x.data.ai.LyricsStreamEvent
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.library.LibraryScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.navigation.AppNavigation
import com.aistudio.aimusic.wltv.jqr8x.ui.player.MusicPlayerManager
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiLyricsGeneratorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUpFirebaseStub() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "lyrics-ui-test-app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.aimusic.wltv.jqr8x")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build(),
                "lyrics-ui-test-app"
            ).also { initialized ->
                val db = FirebaseFirestore.getInstance(
                    initialized,
                    "ai-studio-android-wordligh-6c38febb-2572-4fb1-840d-e10fc09376b8"
                )
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        firestore = FirebaseFirestore.getInstance(
            app,
            "ai-studio-android-wordligh-6c38febb-2572-4fb1-840d-e10fc09376b8"
        )
        auth = FirebaseAuth.getInstance(app)
        auth.signOut()
    }

    @Test
    fun lyricsGenerator_rootsInZion_displaysTitleGenreLanguageMood_andSupportsRegenerateEditCopy() {
        val capturedRequests = mutableListOf<AiLyricsRequest>()
        var generationCount = 0
        val fakeGenerator = object : AiTextGenerator {
            override val modelName: String = "gemini-3.8-flash"

            override suspend fun generateLyrics(request: AiLyricsRequest): AiLyricsResult {
                capturedRequests.add(request)
                generationCount += 1
                val lyricsBody = if (generationCount == 1) {
                    """
                    Title: Roots in Zion

                    [Verse 1]
                    Ancient rivers water every stone
                    Planted deep before the Father's throne

                    [Chorus]
                    Our roots in Zion never run dry
                    We lift Your banner to the morning sky

                    [Verse 2]
                    Drums of praise awake across the land
                    Held forever in Your mighty hand

                    [Bridge]
                    From generation to eternity
                    Your steadfast love sets every captive free

                    [Final Chorus]
                    Our roots in Zion stand in holy fire
                    You are our joy, our song, our one desire
                    """.trimIndent()
                } else {
                    """
                    Title: Roots in Zion

                    [Verse 1]
                    Sacred fire burning in our praise
                    We remember mercy all our days

                    [Chorus]
                    Stronger in Zion, lifted by Your grace
                    Shining with glory in this holy place

                    [Verse 2]
                    Every nation gathers at Your feet
                    Making heaven's melody complete
                    """.trimIndent()
                }
                return AiLyricsResult(
                    title = request.title.ifBlank { "Roots in Zion" },
                    lyrics = lyricsBody,
                    scriptureReference = request.scriptureReference,
                    modelName = modelName,
                    isDemo = false
                )
            }

            override suspend fun generateIdeas(
                category: String?,
                genre: String?,
                mood: String?,
                language: String,
                batchSeed: Int
            ): AiIdeasResult {
                return AiIdeasResult(emptyList(), modelName, isDemo = false)
            }
        }

        val repository = MusicRepository(firestore, auth, fakeGenerator)
        var transferredIdea: CreativeIdea? = null

        composeRule.setContent {
            WordAndLightTheme {
                AiLyricsGeneratorScreen(
                    onNavigateBack = {},
                    onUseLyricsInAudioStudio = { transferredIdea = it },
                    repositoryOverride = repository
                )
            }
        }

        // Empty state is visible initially
        composeRule.onNodeWithTag("lyrics_gen_empty_state").performScrollTo().assertIsDisplayed()

        // Fill Title: Roots in Zion, Genre: African Gospel Worship, Language: English, Mood: Powerful, uplifting, worshipful
        composeRule.onNodeWithTag("lyrics_gen_title_input")
            .performScrollTo()
            .performTextInput("Roots in Zion")
        composeRule.onNodeWithTag("lyrics_gen_genre_chip_african_gospel_worship")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("language_chip_english")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("lyrics_gen_mood_chip_powerful_uplifting_worshipful")
            .performScrollTo()
            .performClick()

        // Generate via Gemini AI
        composeRule.onNodeWithTag("lyrics_gen_submit_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, capturedRequests.size)
        val firstReq = capturedRequests.first()
        assertEquals("Roots in Zion", firstReq.title)
        assertEquals("African Gospel Worship", firstReq.genre)
        assertEquals("English", firstReq.language)
        assertEquals("Powerful, uplifting, worshipful", firstReq.mood)

        // Verify Song Title, Genre, Mood, Language, Model Badge, and Complete Lyrics displayed
        composeRule.onNodeWithTag("lyrics_gen_result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lyrics_gen_result_title")
            .performScrollTo()
            .assertTextContains("Roots in Zion")
        composeRule.onNodeWithTag("lyrics_gen_result_meta")
            .performScrollTo()
            .assertTextContains("African Gospel Worship • Powerful, uplifting, worshipful • English")
        composeRule.onNodeWithTag("lyrics_gen_result_badge")
            .assertTextContains("Gemini • gemini-3.8-flash", substring = true)
        composeRule.onNodeWithTag("lyrics_gen_result_body")
            .performScrollTo()
            .assertTextContains("Our roots in Zion never run dry", substring = true)
        composeRule.onNodeWithTag("lyrics_gen_save_button").performScrollTo().assertIsDisplayed()

        // Regenerate calls Gemini again and updates lyrics
        composeRule.onNodeWithTag("lyrics_gen_regenerate_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(2, capturedRequests.size)
        composeRule.onNodeWithTag("lyrics_gen_result_body")
            .performScrollTo()
            .assertTextContains("Stronger in Zion, lifted by Your grace", substring = true)

        // Edit and Copy
        composeRule.onNodeWithTag("lyrics_gen_edit_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("lyrics_gen_result_editor")
            .performScrollTo()
            .performTextReplacement("[Chorus]\nUpdated Zion refrain")
        composeRule.onNodeWithTag("lyrics_gen_copy_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lyrics_gen_feedback_chip").performScrollTo().assertIsDisplayed()

        // Use in Studio transfers updated lyrics, genre, mood, language, and title
        composeRule.onNodeWithTag("lyrics_gen_use_in_studio_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNotNull(transferredIdea)
        assertEquals("African Gospel Worship", transferredIdea?.genre)
        assertEquals("Powerful, uplifting, worshipful", transferredIdea?.mood)
        assertEquals("English", transferredIdea?.language)
        assertEquals("Roots in Zion", transferredIdea?.title)
        assertEquals("[Chorus]\nUpdated Zion refrain", transferredIdea?.lyricsPreview)
    }

    @Test
    fun lyricsGenerator_handlesCustomTypedGenreAndMood_andHonestRetryOnError() {
        var capturedRequest: AiLyricsRequest? = null
        var shouldFail = true
        val retryingGenerator = object : AiTextGenerator {
            override val modelName: String = "gemini-3.8-flash"

            override suspend fun generateLyrics(request: AiLyricsRequest): AiLyricsResult {
                capturedRequest = request
                if (shouldFail) {
                    throw AiGenerationException(
                        message = "Network unavailable for Gemini. Please tap Retry.",
                        reason = AiFailureReason.NETWORK_UNAVAILABLE
                    )
                }
                return AiLyricsResult(
                    title = "Morning Thanksgiving",
                    lyrics = "[Verse 1]\nWarm sunlight fills the island air\n\n[Chorus]\nEvery heartbeat lifts a prayer",
                    scriptureReference = "",
                    modelName = modelName,
                    isDemo = false
                )
            }

            override suspend fun generateIdeas(
                category: String?,
                genre: String?,
                mood: String?,
                language: String,
                batchSeed: Int
            ): AiIdeasResult = AiIdeasResult(emptyList(), modelName, isDemo = false)
        }

        val repository = MusicRepository(firestore, auth, retryingGenerator)

        composeRule.setContent {
            WordAndLightTheme {
                AiLyricsGeneratorScreen(
                    onNavigateBack = {},
                    repositoryOverride = repository
                )
            }
        }

        composeRule.onNodeWithTag("lyrics_gen_topic_input")
            .performScrollTo()
            .performTextInput("Morning thanksgiving")
        composeRule.onNodeWithTag("lyrics_gen_genre_input")
            .performScrollTo()
            .performTextReplacement("Reggae Gospel")
        composeRule.onNodeWithTag("lyrics_gen_mood_input")
            .performScrollTo()
            .performTextReplacement("Grateful")

        composeRule.onNodeWithTag("lyrics_gen_submit_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals("Reggae Gospel", capturedRequest?.genre)
        assertEquals("Grateful", capturedRequest?.mood)
        composeRule.onNodeWithTag("lyrics_gen_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lyrics_gen_fallback_demo_button").assertDoesNotExist()

        // Tap Retry once network returns -> Gemini generates real lyrics
        shouldFail = false
        composeRule.onNodeWithTag("lyrics_gen_retry_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lyrics_gen_result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lyrics_gen_result_badge")
            .assertTextContains("Gemini • gemini-3.8-flash", substring = true)
        composeRule.onNodeWithTag("lyrics_gen_result_body")
            .performScrollTo()
            .assertTextContains("Every heartbeat lifts a prayer", substring = true)
    }

    @Test
    fun appNavigation_opensAiLyricsGeneratorFromHomeAndStudioHub() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = MusicRepository(firestore, auth)
        val playerManager = MusicPlayerManager(context = context, connectController = false)

        composeRule.setContent {
            WordAndLightTheme {
                AppNavigation(
                    uid = "test_user",
                    repositoryOverride = repository,
                    playerManagerOverride = playerManager
                )
            }
        }
        composeRule.waitForIdle()

        // Open from Home shortcut
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_studio_shortcut_lyrics"))
        composeRule.onNodeWithTag("home_studio_shortcut_lyrics").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_lyrics_generator_screen").assertIsDisplayed()

        // Back to Home, then open from Studio Hub
        composeRule.onNodeWithTag("lyrics_screen_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Studio").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_lazy_column")
            .performScrollToNode(hasTestTag("hub_ai_lyrics_generator_card"))
        composeRule.onNodeWithTag("hub_ai_lyrics_generator_card").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_lyrics_generator_screen").assertIsDisplayed()
    }

    @Test
    fun lyricsGenerator_streamsLiveCard_andSupportsBilingualCaptionSync() {
        val releaseCompletion = CompletableDeferred<Unit>()
        val streamingGenerator = object : AiTextGenerator {
            override val modelName: String = "gemini-3.8-flash"

            override suspend fun generateLyrics(request: AiLyricsRequest): AiLyricsResult {
                error("Uses stream")
            }

            override fun generateLyricsStream(request: AiLyricsRequest): Flow<LyricsStreamEvent> = flow {
                emit(LyricsStreamEvent.Chunk("Title: Aseda Light\n\n[Verse 1]\nOnyame adom hyerɛn"))
                releaseCompletion.await()
                val finalLyrics = "Title: Aseda Light\n\n[Verse 1]\nOnyame adom hyerɛn\n\n[Chorus]\nAyeyi nka Wo din / Praise Your holy name"
                emit(
                    LyricsStreamEvent.Completed(
                        AiLyricsResult(
                            title = "Aseda Light",
                            lyrics = finalLyrics,
                            scriptureReference = request.scriptureReference,
                            modelName = modelName,
                            isDemo = false
                        )
                    )
                )
            }

            override suspend fun generateIdeas(
                category: String?,
                genre: String?,
                mood: String?,
                language: String,
                batchSeed: Int
            ): AiIdeasResult = AiIdeasResult(emptyList(), modelName, isDemo = false)
        }

        val repository = MusicRepository(firestore, auth, streamingGenerator)

        composeRule.setContent {
            WordAndLightTheme {
                AiLyricsGeneratorScreen(
                    onNavigateBack = {},
                    repositoryOverride = repository
                )
            }
        }

        composeRule.onNodeWithTag("lyrics_gen_topic_input")
            .performScrollTo()
            .performTextInput("Thanksgiving and Light")
        composeRule.onNodeWithTag("lyrics_gen_submit_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // Streaming card renders partial text live
        composeRule.onNodeWithTag("lyrics_gen_streaming_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lyrics_gen_streaming_body")
            .assertTextContains("Onyame adom hyerɛn", substring = true)

        // Complete stream -> transitions to result card and bilingual caption sync
        releaseCompletion.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lyrics_gen_result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lyrics_gen_captions_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lyrics_gen_bilingual_cues_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lyrics_gen_autosync_cues_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lyrics_gen_feedback_chip")
            .performScrollTo()
            .assertTextContains("Synced", substring = true)
    }

    @Test
    fun libraryScreen_inlineLyricEditDialog_displaysAndValidatesEdits() {
        var savedUpdate: Lyric? = null
        val initialLyric = Lyric(
            id = "lyric_test_1",
            userId = "user_1",
            title = "Morning Praise",
            lyrics = "[Verse 1]\nOnyame adom hyerɛn\n[Chorus]\nAyeyi nka Wo din",
            topic = "Grace",
            genre = "Gospel",
            mood = "Uplifting",
            language = "Mixed English + Twi",
            scriptureReference = "Psalm 23:1",
            generationSource = "AI_GEMINI"
        )

        composeRule.setContent {
            WordAndLightTheme {
                com.aistudio.aimusic.wltv.jqr8x.ui.library.LyricDetailAndEditDialog(
                    lyric = initialLyric,
                    initialEditMode = false,
                    onDismiss = {},
                    onCopy = {},
                    onReuseInStudio = null,
                    onSaveEdits = { updated -> savedUpdate = updated }
                )
            }
        }
        composeRule.waitForIdle()

        // View mode renders body and Edit button
        composeRule.onNodeWithTag("library_lyric_view_body")
            .assertTextContains("Onyame adom hyerɛn", substring = true)
        composeRule.onNodeWithTag("library_lyric_edit_button").performClick()
        composeRule.waitForIdle()

        // Edit fields, auto-sync bilingual captions, and save changes
        composeRule.onNodeWithTag("library_lyric_edit_title")
            .performScrollTo()
            .performTextReplacement("Morning Praise Revised")
        composeRule.onNodeWithTag("library_lyric_edit_body")
            .performScrollTo()
            .performTextReplacement("[Verse 1]\nOnyame ne yɛn hann / God is our light\n[Chorus]\nAyeyi nka Wo din")
        composeRule.onNodeWithTag("library_lyric_edit_sync_cues_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_lyric_save_button").performClick()
        composeRule.waitForIdle()

        assertNotNull(savedUpdate)
        assertEquals("Morning Praise Revised", savedUpdate?.title)
        assertEquals(2, savedUpdate?.captionCues?.size)
        assertEquals(true, savedUpdate?.lyrics?.contains("God is our light"))
    }
}
