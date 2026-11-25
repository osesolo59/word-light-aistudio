package com.aistudio.aimusic.wltv.jqr8x.ui.mixer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.create.CreateScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.library.AudioMixesTabContent
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioMixerAndBilingualUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var testRepository: MusicRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "mixer-ui-test-app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.aimusic.wltv.jqr8x")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build(),
                "mixer-ui-test-app"
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
    fun createScreen_mixedEnglishTwi_showsSectionPlannerAndMixerShortcut() {
        var mixerOpened = false
        composeRule.setContent {
            WordAndLightTheme {
                CreateScreen(
                    onNavigateToLibrary = {},
                    repositoryOverride = testRepository,
                    onNavigateToAudioMixer = { mixerOpened = true }
                )
            }
        }

        composeRule.onNodeWithTag("create_tab_custom").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("open_audio_mixer_button").performClick()
        assertTrue(mixerOpened)

        composeRule.onNodeWithTag("language_chip_mixed_english_twi").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("section_language_planner_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("section_preset_twi_verses_english_chorus").performScrollTo().performClick()
        composeRule.onNodeWithTag("sec_lang_chorus_english").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun audioMixerScreen_demoLyricsSampleTracksAndTrimMuteControls_workEndToEnd() {
        val viewModel = AudioMixerViewModel(testRepository)
        composeRule.setContent {
            WordAndLightTheme {
                AudioMixerScreen(
                    onNavigateToMusicVideoCreator = { _, _, _, _, _ -> },
                    onNavigateToProjects = {},
                    repositoryOverride = testRepository,
                    viewModelOverride = viewModel
                )
            }
        }

        composeRule.onNodeWithTag("mixer_title_input").performTextInput("Ayeyi Morning Praise")
        composeRule.onNodeWithTag("mixer_demo_lyrics_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        val stateAfterLyrics = viewModel.uiState.value
        assertTrue(stateAfterLyrics.lyrics.contains("[Chorus"))
        assertTrue(stateAfterLyrics.captionCues.isNotEmpty())

        // Add sample WAV pad track on device and test track mute/remove UI
        composeRule.onNodeWithTag("mixer_add_sample_track_button").performScrollTo().performClick()
        try {
            composeRule.waitUntil(10_000L) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.tracks.size == 1
            }
        } catch (e: Throwable) {
            val s = viewModel.uiState.value
            throw AssertionError(
                "tracks=${s.tracks.size}, isImporting=${s.isImportingAudio}, feedback=${s.feedbackMessage}, error=${s.errorMessage}",
                e
            )
        }

        composeRule.onNodeWithTag("mixer_track_card_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mixer_track_role_0_vocal").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            com.aistudio.aimusic.wltv.jqr8x.data.model.AudioTrackRole.VOCAL,
            viewModel.uiState.value.tracks.first().role
        )

        composeRule.onNodeWithTag("pro_audio_mastering_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mastering_preset_broadcast_crisp").performScrollTo().performClick()
        composeRule.onNodeWithTag("mixer_export_format_wav_48000").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            com.aistudio.aimusic.wltv.jqr8x.data.model.MasteringPreset.BROADCAST_CRISP,
            viewModel.uiState.value.masteringSettings.preset
        )
        assertEquals(
            com.aistudio.aimusic.wltv.jqr8x.data.model.AudioExportFormat.WAV_48000,
            viewModel.uiState.value.selectedExportFormat
        )
        composeRule.onNodeWithTag("mixer_export_format_mp3_320").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            com.aistudio.aimusic.wltv.jqr8x.data.model.AudioExportFormat.MP3_320,
            viewModel.uiState.value.selectedExportFormat
        )

        composeRule.onNodeWithTag("mixer_track_mute_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(viewModel.uiState.value.tracks.first().isMuted)
    }

    @Test
    fun audioMixesTabContent_rendersMixCardAndDispatchesReopenMusicVideoAndDelete() {
        val sampleMix = AudioMixProject(
            id = "mix_123",
            title = "Onyame Ye",
            language = "Mixed English + Twi",
            lyrics = "[Chorus]\nAyeyi nka Wo",
            captionCues = listOf(TimedBilingualCue("c1", 0, 5, "Chorus", "Praise You", "Ayeyi nka Wo")),
            tracks = listOf(AudioMixTrack(id = "t1", displayName = "Lead.m4a", formatLabel = "M4A", durationMs = 5000L)),
            totalDurationMs = 5000L
        )
        var reopenedId = ""
        var mvTitle = ""
        var deletedId = ""

        composeRule.setContent {
            WordAndLightTheme {
                AudioMixesTabContent(
                    mixes = listOf(sampleMix),
                    onPlayMix = {},
                    onReopenInMixer = { reopenedId = it.id },
                    onUseInMusicVideo = { mvTitle = it.title },
                    onSaveMixToDevice = {},
                    onShareMix = {},
                    onDeleteMix = { deletedId = it.id }
                )
            }
        }

        composeRule.onNodeWithTag("audio_mix_card_mix_123").assertIsDisplayed()
        composeRule.onNodeWithText("Onyame Ye").assertIsDisplayed()
        composeRule.onNodeWithTag("reopen_mix_mix_123").performClick()
        composeRule.onNodeWithTag("use_mix_mv_mix_123").performClick()
        composeRule.onNodeWithTag("delete_mix_mix_123").performClick()

        assertEquals("mix_123", reopenedId)
        assertEquals("Onyame Ye", mvTitle)
        assertEquals("mix_123", deletedId)
    }
}
