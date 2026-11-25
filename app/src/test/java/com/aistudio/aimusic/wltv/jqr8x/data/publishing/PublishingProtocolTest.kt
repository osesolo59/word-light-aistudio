package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PublishingProtocolTest {

    private lateinit var context: Context
    private lateinit var prefsName: String
    private lateinit var sessionStore: EncryptedSessionStore
    private lateinit var tempVideoFile: File
    private var server: HttpServer? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefsName = "test_oauth_sessions_${System.nanoTime()}"
        sessionStore = EncryptedSessionStore(
            context = context,
            prefsName = prefsName
        )
        tempVideoFile = File.createTempFile("wltv_test_video_", ".mp4", context.cacheDir).apply {
            // Write 600 KB payload so chunked uploads span multiple chunks
            writeBytes(ByteArray(600 * 1024) { (it % 127).toByte() })
        }
    }

    @After
    fun tearDown() {
        server?.stop(0)
        server = null
        tempVideoFile.delete()
    }

    @Test
    fun `Google YouTube AuthorizationClient uses only youtube upload scope and custom-scheme YouTube OAuth is disabled`() {
        val ytAuthReq = GoogleYouTubeAuthorizationManager.buildUploadAuthorizationRequest()
        val scopeUris = ytAuthReq.requestedScopes.map { it.scopeUri }
        assertEquals(listOf(GoogleYouTubeAuthorizationManager.YOUTUBE_UPLOAD_SCOPE), scopeUris)
        assertFalse(scopeUris.any { it.contains("youtube.readonly") })

        val config = PublishingEnvironmentConfig(
            metaAppId = "pub-meta-app-id",
            tiktokClientKey = "pub-tt-client-key",
            socialBrokerBaseUrl = "https://broker.example.workers.dev"
        )
        val disallowedYt = OAuthPkceManager.buildAuthorizationRequest(PublishPlatform.YOUTUBE, config)
        assertTrue(disallowedYt.isFailure)
        assertTrue(disallowedYt.exceptionOrNull() is UnsupportedOperationException)

        val verifier = OAuthPkceManager.generateCodeVerifier()
        assertTrue(verifier.length >= 43)
        assertFalse(verifier.contains("="))
        val expectedDigest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val expectedChallenge = Base64.encodeToString(
            expectedDigest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        assertEquals(expectedChallenge, OAuthPkceManager.computeCodeChallenge(verifier))

        val fbReq = OAuthPkceManager.buildAuthorizationRequest(PublishPlatform.FACEBOOK, config).getOrThrow()
        assertTrue(fbReq.authorizationUri.getQueryParameter("redirect_uri")!!.endsWith("/oauth/meta/callback"))
        val fbScopes = fbReq.authorizationUri.getQueryParameter("scope")!!
        assertTrue(fbScopes.contains("pages_manage_posts"))
        assertFalse(fbScopes.contains("publish_video"))

        val ttReq = OAuthPkceManager.buildAuthorizationRequest(PublishPlatform.TIKTOK, config).getOrThrow()
        assertTrue(ttReq.authorizationUri.getQueryParameter("redirect_uri")!!.endsWith("/oauth/tiktok/callback"))
        val ttScopes = ttReq.authorizationUri.getQueryParameter("scope")!!
        assertEquals("user.info.basic,video.publish", ttScopes)
        assertFalse(ttScopes.contains("video.upload"))
    }

    @Test
    fun `redirect URI parsing extracts Meta and TikTok callbacks and rejects YouTube custom scheme`() {
        val tikTokUri = Uri.parse("com.aistudio.aimusic.wltv.jqr8x://oauth2redirect/tiktok?code=abc123&state=xyz789")
        assertTrue(OAuthPkceManager.isOAuthRedirectUri(tikTokUri))
        val payload = OAuthPkceManager.parseRedirectUri(tikTokUri)
        assertEquals(PublishPlatform.TIKTOK, payload.platform)
        assertEquals("abc123", payload.code)
        assertEquals("xyz789", payload.state)
        assertNull(payload.error)

        val fbUri = Uri.parse("com.aistudio.aimusic.wltv.jqr8x://oauth2redirect/facebook?code=fbcode&state=fbstate")
        assertTrue(OAuthPkceManager.isOAuthRedirectUri(fbUri))
        assertEquals(PublishPlatform.FACEBOOK, OAuthPkceManager.parseRedirectUri(fbUri).platform)

        val ytLegacyUri = Uri.parse("com.aistudio.aimusic.wltv.jqr8x://oauth2redirect/youtube?code=yt&state=st")
        assertFalse(OAuthPkceManager.isOAuthRedirectUri(ytLegacyUri))
        assertNull(OAuthPkceManager.parseRedirectUri(ytLegacyUri).platform)
    }

    @Test
    fun `session store fails closed without Keystore on real devices and isolates expiring per-platform PKCE states`() {
        val unavailableStore = EncryptedSessionStore(
            context = context,
            prefsName = "unavailable_${System.nanoTime()}",
            keystoreAvailabilityCheck = { false },
            allowRobolectricTestCipher = false
        )
        assertFalse(unavailableStore.isSecureStorageAvailable())
        var failedClosed = false
        try {
            unavailableStore.saveSession(
                ConnectedChannelSession(
                    platform = PublishPlatform.YOUTUBE,
                    accessToken = "secret-yt-token"
                )
            )
        } catch (_: SecurityException) {
            failedClosed = true
        }
        assertTrue("Expected SecurityException when Keystore is unavailable", failedClosed)

        val nowMs = 1_000_000L
        sessionStore.savePendingOAuthState(
            PendingOAuthState(PublishPlatform.FACEBOOK, "fb-state", "fb-verifier", createdAtEpochMs = nowMs),
            nowMs = nowMs
        )
        sessionStore.savePendingOAuthState(
            PendingOAuthState(PublishPlatform.TIKTOK, "tt-state", "tt-verifier", createdAtEpochMs = nowMs),
            nowMs = nowMs
        )
        assertEquals(setOf(PublishPlatform.FACEBOOK, PublishPlatform.TIKTOK), sessionStore.activePendingPlatforms(nowMs))

        // Mismatched state aborts without clobbering active state
        assertNull(sessionStore.consumePendingOAuthState(PublishPlatform.FACEBOOK, "wrong-state", nowMs))
        assertNotNull(sessionStore.consumePendingOAuthState(PublishPlatform.FACEBOOK, "fb-state", nowMs))
        assertNull(sessionStore.consumePendingOAuthState(PublishPlatform.FACEBOOK, "fb-state", nowMs))

        // Expired state (> 10 minutes) is rejected
        val expiredNow = nowMs + PendingOAuthState.PENDING_OAUTH_TTL_MS + 1_000L
        assertNull(sessionStore.consumePendingOAuthState(PublishPlatform.TIKTOK, "tt-state", expiredNow))

        // Stored sessions use AES-GCM ciphertext, never plaintext tokens
        val session = ConnectedChannelSession(
            platform = PublishPlatform.FACEBOOK,
            accessToken = "page-token-abc",
            accountId = "user-1",
            channelDisplayName = "Pastor Osei",
            targetPageId = "100200300",
            targetPageName = "Word & Light TV",
            availablePages = listOf(
                FacebookPageTarget("100200300", "Word & Light TV", "Ministry", "page-token-abc"),
                FacebookPageTarget("999888777", "Other Page", "Community", "other-token")
            )
        )
        sessionStore.saveSession(session)
        val rawPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString("oauth_session_facebook", "").orEmpty()
        assertFalse(rawPrefs.contains("page-token-abc"))
        assertFalse(rawPrefs.contains("Word & Light TV"))

        val loaded = sessionStore.getSession(PublishPlatform.FACEBOOK)
        assertNotNull(loaded)
        assertEquals("100200300", loaded?.targetPageId)

        val switched = sessionStore.updateFacebookTargetPage(loaded!!.availablePages[1])
        assertEquals("999888777", switched?.targetPageId)

        sessionStore.clearSession(PublishPlatform.FACEBOOK)
        assertNull(sessionStore.getSession(PublishPlatform.FACEBOOK))
    }

    @Test
    fun `TokenBrokerClient rejects missing Meta Page credentials and retries 401 with refreshed Firebase token`() = runTest {
        val exchangeCalls = AtomicInteger(0)
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/oauth/meta/exchange") { ex ->
                val attempt = exchangeCalls.incrementAndGet()
                val authHeader = ex.requestHeaders.getFirst("Authorization")
                ex.requestBody.readBytes()
                if (attempt == 1 && authHeader == "Bearer stale-fb-jwt") {
                    val err = JSONObject().put("error", "Expired Firebase JWT").toString().toByteArray()
                    ex.sendResponseHeaders(401, err.size.toLong())
                    ex.responseBody.use { it.write(err) }
                } else {
                    // Response omits any valid Facebook Page access token -> client must reject
                    val resp = JSONObject()
                        .put("userId", "u1")
                        .put("userName", "Pastor Osei")
                        .put("pages", JSONArray())
                        .toString().toByteArray()
                    ex.sendResponseHeaders(200, resp.size.toLong())
                    ex.responseBody.use { it.write(resp) }
                }
                ex.close()
            }
            start()
        }
        server = httpServer
        val testHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val localUrl = original.url.newBuilder()
                    .scheme("http")
                    .host("127.0.0.1")
                    .port(httpServer.address.port)
                    .build()
                chain.proceed(original.newBuilder().url(localUrl).build())
            }
            .build()

        val client = TokenBrokerClient(
            httpClient = testHttpClient,
            firebaseIdTokenProvider = { forceRefresh ->
                if (forceRefresh) "fresh-fb-jwt" else "stale-fb-jwt"
            }
        )
        val result = client.exchangeAuthorizationCode(
            platform = PublishPlatform.FACEBOOK,
            code = "auth-code",
            codeVerifier = "verifier",
            config = PublishingEnvironmentConfig(socialBrokerBaseUrl = "https://broker.example.workers.dev")
        )
        assertEquals(2, exchangeCalls.get())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Facebook Page"))
    }

    @Test
    fun `YouTubeResumableUploader executes two-step resumable chunked PUT upload`() = runTest {
        val chunkRequests = AtomicInteger(0)
        var baseUrl = ""
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/yt/init") { ex ->
                val body = ex.requestBody.bufferedReader().readText()
                assertTrue(body.contains("Word & Light Worship"))
                ex.responseHeaders.add("Location", "$baseUrl/yt/upload")
                ex.sendResponseHeaders(200, -1)
                ex.close()
            }
            createContext("/yt/upload") { ex ->
                val contentRange = ex.requestHeaders.getFirst("Content-Range")
                assertNotNull(contentRange)
                assertTrue(contentRange!!.startsWith("bytes "))
                ex.requestBody.readBytes()
                val callNum = chunkRequests.incrementAndGet()
                if (callNum < 3) {
                    ex.sendResponseHeaders(308, -1)
                } else {
                    val resp = JSONObject().put("id", "yt_wltv_777").toString().toByteArray()
                    ex.sendResponseHeaders(200, resp.size.toLong())
                    ex.responseBody.use { it.write(resp) }
                }
                ex.close()
            }
            start()
        }
        server = httpServer
        baseUrl = "http://127.0.0.1:${httpServer.address.port}"

        val uploader = YouTubeResumableUploader(
            resumableInitUrl = "$baseUrl/yt/init",
            chunkSizeBytes = 256 * 1024
        )
        val result = uploader.uploadVideo(
            accessToken = "test-yt-token",
            metadata = PublishVideoMetadata(
                localVideoPath = tempVideoFile.absolutePath,
                title = "Word & Light Worship",
                description = "Praise session",
                privacy = PublishPrivacy.PRIVATE
            ),
            onProgress = { _, _ -> }
        ).getOrThrow()

        assertEquals("yt_wltv_777", result.remoteId)
        assertEquals("https://youtu.be/yt_wltv_777", result.remoteUrl)
        assertEquals(3, chunkRequests.get())
    }

    @Test
    fun `MetaPageVideoUploader requires numeric Page ID and executes 3-phase Graph API upload`() = runTest {
        val uploaderForValidation = MetaPageVideoUploader(graphVideoBaseUrl = "http://127.0.0.1:1/v21.0")
        val badIdResult = uploaderForValidation.uploadToPage(
            pageId = "me",
            pageAccessToken = "token",
            metadata = PublishVideoMetadata(tempVideoFile.absolutePath, "Title", "Desc"),
            onProgress = { _, _ -> }
        )
        assertTrue(badIdResult.isFailure)

        val totalLen = tempVideoFile.length()
        val phasesSeen = mutableListOf<String>()
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v21.0/112233/videos") { ex ->
                val body = String(ex.requestBody.readBytes(), Charsets.UTF_8)
                val responseJson = when {
                    body.contains("start") && !body.contains("transfer") && !body.contains("finish") -> {
                        phasesSeen.add("start")
                        JSONObject()
                            .put("upload_session_id", "fb_sess_1")
                            .put("video_id", "fb_vid_456")
                            .put("start_offset", "0")
                            .put("end_offset", totalLen.toString())
                    }
                    body.contains("transfer") -> {
                        phasesSeen.add("transfer")
                        JSONObject()
                            .put("start_offset", totalLen.toString())
                            .put("end_offset", totalLen.toString())
                    }
                    else -> {
                        phasesSeen.add("finish")
                        JSONObject().put("success", true)
                    }
                }.toString().toByteArray()
                ex.sendResponseHeaders(200, responseJson.size.toLong())
                ex.responseBody.use { it.write(responseJson) }
                ex.close()
            }
            start()
        }
        server = httpServer
        val baseUrl = "http://127.0.0.1:${httpServer.address.port}/v21.0"

        val uploader = MetaPageVideoUploader(graphVideoBaseUrl = baseUrl)
        val result = uploader.uploadToPage(
            pageId = "112233",
            pageAccessToken = "test-page-token",
            metadata = PublishVideoMetadata(
                localVideoPath = tempVideoFile.absolutePath,
                title = "Word & Light Live",
                description = "Ministry video",
                privacy = PublishPrivacy.PUBLIC
            ),
            onProgress = { _, _ -> }
        ).getOrThrow()

        assertEquals("fb_vid_456", result.remoteId)
        assertEquals("https://www.facebook.com/watch/?v=fb_vid_456", result.remoteUrl)
        assertEquals(listOf("start", "transfer", "finish"), phasesSeen)
    }

    @Test
    fun `TikTokDirectPostUploader executes creator_info preflight and enforces SELF_ONLY sandbox restriction`() = runTest {
        var baseUrl = ""
        val callsOrder = mutableListOf<String>()
        var initRequestBody = ""
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v2/post/publish/creator_info/query/") { ex ->
                callsOrder.add("creator_info")
                ex.requestBody.readBytes()
                val resp = JSONObject()
                    .put(
                        "data",
                        JSONObject()
                            .put("creator_nickname", "Ministry Account")
                            .put("creator_username", "wltv_creator")
                            .put("privacy_level_options", JSONArray().put("SELF_ONLY"))
                            .put("comment_disabled", true)
                            .put("duet_disabled", false)
                            .put("stitch_disabled", false)
                            .put("max_video_post_duration_sec", 600)
                    )
                    .put("error", JSONObject().put("code", "ok"))
                    .toString().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
                ex.close()
            }
            createContext("/v2/post/publish/video/init/") { ex ->
                callsOrder.add("init")
                initRequestBody = ex.requestBody.bufferedReader().readText()
                assertTrue(initRequestBody.contains("FILE_UPLOAD"))
                val resp = JSONObject()
                    .put(
                        "data",
                        JSONObject()
                            .put("publish_id", "tt_pub_999")
                            .put("upload_url", "$baseUrl/tt/upload")
                    )
                    .put("error", JSONObject().put("code", "ok"))
                    .toString().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
                ex.close()
            }
            createContext("/tt/upload") { ex ->
                callsOrder.add("put")
                val contentRange = ex.requestHeaders.getFirst("Content-Range")
                assertNotNull(contentRange)
                assertNotEquals("", contentRange)
                ex.requestBody.readBytes()
                ex.sendResponseHeaders(201, -1)
                ex.close()
            }
            createContext("/v2/post/publish/status/fetch/") { ex ->
                callsOrder.add("status")
                ex.requestBody.readBytes()
                val resp = JSONObject()
                    .put("data", JSONObject().put("status", "PUBLISH_COMPLETE"))
                    .toString().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
                ex.close()
            }
            start()
        }
        server = httpServer
        baseUrl = "http://127.0.0.1:${httpServer.address.port}"

        val uploader = TikTokDirectPostUploader(
            creatorInfoUrl = "$baseUrl/v2/post/publish/creator_info/query/",
            initUrl = "$baseUrl/v2/post/publish/video/init/",
            statusFetchUrl = "$baseUrl/v2/post/publish/status/fetch/",
            pollDelayMs = 10L
        )
        // Request PUBLIC privacy, but creator_info preflight only permits SELF_ONLY -> must enforce SELF_ONLY
        val result = uploader.uploadVideo(
            accessToken = "test-tt-token",
            metadata = PublishVideoMetadata(
                localVideoPath = tempVideoFile.absolutePath,
                title = "Gospel Short Clip",
                description = "Psalm 119:105",
                privacy = PublishPrivacy.PUBLIC,
                allowComments = true
            ),
            onProgress = { _, _ -> }
        ).getOrThrow()

        assertEquals(listOf("creator_info", "init", "put", "status"), callsOrder)
        assertTrue(initRequestBody.contains("\"privacy_level\":\"SELF_ONLY\""))
        assertTrue(initRequestBody.contains("\"disable_comment\":true"))
        assertEquals("tt_pub_999", result.remoteId)
        assertEquals("https://www.tiktok.com/@wltv_creator", result.remoteUrl)
        assertFalse(result.remoteUrl.contains("oseisolomon7"))
        assertTrue(result.visibilityNote.contains("SELF_ONLY"))
    }

    @Test
    fun `PublishStatusStore persists per-platform outcomes across instances and classifies transient errors`() {
        val statusPrefs = "test_publish_status_${System.nanoTime()}"
        val writer = PublishStatusStore(context, statusPrefs)
        writer.saveStatus(
            PublishPlatform.YOUTUBE,
            PlatformPublishStatus.Published("yt_1", "https://youtu.be/yt_1", "Private")
        )
        writer.saveStatus(
            PublishPlatform.FACEBOOK,
            PlatformPublishStatus.Failed("HTTP 503 Service Unavailable", requiresReauth = false)
        )

        val reader = PublishStatusStore(context, statusPrefs)
        val statuses = reader.getAllStatuses()
        assertTrue(statuses[PublishPlatform.YOUTUBE] is PlatformPublishStatus.Published)
        assertEquals("yt_1", (statuses[PublishPlatform.YOUTUBE] as PlatformPublishStatus.Published).remoteId)
        assertTrue(statuses[PublishPlatform.FACEBOOK] is PlatformPublishStatus.Failed)
        assertEquals(PlatformPublishStatus.Idle, statuses[PublishPlatform.TIKTOK])

        assertTrue(VideoPublishWorker.isTransientErrorMessage("HTTP 503 Gateway Timeout"))
        assertTrue(VideoPublishWorker.isTransientErrorMessage("Socket timed out during upload"))
        assertFalse(VideoPublishWorker.isTransientErrorMessage("Missing or invalid Facebook Page token"))
    }

    @Test
    fun `YouTubeResumableUploader streams content URI with tags and handles non-fatal thumbnail 403`() = runTest {
        val providerAuthority = "com.aistudio.aimusic.wltv.jqr8x.testvideo"
        val info = ProviderInfo().apply { authority = providerAuthority }
        TestVideoContentProvider.backingFile = tempVideoFile
        Robolectric.buildContentProvider(TestVideoContentProvider::class.java)
            .create(info)

        val thumbFile = File.createTempFile("wltv_thumb_", ".jpg", context.cacheDir).apply {
            writeBytes(ByteArray(2048) { 0x55 })
        }

        var capturedInitJson = ""
        var thumbnailRequested = false
        var baseUrl = ""
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/yt/init") { ex ->
                capturedInitJson = ex.requestBody.bufferedReader().readText()
                ex.responseHeaders.add("Location", "$baseUrl/yt/upload")
                ex.sendResponseHeaders(200, -1)
                ex.close()
            }
            createContext("/yt/upload") { ex ->
                ex.requestBody.readBytes()
                val resp = JSONObject().put("id", "yt_uri_vid_888").toString().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
                ex.close()
            }
            createContext("/yt/thumb") { ex ->
                thumbnailRequested = true
                ex.requestBody.readBytes()
                val err = JSONObject()
                    .put("error", JSONObject().put("message", "Channel not eligible for custom thumbnails"))
                    .toString().toByteArray()
                ex.sendResponseHeaders(403, err.size.toLong())
                ex.responseBody.use { it.write(err) }
                ex.close()
            }
            start()
        }
        server = httpServer
        baseUrl = "http://127.0.0.1:${httpServer.address.port}"

        try {
            val uploader = YouTubeResumableUploader(
                resumableInitUrl = "$baseUrl/yt/init",
                thumbnailsSetUrl = "$baseUrl/yt/thumb",
                chunkSizeBytes = 1024 * 1024,
                appContext = context
            )
            val contentUri = "content://$providerAuthority/video/gospel.mp4"
            val result = uploader.uploadVideo(
                accessToken = "test-yt-token",
                metadata = PublishVideoMetadata(
                    localVideoPath = contentUri,
                    title = "Grace Praise Song",
                    description = "Recorded on phone",
                    hashtags = "#GospelMusic, Praise, #GospelMusic",
                    privacy = PublishPrivacy.UNLISTED,
                    localThumbnailPath = thumbFile.absolutePath
                ),
                onProgress = { _, _ -> }
            ).getOrThrow()

            assertEquals("yt_uri_vid_888", result.remoteId)
            assertEquals("https://youtu.be/yt_uri_vid_888", result.remoteUrl)
            assertTrue(thumbnailRequested)
            assertTrue(result.visibilityNote.contains("Custom thumbnail not applied"))

            val parsedInit = JSONObject(capturedInitJson)
            assertEquals("unlisted", parsedInit.getJSONObject("status").getString("privacyStatus"))
            val tags = parsedInit.getJSONObject("snippet").getJSONArray("tags")
            assertEquals(2, tags.length())
            assertEquals("GospelMusic", tags.getString(0))
            assertEquals("Praise", tags.getString(1))
        } finally {
            thumbFile.delete()
        }
    }

    @Test
    fun `PublishingRepository refreshes expired YouTube token on 401 and supports upload cancellation`() {
        val initAttempts = AtomicInteger(0)
        val firstChunkStarted = CompletableDeferred<Unit>()
        val releaseSlowChunk = CountDownLatch(1)
        var baseUrl = ""

        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/yt/init") { ex ->
                val attempt = initAttempts.incrementAndGet()
                val auth = ex.requestHeaders.getFirst("Authorization")
                ex.requestBody.readBytes()
                if (attempt == 1 && auth == "Bearer expired-yt-token") {
                    val err = JSONObject().put("error", JSONObject().put("message", "Invalid Credentials")).toString().toByteArray()
                    ex.sendResponseHeaders(401, err.size.toLong())
                    ex.responseBody.use { it.write(err) }
                } else {
                    ex.responseHeaders.add("Location", "$baseUrl/yt/upload")
                    ex.sendResponseHeaders(200, -1)
                }
                ex.close()
            }
            createContext("/yt/upload") { ex ->
                ex.requestBody.readBytes()
                if (initAttempts.get() >= 3) {
                    firstChunkStarted.complete(Unit)
                    releaseSlowChunk.await(3, TimeUnit.SECONDS)
                }
                val resp = JSONObject().put("id", "yt_refreshed_123").toString().toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
                ex.close()
            }
            start()
        }
        server = httpServer
        baseUrl = "http://127.0.0.1:${httpServer.address.port}"

        sessionStore.saveSession(
            ConnectedChannelSession(
                platform = PublishPlatform.YOUTUBE,
                accessToken = "expired-yt-token",
                channelDisplayName = "Word & Light TV"
            )
        )
        val repo = PublishingRepository(
            context = context,
            envConfig = PublishingEnvironmentConfig(),
            sessionStore = sessionStore,
            statusStore = PublishStatusStore(context, "test_pub_status_${System.nanoTime()}"),
            youTubeAuthAdapter = object : YouTubeAuthorizationClientAdapter {
                override fun buildUploadAuthorizationRequest() =
                    GoogleYouTubeAuthorizationManager.buildUploadAuthorizationRequest()
                override suspend fun authorize(activity: android.app.Activity) =
                    YouTubeAuthOutcome.Authorized("fresh-yt-token", listOf(GoogleYouTubeAuthorizationManager.YOUTUBE_UPLOAD_SCOPE))
                override fun extractResultFromIntent(context: Context, data: android.content.Intent?) =
                    YouTubeAuthOutcome.Authorized("fresh-yt-token", listOf(GoogleYouTubeAuthorizationManager.YOUTUBE_UPLOAD_SCOPE))
                override suspend fun refreshAccessTokenSilently(context: Context) =
                    Result.success("fresh-yt-token")
            },
            youtubeUploader = YouTubeResumableUploader(
                resumableInitUrl = "$baseUrl/yt/init",
                chunkSizeBytes = 1024 * 1024,
                appContext = context
            )
        )

        val doneLatch = CountDownLatch(1)
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            repo.youTubeUploadState.collect { st ->
                if (st is YouTubeUploadUiState.Success) {
                    doneLatch.countDown()
                }
            }
        }
        repo.startYouTubeDirectUpload(
            PublishVideoMetadata(
                localVideoPath = tempVideoFile.absolutePath,
                title = "Refreshed Token Upload",
                description = "Testing 401 refresh",
                privacy = PublishPrivacy.PRIVATE
            )
        )
        assertTrue("Expected YouTubeUploadUiState.Success after 401 refresh", doneLatch.await(5, TimeUnit.SECONDS))
        job.cancel()
        assertEquals(2, initAttempts.get())
        val successState = repo.youTubeUploadState.value as YouTubeUploadUiState.Success
        assertEquals("https://youtu.be/yt_refreshed_123", successState.videoUrl)

        repo.resetYouTubeUploadState()
        repo.startYouTubeDirectUpload(
            PublishVideoMetadata(
                localVideoPath = tempVideoFile.absolutePath,
                title = "Cancelled Upload",
                description = "Cancel test",
                privacy = PublishPrivacy.PRIVATE
            )
        )
        val startedWait = CountDownLatch(1)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            firstChunkStarted.await()
            startedWait.countDown()
        }
        assertTrue("Upload did not reach chunk stage before cancel", startedWait.await(5, TimeUnit.SECONDS))
        repo.cancelYouTubeUpload()
        releaseSlowChunk.countDown()
        assertTrue(repo.youTubeUploadState.value is YouTubeUploadUiState.Cancelled)
    }

    class TestVideoContentProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val file = backingFile
            val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            val cursor = MatrixCursor(cols)
            val row: Array<Any?> = Array(cols.size) { index ->
                when (cols[index]) {
                    OpenableColumns.DISPLAY_NAME -> "sunday_worship.mp4"
                    OpenableColumns.SIZE -> file?.length() ?: 0L
                    else -> null
                }
            }
            cursor.addRow(row)
            return cursor
        }

        override fun getType(uri: Uri): String = "video/mp4"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            val file = backingFile ?: return null
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        companion object {
            var backingFile: File? = null
        }
    }
}
