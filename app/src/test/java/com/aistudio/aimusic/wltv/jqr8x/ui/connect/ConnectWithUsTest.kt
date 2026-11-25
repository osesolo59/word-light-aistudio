package com.aistudio.aimusic.wltv.jqr8x.ui.connect

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.onNodeWithText
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.data.social.SecureExternalLinkOpener
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinkValidator
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinksRepository
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialPlatform
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialUrlValidationResult
import com.aistudio.aimusic.wltv.jqr8x.data.social.VERIFIED_FACEBOOK_SHARE_URL
import com.aistudio.aimusic.wltv.jqr8x.data.social.VERIFIED_TIKTOK_PROFILE_URL
import com.aistudio.aimusic.wltv.jqr8x.data.social.VERIFIED_WHATSAPP_CONTACT_NUMBER
import com.aistudio.aimusic.wltv.jqr8x.data.social.VERIFIED_WHATSAPP_URL
import com.aistudio.aimusic.wltv.jqr8x.data.social.VERIFIED_YOUTUBE_CHANNEL_URL
import com.aistudio.aimusic.wltv.jqr8x.ui.home.HomeScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.profile.SettingsScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectWithUsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var musicRepository: MusicRepository
    private lateinit var socialRepository: SocialLinksRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SocialLinksRepository.resetSharedInstanceForTests()
        val prefsName = "test_social_links_${System.nanoTime()}"
        socialRepository = SocialLinksRepository(context, prefsName = prefsName)

        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "connect-ui-test-app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.aimusic.wltv.jqr8x")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build(),
                "connect-ui-test-app"
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
        musicRepository = MusicRepository(firestore, auth)
    }

    @Test
    fun `default configuration includes verified YouTube Facebook TikTok and WhatsApp and hides unconfigured platforms`() {
        assertEquals(8, SocialPlatform.entries.size)

        val expectedConfigured = listOf(
            SocialPlatform.YOUTUBE to VERIFIED_YOUTUBE_CHANNEL_URL,
            SocialPlatform.FACEBOOK to VERIFIED_FACEBOOK_SHARE_URL,
            SocialPlatform.TIKTOK to VERIFIED_TIKTOK_PROFILE_URL,
            SocialPlatform.WHATSAPP to VERIFIED_WHATSAPP_URL
        )

        val configured = socialRepository.getConfiguredLinks()
        assertEquals(4, configured.size)
        assertEquals(expectedConfigured, configured.map { it.platform to it.url })

        val hiddenPlatforms = SocialPlatform.entries.filter { platform ->
            expectedConfigured.none { it.first == platform }
        }
        hiddenPlatforms.forEach { platform ->
            assertEquals("", socialRepository.getLink(platform))
        }

        composeRule.setContent {
            WordAndLightTheme {
                ConnectWithUsScreen(
                    onNavigateBack = {},
                    socialLinksRepositoryOverride = socialRepository
                )
            }
        }
        composeRule.waitForIdle()

        expectedConfigured.forEach { (platform, _) ->
            composeRule.onNodeWithTag("connect_with_us_list")
                .performScrollToNode(hasTestTag("connect_platform_card_${platform.key}"))
            composeRule.onNodeWithTag("connect_platform_card_${platform.key}").assertIsDisplayed()
        }

        composeRule.onNodeWithText("Word & Light TV • $VERIFIED_WHATSAPP_CONTACT_NUMBER").assertIsDisplayed()
        composeRule.onNodeWithText("CHAT WITH US", useUnmergedTree = true).assertIsDisplayed()

        hiddenPlatforms.forEach { platform ->
            composeRule
                .onAllNodesWithTag("connect_platform_card_${platform.key}")
                .assertCountEquals(0)
        }
    }

    @Test
    fun `secure url validator allows verified https links and blocks unsafe schemes`() {
        listOf(
            VERIFIED_YOUTUBE_CHANNEL_URL,
            VERIFIED_FACEBOOK_SHARE_URL,
            VERIFIED_TIKTOK_PROFILE_URL,
            VERIFIED_WHATSAPP_URL
        ).forEach { verified ->
            val result = SocialLinkValidator.validate("  $verified  ")
            assertTrue(result is SocialUrlValidationResult.Valid)
            assertEquals(verified, (result as SocialUrlValidationResult.Valid).normalizedUrl)
        }

        assertTrue(SocialLinkValidator.validate("") is SocialUrlValidationResult.Empty)
        assertTrue(SocialLinkValidator.validate("   ") is SocialUrlValidationResult.Empty)

        val unsafeUrls = listOf(
            "http://www.youtube.com/@WordAndLightTV-v6c",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "content://media/external/images/media/1",
            "https://user:pass@example.com",
            "https://localhost/admin",
            "https://invalid host"
        )
        unsafeUrls.forEach { unsafe ->
            assertTrue("Expected $unsafe to be invalid", SocialLinkValidator.validate(unsafe) is SocialUrlValidationResult.Invalid)
            assertNull("Expected no intent for $unsafe", SecureExternalLinkOpener.buildSafeExternalIntent(unsafe))
        }
    }

    @Test
    fun `tapping verified YouTube Facebook TikTok and WhatsApp CHAT WITH US dispatches secure ACTION_VIEW intents`() {
        val shadowApp = Shadows.shadowOf(context as Application)

        composeRule.setContent {
            WordAndLightTheme {
                ConnectWithUsScreen(
                    onNavigateBack = {},
                    socialLinksRepositoryOverride = socialRepository
                )
            }
        }
        composeRule.waitForIdle()

        val platformUrls = listOf(
            SocialPlatform.YOUTUBE to VERIFIED_YOUTUBE_CHANNEL_URL,
            SocialPlatform.FACEBOOK to VERIFIED_FACEBOOK_SHARE_URL,
            SocialPlatform.TIKTOK to VERIFIED_TIKTOK_PROFILE_URL
        )

        platformUrls.forEach { (platform, expectedUrl) ->
            shadowApp.clearNextStartedActivities()
            composeRule.onNodeWithTag("connect_with_us_list")
                .performScrollToNode(hasTestTag("connect_platform_card_${platform.key}"))
            composeRule.onNodeWithTag("connect_platform_card_${platform.key}").performClick()
            composeRule.waitForIdle()

            val startedIntent = shadowApp.nextStartedActivity
            assertNotNull("Expected secure external intent for ${platform.displayName}", startedIntent)
            assertEquals(Intent.ACTION_VIEW, startedIntent.action)
            assertTrue(startedIntent.hasCategory(Intent.CATEGORY_BROWSABLE))
            assertEquals(Uri.parse(expectedUrl), startedIntent.data)
        }

        // Verify clicking CHAT WITH US opens direct WhatsApp conversation with +233245200758
        shadowApp.clearNextStartedActivities()
        composeRule.onNodeWithTag("connect_with_us_list")
            .performScrollToNode(hasTestTag("connect_platform_card_whatsapp"))
        composeRule.onNodeWithTag("connect_whatsapp_chat_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        val whatsappIntent = shadowApp.nextStartedActivity
        assertNotNull("Expected direct WhatsApp intent from CHAT WITH US button", whatsappIntent)
        assertEquals(Intent.ACTION_VIEW, whatsappIntent.action)
        assertTrue(whatsappIntent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals(Uri.parse(VERIFIED_WHATSAPP_URL), whatsappIntent.data)
    }

    @Test
    fun `admin configuration persists updates and dynamically shows or hides platforms`() {
        val sharedPrefs = "persist_social_test_${System.nanoTime()}"
        val repo1 = SocialLinksRepository(context, prefsName = sharedPrefs)

        val updateResult = repo1.updateLinks(
            mapOf(
                SocialPlatform.INSTAGRAM to "https://www.instagram.com/example_page",
                SocialPlatform.OFFICIAL_WEBSITE to "https://wordandlighttv.example.org"
            )
        )
        assertTrue(updateResult.isSuccess)

        val repo2 = SocialLinksRepository(context, prefsName = sharedPrefs)
        val reloaded = repo2.getConfiguredLinks().map { it.platform }
        assertEquals(
            listOf(
                SocialPlatform.YOUTUBE,
                SocialPlatform.FACEBOOK,
                SocialPlatform.TIKTOK,
                SocialPlatform.INSTAGRAM,
                SocialPlatform.WHATSAPP,
                SocialPlatform.OFFICIAL_WEBSITE
            ),
            reloaded
        )

        // Clearing Instagram hides it again while keeping verified channels intact
        assertTrue(repo2.updateLink(SocialPlatform.INSTAGRAM, "").isSuccess)
        val afterClear = repo2.getConfiguredLinks().map { it.platform }
        assertEquals(
            listOf(
                SocialPlatform.YOUTUBE,
                SocialPlatform.FACEBOOK,
                SocialPlatform.TIKTOK,
                SocialPlatform.WHATSAPP,
                SocialPlatform.OFFICIAL_WEBSITE
            ),
            afterClear
        )
    }

    @Test
    fun `home screen displays Connect With Us shortcut and navigates on click`() {
        var navigatedToConnect = false

        composeRule.setContent {
            WordAndLightTheme {
                HomeScreen(
                    onNavigateToAudio = {},
                    onNavigateToVideo = {},
                    onNavigateToCreator = {},
                    repositoryOverride = musicRepository,
                    onNavigateToConnectWithUs = { navigatedToConnect = true },
                    socialLinksRepositoryOverride = socialRepository
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_connect_with_us_card"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_connect_with_us_card")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertTrue(navigatedToConnect)
    }

    @Test
    fun `settings screen navigates to Connect With Us and saves admin social link updates`() {
        var navigatedToConnect = false

        composeRule.setContent {
            WordAndLightTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    onNavigateToEditProfile = {},
                    repositoryOverride = musicRepository,
                    onNavigateToConnectWithUs = { navigatedToConnect = true },
                    socialLinksRepositoryOverride = socialRepository
                )
            }
        }
        composeRule.waitForIdle()

        // Shortcut navigates to Connect With Us
        composeRule.onNodeWithTag("settings_connect_with_us_item")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(navigatedToConnect)

        // Admin settings modal allows configuring links for all 8 platforms
        composeRule.onNodeWithTag("settings_manage_social_links_item")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("social_admin_config_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("social_admin_input_instagram")
            .performScrollTo()
            .performTextReplacement("https://www.instagram.com/example.ministry")
        composeRule.onNodeWithTag("social_admin_input_official_website")
            .performScrollTo()
            .performTextInput("https://wordandlighttv.example.org")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("social_admin_save_button").performClick()
        composeRule.waitForIdle()

        val configured = socialRepository.getConfiguredLinks()
        assertFalse(configured.any { it.platform == SocialPlatform.TELEGRAM })
        assertEquals(
            listOf(
                SocialPlatform.YOUTUBE,
                SocialPlatform.FACEBOOK,
                SocialPlatform.TIKTOK,
                SocialPlatform.INSTAGRAM,
                SocialPlatform.WHATSAPP,
                SocialPlatform.OFFICIAL_WEBSITE
            ),
            configured.map { it.platform }
        )
    }

    @Test
    fun `whatsapp is never hidden even with stale empty prefs or blank update`() {
        val stalePrefsName = "stale_whatsapp_prefs_${System.nanoTime()}"
        context.getSharedPreferences(stalePrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString("social_url_youtube", VERIFIED_YOUTUBE_CHANNEL_URL)
            .putString("social_url_facebook", "")
            .putString("social_url_tiktok", "")
            .putString("social_url_whatsapp", "")
            .apply()

        val migratedRepo = SocialLinksRepository(context, prefsName = stalePrefsName)
        assertEquals(VERIFIED_WHATSAPP_URL, migratedRepo.getLink(SocialPlatform.WHATSAPP))
        assertEquals(VERIFIED_FACEBOOK_SHARE_URL, migratedRepo.getLink(SocialPlatform.FACEBOOK))
        assertEquals(VERIFIED_TIKTOK_PROFILE_URL, migratedRepo.getLink(SocialPlatform.TIKTOK))
        assertTrue(migratedRepo.getConfiguredLinks().any { it.platform == SocialPlatform.WHATSAPP })

        // Even if someone attempts to clear WhatsApp explicitly, it remains visible with verified URL
        val clearResult = migratedRepo.updateLink(SocialPlatform.WHATSAPP, "")
        assertTrue(clearResult.isSuccess)
        assertEquals(VERIFIED_WHATSAPP_URL, migratedRepo.getLink(SocialPlatform.WHATSAPP))
        assertTrue(
            migratedRepo.getConfiguredLinks().any {
                it.platform == SocialPlatform.WHATSAPP && it.url == VERIFIED_WHATSAPP_URL
            }
        )
    }
}
