package com.aistudio.aimusic.wltv.jqr8x.ui.publish

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.ConnectedChannelSession
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.EncryptedSessionStore
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishPlatform
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishStatusStore
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingEnvironmentConfig
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingRepository
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.YouTubeResumableUploader
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.YouTubeUploadUiState
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinksRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.profile.SettingsScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DirectPublishingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var sessionStore: EncryptedSessionStore
    private lateinit var statusStore: PublishStatusStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SocialLinksRepository.resetSharedInstanceForTests()
        sessionStore = EncryptedSessionStore(
            context = context,
            prefsName = "test_publish_ui_${System.nanoTime()}"
        )
        statusStore = PublishStatusStore(
            context = context,
            prefsName = "test_publish_status_ui_${System.nanoTime()}"
        )
    }

    @Test
    fun `ConnectedChannelsScreen renders AuthorizationClient for YouTube and setup instructions for unconfigured Meta and TikTok`() {
        val repo = PublishingRepository(
            context = context,
            envConfig = PublishingEnvironmentConfig(),
            sessionStore = sessionStore,
            statusStore = statusStore
        )

        composeRule.setContent {
            WordAndLightTheme {
                ConnectedChannelsScreen(
                    onNavigateBack = {},
                    publishingRepositoryOverride = repo
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("publishing_security_banner").assertIsDisplayed()

        // YouTube uses Android OAuth + Google AuthorizationClient without needing Web client ID in BuildConfig
        composeRule.onNodeWithTag("connected_channels_list")
            .performScrollToNode(hasTestTag("publish_connect_youtube"))
        composeRule.onNodeWithTag("publish_connect_youtube").assertIsDisplayed()
        composeRule.onNodeWithTag("youtube_scope_notice").assertIsDisplayed()

        // Meta & TikTok remain gated on Worker broker + App ID configuration
        composeRule.onNodeWithTag("connected_channels_list")
            .performScrollToNode(hasTestTag("publish_setup_toggle_facebook"))
        composeRule.onNodeWithTag("publish_setup_toggle_facebook").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("connected_channels_list")
            .performScrollToNode(hasTestTag("publish_setup_details_facebook"))
        composeRule.onNodeWithTag("publish_setup_details_facebook").assertIsDisplayed()

        composeRule.onNodeWithTag("connected_channels_list")
            .performScrollToNode(hasTestTag("publish_setup_toggle_tiktok"))
        composeRule.onNodeWithTag("publish_setup_toggle_tiktok").assertIsDisplayed()

        // Never render password or client secret inputs
        assertTrue(composeRule.onAllNodesWithText("Password").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Client Secret").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `ConnectedChannelsScreen shows Authorize CTA when OAuth config is present and Disconnect when linked`() {
        sessionStore.saveSession(
            ConnectedChannelSession(
                platform = PublishPlatform.YOUTUBE,
                accessToken = "yt-session-token",
                channelDisplayName = "Word & Light TV",
                channelHandle = "@WordAndLightTV-v6c"
            )
        )
        val repo = PublishingRepository(
            context = context,
            envConfig = PublishingEnvironmentConfig(
                metaAppId = "pub-meta-app",
                tiktokClientKey = "pub-tt-key",
                socialBrokerBaseUrl = "https://broker.example.workers.dev"
            ),
            sessionStore = sessionStore,
            statusStore = statusStore
        )

        var openedYouTubeUploader = false
        composeRule.setContent {
            WordAndLightTheme {
                ConnectedChannelsScreen(
                    onNavigateBack = {},
                    publishingRepositoryOverride = repo,
                    onOpenYouTubePublisher = { openedYouTubeUploader = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("connected_channels_list")
            .performScrollToNode(hasTestTag("publish_open_youtube_uploader"))
        composeRule.onNodeWithTag("publish_open_youtube_uploader").assertIsDisplayed()
        composeRule.onNodeWithTag("publish_open_youtube_uploader").performClick()
        assertTrue(openedYouTubeUploader)

        composeRule.onNodeWithTag("connected_channels_list")
            .performScrollToNode(hasTestTag("publish_disconnect_youtube"))
        composeRule.onNodeWithTag("publish_disconnect_youtube").assertIsDisplayed()

        composeRule.onNodeWithTag("connected_channels_list")
            .performScrollToNode(hasTestTag("publish_connect_facebook"))
        composeRule.onNodeWithTag("publish_connect_facebook").assertIsDisplayed()

        composeRule.onNodeWithTag("connected_channels_list")
            .performScrollToNode(hasTestTag("publish_connect_tiktok"))
        composeRule.onNodeWithTag("publish_connect_tiktok").assertIsDisplayed()

        composeRule.onNodeWithTag("connected_channels_list")
            .performScrollToNode(hasTestTag("publish_disconnect_youtube"))
        composeRule.onNodeWithTag("publish_disconnect_youtube").performClick()
        composeRule.waitForIdle()
        assertNull(sessionStore.getSession(PublishPlatform.YOUTUBE))
    }

    @Test
    fun `DirectPublishDialog supports fallback share when no channel linked and enforces TikTok privacy`() {
        var fallbackClicked = false
        val repoUnlinked = PublishingRepository(
            context = context,
            envConfig = PublishingEnvironmentConfig(),
            sessionStore = sessionStore,
            statusStore = statusStore
        )

        composeRule.setContent {
            WordAndLightTheme {
                DirectPublishDialog(
                    initialTitle = "Sunday Praise MP4",
                    localVideoPath = "/tmp/sample.mp4",
                    codecLabel = "HEVC",
                    onDismiss = {},
                    onOpenConnectedChannels = {},
                    onFallbackShareClick = { fallbackClicked = true },
                    publishingRepositoryOverride = repoUnlinked
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("direct_publish_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("direct_publish_hevc_warning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("direct_publish_fallback_share_button").performClick()
        assertTrue(fallbackClicked)
    }

    @Test
    fun `SettingsScreen routes to ConnectedChannels and opens in-app Privacy Policy and Terms without placeholder URL`() {
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "publish-settings-test" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.aimusic.wltv.jqr8x")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build(),
                "publish-settings-test"
            ).also { initialized ->
                val db = FirebaseFirestore.getInstance(
                    initialized,
                    "ai-studio-android-wordligh-6c38febb-2572-4fb1-840d-e10fc09376b8"
                )
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        val firestore = FirebaseFirestore.getInstance(
            app,
            "ai-studio-android-wordligh-6c38febb-2572-4fb1-840d-e10fc09376b8"
        )
        val musicRepo = MusicRepository(firestore, FirebaseAuth.getInstance(app))
        val socialRepo = SocialLinksRepository(context, "test_social_${System.nanoTime()}")
        val pubRepo = PublishingRepository(
            context = context,
            envConfig = PublishingEnvironmentConfig(),
            sessionStore = sessionStore,
            statusStore = statusStore
        )

        var navigatedToPublishing = false
        composeRule.setContent {
            WordAndLightTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    onNavigateToEditProfile = {},
                    repositoryOverride = musicRepo,
                    socialLinksRepositoryOverride = socialRepo,
                    onNavigateToConnectedChannels = { navigatedToPublishing = true },
                    publishingRepositoryOverride = pubRepo
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_direct_publishing_item").performScrollTo().performClick()
        assertTrue(navigatedToPublishing)

        // Open Privacy Policy dialog locally when unconfigured (no broken placeholder URL)
        assertTrue(composeRule.onAllNodesWithText("wordandlight.app", substring = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("settings_privacy_policy_item").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_info_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("youtube.upload", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        // Open Terms of Service dialog locally
        composeRule.onNodeWithTag("settings_terms_of_service_item").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_info_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("SELF_ONLY", substring = true).assertIsDisplayed()
    }

    @Test
    fun `YouTubePublishScreen renders pickers and disables Upload button when no video is picked`() {
        sessionStore.saveSession(
            ConnectedChannelSession(
                platform = PublishPlatform.YOUTUBE,
                accessToken = "yt-ui-token",
                channelDisplayName = "Word & Light TV"
            )
        )
        val repo = PublishingRepository(
            context = context,
            envConfig = PublishingEnvironmentConfig(),
            sessionStore = sessionStore,
            statusStore = statusStore
        )

        composeRule.setContent {
            WordAndLightTheme {
                YouTubeUploadScreen(
                    onNavigateBack = {},
                    onOpenConnectedChannels = {},
                    publishingRepositoryOverride = repo,
                    initialVideoUriForTest = ""
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("youtube_publish_scroll_list")
            .performScrollToNode(hasTestTag("youtube_pick_video_button"))
        composeRule.onNodeWithTag("youtube_pick_video_button").assertIsDisplayed()
        composeRule.onNodeWithTag("youtube_pick_gallery_video_button").assertIsDisplayed()
        composeRule.onNodeWithTag("youtube_publish_scroll_list")
            .performScrollToNode(hasTestTag("youtube_pick_thumbnail_button"))
        composeRule.onNodeWithTag("youtube_pick_thumbnail_button").assertIsDisplayed()
        composeRule.onNodeWithTag("youtube_publish_scroll_list")
            .performScrollToNode(hasTestTag("youtube_upload_submit_button"))
        composeRule.onNodeWithTag("youtube_upload_submit_button").assertIsNotEnabled()
    }

    @Test
    fun `YouTubePublishScreen uploads selected video with metadata and privacy and shows YouTube link`() {
        val sampleVideo = File.createTempFile("ui_yt_vid_", ".mp4", context.cacheDir).apply {
            writeBytes(ByteArray(320 * 1024) { 0x11 })
        }
        var baseUrl = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/yt/init") { ex ->
                ex.requestBody.readBytes()
                ex.responseHeaders.add("Location", "$baseUrl/yt/upload")
                ex.sendResponseHeaders(200, -1)
                ex.close()
            }
            createContext("/yt/upload") { ex ->
                ex.requestBody.readBytes()
                val body = JSONObject().put("id", "yt_ui_901").toString().toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
                ex.close()
            }
            start()
        }
        baseUrl = "http://127.0.0.1:${server.address.port}"

        try {
            sessionStore.saveSession(
                ConnectedChannelSession(
                    platform = PublishPlatform.YOUTUBE,
                    accessToken = "yt-ui-token",
                    channelDisplayName = "Word & Light TV"
                )
            )
            val repoSelected = PublishingRepository(
                context = context,
                envConfig = PublishingEnvironmentConfig(),
                sessionStore = sessionStore,
                statusStore = PublishStatusStore(context, "test_pub_ui2_${System.nanoTime()}"),
                youtubeUploader = YouTubeResumableUploader(
                    resumableInitUrl = "$baseUrl/yt/init",
                    chunkSizeBytes = 512 * 1024,
                    appContext = context
                )
            )
            composeRule.setContent {
                WordAndLightTheme {
                    YouTubeUploadScreen(
                        onNavigateBack = {},
                        onOpenConnectedChannels = {},
                        publishingRepositoryOverride = repoSelected,
                        initialVideoUriForTest = sampleVideo.absolutePath
                    )
                }
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("youtube_publish_scroll_list")
                .performScrollToNode(hasTestTag("youtube_title_input"))
            composeRule.onNodeWithTag("youtube_title_input").performTextInput("Psalm 23 Worship Video")
            composeRule.onNodeWithTag("youtube_description_input").performTextInput("Official gospel broadcast")
            composeRule.onNodeWithTag("youtube_publish_scroll_list")
                .performScrollToNode(hasTestTag("youtube_privacy_dropdown"))
            composeRule.onNodeWithTag("youtube_privacy_dropdown").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("youtube_publish_scroll_list")
                .performScrollToNode(hasTestTag("youtube_privacy_unlisted"))
            composeRule.onNodeWithTag("youtube_privacy_unlisted").performClick()

            composeRule.onNodeWithTag("youtube_publish_scroll_list")
                .performScrollToNode(hasTestTag("youtube_upload_submit_button"))
            composeRule.onNodeWithTag("youtube_upload_submit_button").assertIsEnabled()
            composeRule.onNodeWithTag("youtube_upload_submit_button").performClick()

            composeRule.waitUntil(timeoutMillis = 8_000L) {
                repoSelected.youTubeUploadState.value is YouTubeUploadUiState.Success
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("youtube_publish_scroll_list")
                .performScrollToNode(hasTestTag("youtube_upload_success_card"))
            composeRule.onNodeWithTag("youtube_upload_success_card").assertIsDisplayed()
            composeRule.onNodeWithText("https://youtu.be/yt_ui_901").assertIsDisplayed()
            composeRule.onNodeWithTag("youtube_watch_uploaded_button").assertIsDisplayed()
            composeRule.onNodeWithTag("youtube_copy_link_button").assertIsDisplayed()
        } finally {
            server.stop(0)
            sampleVideo.delete()
        }
    }
}
