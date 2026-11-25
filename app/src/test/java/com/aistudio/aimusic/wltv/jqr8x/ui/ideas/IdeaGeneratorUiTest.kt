package com.aistudio.aimusic.wltv.jqr8x.ui.ideas

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.data.demo.IdeaGeneratorEngine
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.model.StudioIdeaDraft
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.create.CreateScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.home.HomeScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.navigation.AppNavigation
import com.aistudio.aimusic.wltv.jqr8x.ui.studio.MusicVideoCreatorScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.studio.StudioSelectionScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.studio.VideoStudioScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IdeaGeneratorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var testRepository: MusicRepository

    @Before
    fun setUpTestRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "ui-test-app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.aimusic.wltv.jqr8x")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build(),
                "ui-test-app"
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

    @Test
    fun homeScreen_tappingAiIdeaGeneratorCard_triggersNavigation() {
        var navigated = false
        composeRule.setContent {
            WordAndLightTheme {
                HomeScreen(
                    onNavigateToAudio = {},
                    onNavigateToVideo = {},
                    onNavigateToCreator = {},
                    onNavigateToIdeaGenerator = { navigated = true },
                    repositoryOverride = testRepository
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_ai_idea_generator_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_ai_idea_generator_card").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(navigated)
    }

    @Test
    fun studioHub_tappingAiIdeaGeneratorCard_triggersNavigation() {
        var navigated = false
        composeRule.setContent {
            WordAndLightTheme {
                StudioSelectionScreen(
                    onNavigateToAudio = {},
                    onNavigateToVideo = {},
                    onNavigateToCreator = {},
                    onNavigateToIdeaGenerator = { navigated = true },
                    repositoryOverride = testRepository
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_lazy_column")
            .performScrollToNode(hasTestTag("hub_ai_idea_generator_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_ai_idea_generator_card").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(navigated)
    }

    @Test
    fun appNavigation_homeAndStudioHub_openFullAiIdeaGeneratorScreen_andGenerateFiveIdeas() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPlayerManager = com.aistudio.aimusic.wltv.jqr8x.ui.player.MusicPlayerManager(
            context = context,
            connectController = false
        )
        composeRule.setContent {
            WordAndLightTheme {
                AppNavigation(
                    uid = "demo_user",
                    repositoryOverride = testRepository,
                    playerManagerOverride = testPlayerManager
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Scroll Home LazyColumn to AI Idea Generator card and tap it
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_ai_idea_generator_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_ai_idea_generator_card").performClick()
        composeRule.waitForIdle()

        // Full AI Idea Generator screen is open
        composeRule.onNodeWithTag("ai_idea_generator_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("idea_screen_header").assertIsDisplayed()

        // 2. Tap Generate Ideas -> offline Robolectric shows fallback banner -> tap Use Demo Ideas Instead -> displays 5 ideas
        composeRule.onNodeWithTag("idea_screen_generate_ideas_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("idea_screen_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("idea_screen_fallback_demo_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        for (i in 0..4) {
            composeRule.onNodeWithTag("idea_screen_suggestion_card_$i").performScrollTo().assertIsDisplayed()
        }

        // 3. Tap Use This Idea -> transfers to Audio/Lyrics Studio cleanly
        composeRule.onNodeWithTag("idea_screen_use_idea_button_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_idea_transferred_banner").assertIsDisplayed()

        // 4. Switch to Studio Hub bottom bar tab and tap AI Idea Generator card there
        composeRule.onNodeWithText("Studio").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_lazy_column")
            .performScrollToNode(hasTestTag("hub_ai_idea_generator_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_ai_idea_generator_card").performClick()
        composeRule.waitForIdle()

        // Full AI Idea Generator screen opens again with existing 5 ideas preserved
        composeRule.onNodeWithTag("ai_idea_generator_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("idea_screen_suggestion_card_0").performScrollTo().assertIsDisplayed()

        // 5. Tap top back button -> returns to Studio Hub without blank screen
        composeRule.onNodeWithTag("idea_screen_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_ai_idea_generator_card").assertIsDisplayed()
    }

    @Test
    fun appNavigation_useThisIdea_transfersFullDetailsToAllFourStudios() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPlayerManager = com.aistudio.aimusic.wltv.jqr8x.ui.player.MusicPlayerManager(
            context = context,
            connectController = false
        )
        composeRule.setContent {
            WordAndLightTheme {
                AppNavigation(
                    uid = "demo_user",
                    repositoryOverride = testRepository,
                    playerManagerOverride = testPlayerManager
                )
            }
        }
        composeRule.waitForIdle()

        // --- 1. AUDIO STUDIO (Song Ideas / Twi / Praise) ---
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_studio_shortcut_ai_ideas"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_studio_shortcut_ai_ideas").performClick()
        composeRule.waitForIdle()
        // Switch AI Idea Generator into Free Demo Mode for deterministic template transfer assertions
        composeRule.onNodeWithTag("idea_screen_mode_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("idea_screen_language_twi").performScrollTo().performClick()
        composeRule.onNodeWithTag("idea_screen_category_song_ideas").performScrollTo().performClick()
        composeRule.onNodeWithTag("idea_screen_generate_ideas_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("idea_screen_use_idea_button_0").performScrollTo().performClick()
        composeRule.waitForIdle()

        val expectedAudioIdea = IdeaGeneratorEngine.generateIdeas(
            category = "Song Ideas",
            language = "Twi",
            batchSeed = 0
        ).first()
        composeRule.onNodeWithTag("create_idea_transferred_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("simple_title_input")
            .performScrollTo()
            .assertTextContains(expectedAudioIdea.title, substring = true)
        composeRule.onNodeWithTag("simple_description_input")
            .performScrollTo()
            .assertTextContains(expectedAudioIdea.topic, substring = true)
        composeRule.onNodeWithTag("custom_mood_input")
            .performScrollTo()
            .assertTextContains(expectedAudioIdea.mood, substring = true)
        // Switching to Custom tab should preserve all lyrics/topic/title fields too
        composeRule.onNodeWithTag("create_tab_custom").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("custom_title_input")
            .performScrollTo()
            .assertTextContains(expectedAudioIdea.title, substring = true)
        composeRule.onNodeWithTag("custom_topic_input")
            .performScrollTo()
            .assertTextContains(expectedAudioIdea.topic, substring = true)
        composeRule.onNodeWithTag("custom_lyrics_input")
            .performScrollTo()
            .assertTextContains("[Verse 1 -", substring = true)

        // --- 2. LYRICS STUDIO (Lyrics Ideas / Ga) ---
        composeRule.onNodeWithText("Studio").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_lazy_column")
            .performScrollToNode(hasTestTag("hub_ai_idea_generator_card"))
        composeRule.onNodeWithTag("hub_ai_idea_generator_card").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("idea_screen_language_ga").performScrollTo().performClick()
        composeRule.onNodeWithTag("idea_screen_category_lyrics_ideas").performScrollTo().performClick()
        composeRule.onNodeWithTag("idea_screen_generate_ideas_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("idea_screen_use_idea_button_0").performScrollTo().performClick()
        composeRule.waitForIdle()

        val expectedLyricsIdea = IdeaGeneratorEngine.generateIdeas(
            category = "Lyrics Ideas",
            language = "Ga",
            batchSeed = 1
        ).first()
        composeRule.onNodeWithTag("create_idea_transferred_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_topic_input")
            .performScrollTo()
            .assertTextContains(expectedLyricsIdea.topic, substring = true)
        composeRule.onNodeWithTag("custom_title_input")
            .performScrollTo()
            .assertTextContains(expectedLyricsIdea.title, substring = true)
        composeRule.onNodeWithTag("custom_lyrics_input")
            .performScrollTo()
            .assertTextContains("| Ga]", substring = true)
        composeRule.onNodeWithTag("custom_mood_input")
            .performScrollTo()
            .assertTextContains(expectedLyricsIdea.mood, substring = true)

        // --- 3. VIDEO STUDIO (Christian Video Ideas / Ewe) ---
        composeRule.onNodeWithText("Studio").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_lazy_column")
            .performScrollToNode(hasTestTag("hub_ai_idea_generator_card"))
        composeRule.onNodeWithTag("hub_ai_idea_generator_card").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("idea_screen_language_ewe").performScrollTo().performClick()
        composeRule.onNodeWithTag("idea_screen_category_christian_video_ideas").performScrollTo().performClick()
        composeRule.onNodeWithTag("idea_screen_generate_ideas_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("idea_screen_use_idea_button_0").performScrollTo().performClick()
        composeRule.waitForIdle()

        val expectedVideoIdea = IdeaGeneratorEngine.generateIdeas(
            category = "Christian Video Ideas",
            language = "Ewe",
            batchSeed = 2
        ).first()
        composeRule.onNodeWithTag("video_idea_transferred_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("video_idea_details_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("video_title_input")
            .performScrollTo()
            .assertTextContains(expectedVideoIdea.title, substring = true)
        composeRule.onNodeWithTag("video_prompt_input")
            .performScrollTo()
            .assertTextContains(expectedVideoIdea.videoPrompt, substring = true)
        composeRule.onNodeWithText("Genre: ${expectedVideoIdea.genre} • Language: Ewe • Mood: ${expectedVideoIdea.mood}")
            .performScrollTo()
            .assertIsDisplayed()

        // --- 4. MUSIC VIDEO CREATOR (Music Video Ideas / Akan) ---
        composeRule.onNodeWithText("Studio").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_lazy_column")
            .performScrollToNode(hasTestTag("hub_ai_idea_generator_card"))
        composeRule.onNodeWithTag("hub_ai_idea_generator_card").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("idea_screen_language_akan").performScrollTo().performClick()
        composeRule.onNodeWithTag("idea_screen_category_music_video_ideas").performScrollTo().performClick()
        composeRule.onNodeWithTag("idea_screen_generate_ideas_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("idea_screen_use_idea_button_0").performScrollTo().performClick()
        composeRule.waitForIdle()

        val expectedMvIdea = IdeaGeneratorEngine.generateIdeas(
            category = "Music Video Ideas",
            language = "Akan",
            batchSeed = 3
        ).first()
        composeRule.onNodeWithTag("mv_idea_transferred_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mv_idea_details_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mv_title_input")
            .performScrollTo()
            .assertTextContains(expectedMvIdea.title, substring = true)
        composeRule.onNodeWithTag("mv_bible_verse_input")
            .performScrollTo()
            .assertTextContains(expectedMvIdea.bibleVerse, substring = true)
        composeRule.onNodeWithText("Genre: ${expectedMvIdea.genre} • Language: Akan • Mood: ${expectedMvIdea.mood}")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun aiIdeaGeneratorSection_generates5Ideas_supportsMoreSaveCopyAndUse() {
        var usedIdea: CreativeIdea? = null
        var savedIdea: CreativeIdea? = null
        var copiedIdea: CreativeIdea? = null

        composeRule.setContent {
            var state by remember { mutableStateOf(IdeaGeneratorUiState()) }
            WordAndLightTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AiIdeaGeneratorSection(
                        uiState = state,
                        onSelectCategory = { cat ->
                            val next = if (state.selectedCategory == cat) null else cat
                            state = state.copy(selectedCategory = next)
                        },
                        onSelectGenre = { genre ->
                            val next = if (state.selectedGenre == genre) null else genre
                            state = state.copy(selectedGenre = next)
                        },
                        onSelectMood = { mood ->
                            val next = if (state.selectedMood == mood) null else mood
                            state = state.copy(selectedMood = next)
                        },
                        onSelectLanguage = { lang ->
                            state = state.copy(selectedLanguage = lang)
                        },
                        onGenerateIdeas = {
                            val ideas = IdeaGeneratorEngine.generateIdeas(
                                category = state.selectedCategory,
                                genre = state.selectedGenre,
                                mood = state.selectedMood,
                                language = state.selectedLanguage,
                                batchSeed = state.batchSeed
                            )
                            state = state.copy(generatedIdeas = ideas)
                        },
                        onGenerateMore = {
                            val nextSeed = state.batchSeed + 1
                            val ideas = IdeaGeneratorEngine.generateIdeas(
                                category = state.selectedCategory,
                                genre = state.selectedGenre,
                                mood = state.selectedMood,
                                language = state.selectedLanguage,
                                batchSeed = nextSeed
                            )
                            state = state.copy(batchSeed = nextSeed, generatedIdeas = ideas)
                        },
                        onUseIdea = { idea -> usedIdea = idea },
                        onSaveIdea = { idea ->
                            savedIdea = idea
                            state = state.copy(
                                savedIdeaIds = state.savedIdeaIds + idea.id,
                                feedbackMessage = "Saved \"${idea.title}\""
                            )
                        },
                        onDeleteSavedIdea = {},
                        onIdeaCopied = { idea ->
                            copiedIdea = idea
                            state = state.copy(feedbackMessage = "Copied \"${idea.title}\"")
                        },
                        onDismissFeedback = { state = state.copy(feedbackMessage = null) },
                        tagPrefix = "home_idea"
                    )
                }
            }
        }

        // Verify languages exist and tap Twi
        composeRule.onNodeWithTag("home_idea_language_english").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_idea_language_akan").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_idea_language_ga").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_idea_language_ewe").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_idea_language_twi").performScrollTo().performClick()

        // Generate 5 ideas immediately without entering any prompt
        composeRule.onNodeWithTag("home_idea_generate_ideas_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Verify all 5 cards and 4 action buttons
        for (i in 0..4) {
            composeRule.onNodeWithTag("home_idea_suggestion_card_$i").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag("home_idea_use_idea_button_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_idea_generate_more_button_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_idea_save_idea_button_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_idea_copy_idea_button_0").performScrollTo().assertIsDisplayed()

        // Test Copy Idea & Save Idea
        composeRule.onNodeWithTag("home_idea_copy_idea_button_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNotNull(copiedIdea)

        composeRule.onNodeWithTag("home_idea_save_idea_button_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNotNull(savedIdea)

        // Test Generate More
        val firstBatchTitle = savedIdea!!.title
        composeRule.onNodeWithTag("home_idea_generate_more_button_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_idea_use_idea_button_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNotNull(usedIdea)
        assertEquals("Twi", usedIdea!!.language)
        assertEquals(true, usedIdea!!.title != firstBatchTitle)
    }

    @Test
    fun useThisIdea_transfersFieldsIntoAudioAndLyricsStudio() {
        val lyricsIdea = IdeaGeneratorEngine.generateIdeas(
            category = "Lyrics Ideas",
            genre = "Highlife",
            mood = "Joyful",
            language = "Twi"
        ).first()

        var consumedId: String? = null
        composeRule.setContent {
            var draft by remember {
                mutableStateOf<StudioIdeaDraft?>(StudioIdeaDraft("transfer_lyrics_1", lyricsIdea))
            }
            WordAndLightTheme {
                CreateScreen(
                    onNavigateToLibrary = {},
                    repositoryOverride = testRepository,
                    ideaDraft = draft,
                    onConsumeIdeaDraft = { id ->
                        consumedId = id
                        draft = null
                    }
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals("transfer_lyrics_1", consumedId)
        composeRule.onNodeWithTag("create_idea_transferred_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_title_input")
            .performScrollTo()
            .assertTextContains(lyricsIdea.title, substring = true)
        composeRule.onNodeWithTag("custom_lyrics_input")
            .performScrollTo()
            .assertTextContains("[Verse 1 - Highlife | Twi]", substring = true)
    }

    @Test
    fun useThisIdea_transfersFieldsIntoVideoStudio() {
        val videoIdea = IdeaGeneratorEngine.generateIdeas(
            category = "Christian Video Ideas",
            language = "Ga"
        ).first()
        var draftState: StudioIdeaDraft? = StudioIdeaDraft("transfer_video_1", videoIdea)

        composeRule.setContent {
            var draft by remember { mutableStateOf(draftState) }
            WordAndLightTheme {
                VideoStudioScreen(
                    onNavigateToProjects = {},
                    repositoryOverride = testRepository,
                    ideaDraft = draft,
                    onConsumeIdeaDraft = {
                        draft = null
                        draftState = null
                    }
                )
            }
        }
        composeRule.waitForIdle()
        assertNull(draftState)
        composeRule.onNodeWithTag("video_idea_transferred_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("video_prompt_input")
            .assertTextContains(videoIdea.videoPrompt, substring = true)
    }

    @Test
    fun useThisIdea_transfersFieldsIntoMusicVideoCreator() {
        val mvIdea = IdeaGeneratorEngine.generateIdeas(
            category = "Music Video Ideas",
            language = "Ewe"
        ).first()
        var consumed = false

        composeRule.setContent {
            var draft by remember { mutableStateOf<StudioIdeaDraft?>(StudioIdeaDraft("transfer_mv_1", mvIdea)) }
            WordAndLightTheme {
                MusicVideoCreatorScreen(
                    onNavigateToProjects = {},
                    repositoryOverride = testRepository,
                    ideaDraft = draft,
                    onConsumeIdeaDraft = {
                        consumed = true
                        draft = null
                    }
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(true, consumed)
        composeRule.onNodeWithTag("mv_idea_transferred_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("mv_title_input").assertTextContains(mvIdea.title, substring = true)
        composeRule.onNodeWithTag("mv_bible_verse_input").assertTextContains(mvIdea.bibleVerse, substring = true)
    }
}
