package com.aistudio.aimusic.wltv.jqr8x.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.AppContent
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.auth.AuthScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.explore.ExploreScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.explore.FavoritesScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.library.LibraryScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.player.MusicPlayerManager
import com.aistudio.aimusic.wltv.jqr8x.ui.profile.SettingsScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import org.junit.Assert.assertEquals
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
class AuditScreensUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var testRepository: MusicRepository

    @Before
    fun setUpTestRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "audit-ui-test-app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.aimusic.wltv.jqr8x")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build(),
                "audit-ui-test-app"
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
    fun libraryScreen_allFiveTabsRenderCleanlyIncludingLyricsAndPlaylists() {
        composeRule.setContent {
            WordAndLightTheme {
                LibraryScreen(
                    onNavigateToFavorites = {},
                    repositoryOverride = testRepository
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library_tab_music").assertIsDisplayed()
        composeRule.onNodeWithTag("library_tab_videos").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No videos yet").assertIsDisplayed()

        composeRule.onNodeWithTag("library_tab_creator").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No music videos yet").assertIsDisplayed()

        composeRule.onNodeWithTag("library_tab_lyrics").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No saved lyrics yet").assertIsDisplayed()

        composeRule.onNodeWithTag("library_tab_playlists").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_new_playlist_button").assertIsDisplayed()
        composeRule.onNodeWithText("No playlists yet").assertIsDisplayed()
    }

    @Test
    fun favoritesScreen_displaysBackButtonAndEmptyState() {
        var navigatedBack = false
        composeRule.setContent {
            WordAndLightTheme {
                FavoritesScreen(
                    onNavigateBack = { navigatedBack = true },
                    repositoryOverride = testRepository
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("favorites_back_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(navigatedBack)
        composeRule.onNodeWithText("No favorites yet").assertIsDisplayed()
    }

    @Test
    fun exploreScreen_showsHelpfulEmptyStateWhenNoPublicTracks() {
        composeRule.setContent {
            WordAndLightTheme {
                ExploreScreen(repositoryOverride = testRepository)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No public tracks yet").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_languageDialogListsAllFiveGhanaianLanguages() {
        composeRule.setContent {
            WordAndLightTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    onNavigateToEditProfile = {},
                    repositoryOverride = testRepository
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Language").performScrollTo().performClick()
        composeRule.waitForIdle()

        listOf("english", "twi", "akan", "ga", "ewe", "french").forEach { lang ->
            composeRule.onNodeWithTag("settings_language_option_$lang").assertIsDisplayed()
        }
    }

    @Test
    fun musicPlayerManager_stopClearsTitleAndQueue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = MusicPlayerManager(context = context, connectController = false)
        manager.playSong("https://example.com/song.mp3", "Onyame Ne Hann")
        assertEquals("Onyame Ne Hann", manager.currentSongTitle.value)

        manager.stop()
        assertNull(manager.currentSongTitle.value)
        assertEquals(false, manager.isPlaying.value)
        assertTrue(manager.queueTitles.value.isEmpty())
        manager.release()
    }

    @Test
    fun appContent_withoutDefaultFirebaseApp_showsConfigMessageWithoutCrashing() {
        composeRule.setContent {
            AppContent(firebaseReadyOverride = false)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("retry_firebase_init_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("retry_firebase_init_button").assertIsDisplayed()
    }

    @Test
    fun authScreen_rendersSignInScreenAndGoogleClientResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clientIdRes = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        assertTrue("Expected default_web_client_id from google-services.json", clientIdRes != 0)
        assertTrue(context.getString(clientIdRes).isNotBlank())

        composeRule.setContent {
            WordAndLightTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("WORD & LIGHT AI STUDIO").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
    }
}
