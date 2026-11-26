package com.aistudio.aimusic.wltv.jqr8x.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.data.demo.IdeaGeneratorEngine
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.create.LyricsUiState
import com.aistudio.aimusic.wltv.jqr8x.ui.create.LyricsViewModel
import com.aistudio.aimusic.wltv.jqr8x.ui.create.lyricsErrorHeadline
import com.aistudio.aimusic.wltv.jqr8x.ui.ideas.IdeaGeneratorViewModel
import com.aistudio.aimusic.wltv.jqr8x.util.AppCheckStatus
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseAiLyricsAndFallbackTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private class FakeAiTextGenerator : AiTextGenerator {
        override val modelName: String = FreeSparkModels.DEFAULT_FREE_MODEL
        var nextError: Throwable? = null
        var lastLyricsRequest: AiLyricsRequest? = null

        override suspend fun generateLyrics(request: AiLyricsRequest): AiLyricsResult {
            lastLyricsRequest = request
            nextError?.let { throw it }

            val rawJson = when (request.language) {
                "Twi" -> """
                    {
                      "title": "${request.title.ifBlank { "Onyame Adom" }}",
                      "intro": "(Talking Drum ne Piano ahosiesie)",
                      "verse1": "Onyame ne yɛn hann ne yɛn gyefo\nYɛma Wo din so anɔpa yi",
                      "verse2": "Wo adom kɛse gyina hɔ daa\nWɔ yɛn asetena nyinaa mu",
                      "chorus": "Ayeyi ne aseda nka Wo din\nAwurade Yesu yɛ yɛn hene",
                      "bridge": "Yɛpagya yɛn nsa ma Wo\nWɔ anigye ne aseda mu",
                      "outro": "Amen, Hallelujah daa nyinaa"
                    }
                """.trimIndent()
                "Akan" -> """
                    {
                      "title": "${request.title.ifBlank { "Aseda Kɛse" }}",
                      "intro": "(Acoustic Guitar ne Gospel Choir)",
                      "verse1": "Awurade adɔe boro ade nyinaa so\nYɛbɛto aseda nnwom ama Wo",
                      "verse2": "Wo hann hyerɛn wɔ yɛn kwan so\nWoyɛ ogyefo pa daa",
                      "chorus": "Onyame kɛse, gye ayeyi\nWo din kronkron so daa",
                      "bridge": "Asase ne soro yi Wo ayɛ\nWo dɔ to ntwa da",
                      "outro": "Ayeyi nka Onyame, Amen"
                    }
                """.trimIndent()
                "Ga" -> """
                    {
                      "title": "${request.title.ifBlank { "Nyɔŋmɔ La" }}",
                      "intro": "(Organ kɛ Bass gbɛɛmɔ)",
                      "verse1": "Nyɔŋmɔ ji wɔ la kɛ wɔ wala\nWɔjieɔ Oyi yɛ miishɛɛ mli",
                      "verse2": "Oduromɔ haa wɔ hewalɛ daa\nYesu ji wɔ yiwalaherelɔ",
                      "chorus": "Yijiemɔ kɛ shidaa aha Bo, Nuŋtsɔ\nOgbɛi lɛ nɔ kwɔ daa",
                      "bridge": "Wɔwóɔ wɔnine nɔ yɛ shidaa mli\nShidaa fɛɛ ji Onɔ",
                      "outro": "Haleluya, Amen"
                    }
                """.trimIndent()
                "Ewe" -> """
                    {
                      "title": "${request.title.ifBlank { "Mawu Kekeli" }}",
                      "intro": "(Saxophone kple Talking Drum)",
                      "verse1": "Mawu nye míaƒe kekeli kple agbe\nMíekafua Wò ŋkɔ le dzidzɔ me",
                      "verse2": "Wò amenuveve doa ŋusẽ mí gbe sia gbe\nYesu nye míaƒe xɔnametɔ",
                      "chorus": "Kafukafu kple akpedada na Wò, Aƒetɔ\nWò lɔlɔ̃ nɔa anyi ɖaa",
                      "bridge": "Míekɔa míaƒe asiwo dzi le dzidzɔ me\nAkpe na Wò, Mawu",
                      "outro": "Haleluya, Amen"
                    }
                """.trimIndent()
                else -> """
                    {
                      "title": "${request.title.ifBlank { "Walk in Grace" }}",
                      "intro": "(Warm Piano and Choir hum)",
                      "verse1": "Morning breaks with mercy shining bright\nYou guide our steps in steadfast light",
                      "verse2": "Every promise stands through storm and praise\nOur hearts respond in grateful ways",
                      "chorus": "Glory to Your holy name forever\nWord and Light, You fail us never",
                      "bridge": "Lift every voice across the earth\nDeclaring Your unchanging worth",
                      "outro": "Forever praising, Hallelujah, Amen"
                    }
                """.trimIndent()
            }

            return FirebaseAiTextGenerator.parseLyricsJson(
                rawJson = rawJson,
                request = request,
                modelName = modelName
            )
        }

        override suspend fun generateIdeas(
            category: String?,
            genre: String?,
            mood: String?,
            language: String,
            batchSeed: Int
        ): AiIdeasResult {
            nextError?.let { throw it }
            val seeded = IdeaGeneratorEngine.generateIdeas(
                category = category,
                genre = genre,
                mood = mood,
                language = language,
                batchSeed = batchSeed
            ).map { it.copy(id = "ai_${it.id}", isDemo = false) }
            return AiIdeasResult(
                ideas = seeded,
                modelName = modelName,
                isDemo = false
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "ai-lyrics-test-app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.aimusic.wltv.jqr8x")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build(),
                "ai-lyrics-test-app"
            ).also { initialized ->
                val db = FirebaseFirestore.getInstance(
                    initialized,
                    "ai-studio-android-wordligh-5ca335cd-2f1f-497a-b7bb-2021e1d6149e"
                )
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        firestore = FirebaseFirestore.getInstance(
            app,
            "ai-studio-android-wordligh-5ca335cd-2f1f-497a-b7bb-2021e1d6149e"
        )
        auth = FirebaseAuth.getInstance(app)
        auth.signOut()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun buildLyricsPrompt_includesAllSpecsAndKeepsScriptureSeparate() {
        val request = AiLyricsRequest(
            title = "Shepherd of Peace",
            topic = "God's Guidance",
            genre = "Highlife",
            mood = "Joyful",
            tempo = "Upbeat (120 BPM)",
            language = "Twi",
            instruments = listOf("Talking Drum", "Piano"),
            vocalStyle = "Call & Response",
            scriptureReference = "Psalm 23:1",
            userInstructions = "Include congregation call-and-response chorus"
        )

        val prompt = FirebaseAiTextGenerator.buildLyricsPrompt(request)
        assertTrue(prompt.contains("Shepherd of Peace"))
        assertTrue(prompt.contains("God's Guidance"))
        assertTrue(prompt.contains("Highlife"))
        assertTrue(prompt.contains("Talking Drum, Piano"))
        assertTrue(prompt.contains("Call & Response"))
        assertTrue(prompt.contains("Joyful"))
        assertTrue(prompt.contains("Upbeat (120 BPM)"))
        assertTrue(prompt.contains("Twi"))
        assertTrue(prompt.contains("Psalm 23:1"))
        assertTrue(prompt.contains("DO NOT insert quoted Bible text"))
        assertTrue(prompt.contains("Include congregation call-and-response chorus"))
    }

    @Test
    fun buildLyricsPrompt_rootsInZion_enforcesSingleGenreLanguageAndNoPromptEcho() {
        val request = AiLyricsRequest(
            title = "Roots in Zion",
            topic = "Standing firm in God's covenant",
            genre = "African Gospel Worship",
            mood = "Powerful, uplifting, worshipful",
            language = "English"
        )

        val prompt = FirebaseAiTextGenerator.buildLyricsPrompt(request, forStreaming = true)
        assertTrue(prompt.contains("Song Title: Roots in Zion"))
        assertTrue(prompt.contains("Exclusive Genre (stay strictly within this single genre): African Gospel Worship"))
        assertTrue(prompt.contains("Mood & Atmosphere: Powerful, uplifting, worshipful"))
        assertTrue(prompt.contains("Language (write strictly in this language): English"))
        assertTrue(prompt.contains("do NOT copy or echo"))
        assertTrue(prompt.contains("zero duplicated verses"))
    }

    @Test
    fun liveAiLyricsGeneration_supportsAllFiveLanguagesAndSixSections() = runTest(testDispatcher) {
        val fakeAi = FakeAiTextGenerator()
        val repository = MusicRepository(firestore, auth, aiTextGenerator = fakeAi)
        val viewModel = LyricsViewModel(repository)

        val expectedLanguageTokens = mapOf(
            "English" to "Word and Light",
            "Twi" to "Onyame",
            "Akan" to "Onyame",
            "Ga" to "Nyɔŋmɔ",
            "Ewe" to "Mawu"
        )

        for ((language, expectedToken) in expectedLanguageTokens) {
            val req = AiLyricsRequest(
                title = "Light Song $language",
                topic = "Praise and Mercy",
                genre = "Gospel Worship",
                mood = "Uplifting",
                tempo = "Moderate (95 BPM)",
                language = language,
                instruments = listOf("Piano", "Talking Drum"),
                vocalStyle = "Gospel Choir",
                scriptureReference = "John 8:12",
                userInstructions = "Keep verses joyful and singable"
            )
            viewModel.generateLyrics(req)
            advanceUntilIdle()

            val state = viewModel.uiState.value as LyricsUiState.Success
            assertFalse("Live AI should mark isDemo = false", state.isDemo)
            assertEquals(FreeSparkModels.DEFAULT_FREE_MODEL, state.modelName)
            assertEquals("John 8:12", state.scriptureReference)
            assertTrue(state.lyrics.contains("Title: Light Song $language"))
            assertTrue(state.lyrics.contains("[Intro]"))
            assertTrue(state.lyrics.contains("[Verse 1]"))
            assertTrue(state.lyrics.contains("[Verse 2]"))
            assertTrue(state.lyrics.contains("[Chorus]"))
            assertTrue(state.lyrics.contains("[Bridge]"))
            assertTrue(state.lyrics.contains("[Outro]"))
            assertTrue("Missing $language token '$expectedToken'", state.lyrics.contains(expectedToken))
            // Bible reference stays separate from the lyrics text itself
            assertFalse(state.lyrics.contains("John 8:12"))
        }
    }

    @Test
    fun lyricsErrors_mapQuotaOfflinePermissionAndRetryHonestly() = runTest(testDispatcher) {
        val fakeAi = FakeAiTextGenerator()
        val repository = MusicRepository(firestore, auth, aiTextGenerator = fakeAi)
        val viewModel = LyricsViewModel(repository)

        val req = AiLyricsRequest(
            title = "Roots in Zion",
            topic = "Steadfast faith",
            genre = "African Gospel Worship",
            mood = "Powerful, uplifting, worshipful",
            language = "English"
        )

        val cases = listOf(
            AiGenerationException("Quota hit", AiFailureReason.QUOTA_EXCEEDED) to AiFailureReason.QUOTA_EXCEEDED,
            FirebaseAiTextGenerator.mapAiException(Exception("HTTP 429 RESOURCE_EXHAUSTED")) to AiFailureReason.QUOTA_EXCEEDED,
            FirebaseAiTextGenerator.mapAiException(Exception("HTTP 403 App Check attestation failed")) to AiFailureReason.PERMISSION_OR_APP_CHECK,
            FirebaseAiTextGenerator.mapAiException(UnknownHostException("offline")) to AiFailureReason.NETWORK_UNAVAILABLE,
            FirebaseAiTextGenerator.mapAiException(Exception("404 model not found")) to AiFailureReason.MODEL_UNAVAILABLE
        )

        for ((thrownError, expectedReason) in cases) {
            fakeAi.nextError = thrownError
            viewModel.generateLyrics(req)
            advanceUntilIdle()

            val errState = viewModel.uiState.value as LyricsUiState.Error
            assertEquals(expectedReason, errState.reason)
            assertFalse(errState.message.contains("Demo Mode", ignoreCase = true))

            // Retry with Gemini working returns real non-demo lyrics
            fakeAi.nextError = null
            viewModel.generateLyrics(req)
            advanceUntilIdle()
            val retrySuccess = viewModel.uiState.value as LyricsUiState.Success
            assertFalse(retrySuccess.isDemo)
            assertEquals("Roots in Zion", retrySuccess.title)
            assertTrue(retrySuccess.lyrics.contains("[Verse 1]"))
            assertTrue(retrySuccess.lyrics.contains("[Chorus]"))
            assertTrue(retrySuccess.lyrics.contains("[Verse 2]"))
        }
    }

    @Test
    fun ideaGeneratorViewModel_supportsLiveAiAndRecoversWithDemoFallback() = runTest(testDispatcher) {
        val fakeAi = FakeAiTextGenerator()
        val repository = MusicRepository(firestore, auth, aiTextGenerator = fakeAi)
        val viewModel = IdeaGeneratorViewModel(repository)

        viewModel.selectLanguage("Ewe")
        viewModel.selectCategory("Lyrics Ideas")
        viewModel.generateIdeas()
        advanceUntilIdle()

        val liveState = viewModel.uiState.value
        assertEquals(5, liveState.generatedIdeas.size)
        assertTrue(liveState.isLiveAiActive)
        assertFalse(liveState.generatedIdeas.first().isDemo)
        assertEquals(FreeSparkModels.DEFAULT_FREE_MODEL, liveState.activeModelLabel)

        // Simulate quota exceeded -> clean error banner, then Demo fallback recovers cleanly
        fakeAi.nextError = FirebaseAiTextGenerator.mapAiException(Exception("429 quota exceeded"))
        viewModel.generateMore()
        advanceUntilIdle()
        val errState = viewModel.uiState.value
        assertFalse(errState.isLiveAiActive)
        assertTrue(errState.errorMessage?.contains("quota", ignoreCase = true) == true)

        viewModel.generateDemoFallback()
        advanceUntilIdle()
        val fallbackState = viewModel.uiState.value
        assertTrue(fallbackState.useDemoMode)
        assertEquals(5, fallbackState.generatedIdeas.size)
        assertTrue(fallbackState.generatedIdeas.first().isDemo)
        assertEquals(null, fallbackState.errorMessage)
    }

    @Test
    fun sparkModelAndSecurityInvariants_holdWithoutApiKeys() {
        assertEquals("gemini-3.8-flash", FreeSparkModels.DEFAULT_FREE_MODEL)
        assertEquals(GenerativeBackend.googleAI(), FirebaseAiTextGenerator.selectedBackend())
        assertTrue(MusicRepository.IS_DEMO_MODE)

        // Ensure .env.example does not embed any active Gemini key
        val envExample = File(".env.example").takeIf { it.exists() } ?: File("../.env.example")
        if (envExample.exists()) {
            val content = envExample.readText()
            assertFalse("GEMINI_API_KEY must not remain in .env.example", content.contains("GEMINI_API_KEY="))
        }
    }

    private inline fun <reified T : Throwable> sdkError(message: String): T {
        val ctor = T::class.java.getDeclaredConstructor(String::class.java, Throwable::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(message, null)
    }

    private fun sdkUnsupportedLocation(): UnsupportedUserLocationException {
        val ctor = UnsupportedUserLocationException::class.java.getDeclaredConstructor()
        ctor.isAccessible = true
        return ctor.newInstance()
    }

    private fun sdkRequestTimeout(message: String): RequestTimeoutException {
        val ctor = RequestTimeoutException::class.java.getDeclaredConstructor(
            String::class.java,
            Throwable::class.java,
            List::class.java
        )
        ctor.isAccessible = true
        return ctor.newInstance(message, null, emptyList<Any>())
    }

    @Test
    fun mapAiException_distinguishesConcreteSdkErrorsAndNeverMasks503OrUnavailable() {
        val quota = FirebaseAiTextGenerator.mapAiException(
            sdkError<QuotaExceededException>("Quota exceeded for metric")
        )
        assertEquals(AiFailureReason.QUOTA_EXCEEDED, quota.reason)
        assertEquals(429, quota.httpStatus)
        assertTrue(quota.message!!.contains("429"))

        val serviceDisabled = FirebaseAiTextGenerator.mapAiException(
            sdkError<ServiceDisabledException>("SERVICE_DISABLED on firebasevertexai.googleapis.com")
        )
        assertEquals(AiFailureReason.PERMISSION_OR_APP_CHECK, serviceDisabled.reason)
        assertEquals(403, serviceDisabled.httpStatus)
        assertEquals("SERVICE_DISABLED", serviceDisabled.diagnosticCode)

        val permissionMissing = FirebaseAiTextGenerator.mapAiException(
            sdkError<PermissionMissingException>("HTTP 403 App Check token missing")
        )
        assertEquals(AiFailureReason.PERMISSION_OR_APP_CHECK, permissionMissing.reason)
        assertEquals(403, permissionMissing.httpStatus)

        val invalidKey = FirebaseAiTextGenerator.mapAiException(
            sdkError<InvalidAPIKeyException>("API_KEY_ANDROID_APP_BLOCKED")
        )
        assertEquals(AiFailureReason.PERMISSION_OR_APP_CHECK, invalidKey.reason)
        assertEquals("ANDROID_KEY_OR_APP_RESTRICTED", invalidKey.diagnosticCode)

        val apiNotConfigured = FirebaseAiTextGenerator.mapAiException(
            sdkError<APINotConfiguredException>("genai config not found")
        )
        assertEquals(AiFailureReason.MODEL_UNAVAILABLE, apiNotConfigured.reason)
        assertEquals(404, apiNotConfigured.httpStatus)
        assertEquals("API_NOT_CONFIGURED", apiNotConfigured.diagnosticCode)

        val timeoutErr = FirebaseAiTextGenerator.mapAiException(
            sdkRequestTimeout("Timed out")
        )
        assertEquals(AiFailureReason.NETWORK_UNAVAILABLE, timeoutErr.reason)
        assertTrue(timeoutErr.message!!.contains("Internet connection problem", ignoreCase = true))

        // Critical regression check: 503 "Service unavailable" or "FirebaseApp" errors must NOT be masked
        // behind "Live AI model is currently unavailable".
        val server503 = FirebaseAiTextGenerator.mapAiException(
            sdkError<ServerException>("HTTP 503 Service temporarily unavailable on backend")
        )
        assertEquals(AiFailureReason.UNKNOWN, server503.reason)
        assertEquals(503, server503.httpStatus)
        assertFalse(server503.message!!.contains("Live AI model is currently unavailable"))
        assertTrue(server503.message!!.contains("503"))

        val regionErr = FirebaseAiTextGenerator.mapAiException(sdkUnsupportedLocation())
        assertEquals(AiFailureReason.UNKNOWN, regionErr.reason)
        assertEquals("UNSUPPORTED_LOCATION", regionErr.diagnosticCode)

        val blockedErr = FirebaseAiTextGenerator.mapAiException(
            sdkError<PromptBlockedException>("Safety filter triggered")
        )
        assertEquals(AiFailureReason.UNKNOWN, blockedErr.reason)
        assertEquals("SAFETY_OR_STOPPED", blockedErr.diagnosticCode)
    }

    @Test
    fun sanitizeDiagnostic_redactsKeysTokensAndEmailsFromErrorMessages() {
        val raw = "Failed calling key=AIzaSyDummySecretValue1234567890 with Bearer ya29.secretTokenVal_999 " +
            "and debug uuid 12345678-1234-5678-90ab-1234567890ab for user@example.com"
        val mapped = FirebaseAiTextGenerator.mapAiException(sdkError<ServerException>(raw))
        val msg = mapped.message!!
        assertFalse(msg.contains("AIzaSy"))
        assertFalse(msg.contains("ya29."))
        assertFalse(msg.contains("12345678-1234-5678-90ab-1234567890ab"))
        assertFalse(msg.contains("user@example.com"))
    }

    @Test
    fun parseStreamingLyricsText_andStreamingFlowEmitPartialStatesBeforeSuccess() = runTest(testDispatcher) {
        val streamingGenerator = object : AiTextGenerator {
            override val modelName: String = FreeSparkModels.DEFAULT_FREE_MODEL

            override suspend fun generateLyrics(request: AiLyricsRequest): AiLyricsResult {
                error("Should stream instead")
            }

            override fun generateLyricsStream(request: AiLyricsRequest) = kotlinx.coroutines.flow.flow {
                emit(LyricsStreamEvent.Chunk("Title: Streams of Mercy\n\n[Verse 1]\nOnyame adom hyerɛn"))
                kotlinx.coroutines.yield()
                val full = "Title: Streams of Mercy\n\n[Verse 1]\nOnyame adom hyerɛn\n\n[Chorus]\nAyeyi nka Wo din / Praise Your holy name"
                emit(LyricsStreamEvent.Chunk(full))
                emit(
                    LyricsStreamEvent.Completed(
                        FirebaseAiTextGenerator.parseStreamingLyricsText(full, request, modelName)
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

        val repository = MusicRepository(firestore, auth, aiTextGenerator = streamingGenerator)
        val viewModel = LyricsViewModel(repository)

        val observedStates = mutableListOf<LyricsUiState>()
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect { observedStates.add(it) }
        }

        viewModel.generateLyrics(
            AiLyricsRequest(
                topic = "Mercy and Light",
                genre = "Gospel",
                mood = "Uplifting",
                language = "Mixed English + Twi",
                scriptureReference = "Lamentations 3:22"
            )
        )
        advanceUntilIdle()
        collectJob.cancel()

        assertTrue(
            "Expected at least one LyricsUiState.Streaming state",
            observedStates.any { it is LyricsUiState.Streaming && it.partialLyrics.contains("Onyame adom") }
        )
        val finalSuccess = viewModel.uiState.value as LyricsUiState.Success
        assertEquals("Streams of Mercy", finalSuccess.title)
        assertEquals("Lamentations 3:22", finalSuccess.scriptureReference)
        assertFalse(finalSuccess.isDemo)
        assertTrue(finalSuccess.captionCues.isNotEmpty())
        assertTrue(finalSuccess.captionCues.any { it.twiText.contains("Ayeyi") && it.englishText.contains("Praise") })
    }

    @Test
    fun parseLyricsJson_rejectsDuplicateVersesAndIncompleteSections() {
        val request = AiLyricsRequest(
            title = "Roots in Zion",
            genre = "African Gospel Worship",
            language = "English",
            mood = "Powerful, uplifting, worshipful"
        )
        val duplicateJson = """
            {
              "title": "Roots in Zion",
              "verse1": "Planted deep beside the living stream\nYour faithfulness is my eternal song",
              "verse2": "Planted deep beside the living stream\nYour faithfulness is my eternal song",
              "chorus": "We stand on Zion's holy hill\nSinging Glory to the Lamb"
            }
        """.trimIndent()
        var duplicateThrew = false
        try {
            FirebaseAiTextGenerator.parseLyricsJson(duplicateJson, request, FreeSparkModels.DEFAULT_FREE_MODEL)
        } catch (e: AiGenerationException) {
            duplicateThrew = e.diagnosticCode == "DUPLICATE_LYRICS_VERSES"
        }
        assertTrue("Duplicate verse1/verse2 must be rejected", duplicateThrew)

        val incompleteJson = """
            {
              "title": "Roots in Zion",
              "verse1": "Only one verse line",
              "chorus": ""
            }
        """.trimIndent()
        var incompleteThrew = false
        try {
            FirebaseAiTextGenerator.parseLyricsJson(incompleteJson, request, FreeSparkModels.DEFAULT_FREE_MODEL)
        } catch (e: AiGenerationException) {
            incompleteThrew = e.diagnosticCode == "INCOMPLETE_LYRICS_SECTIONS"
        }
        assertTrue("Missing chorus must be rejected", incompleteThrew)
    }

    @Test
    fun parseStreamingLyricsText_stripsPromptLeaksAndKeepsRepeatedChorusesClean() {
        val raw = """
            Write an original Christian Gospel song matching this exact specification:
            - Song Title: Roots in Zion
            - Exclusive Genre: African Gospel Worship
            Here are the lyrics:
            Title: Roots in Zion

            [Verse 1]
            Deep in the soil of grace we stand
            Anchored by Your mighty hand

            [Chorus]
            Roots in Zion, strong and free
            Jesus, You reign in majesty

            [Chorus]
            Roots in Zion, strong and free
            Jesus, You reign in majesty

            [Verse 2]
            Across the hills the drums awake
            For mercy shines at morning break

            [Chorus]
            Roots in Zion, strong and free
            Jesus, You reign in majesty

            [Bridge]
            No storm can shake the cornerstone
            Our praise belongs to Christ alone

            [Outro]
            Amen, Hallelujah
            (Demo Mode: simulated locally)
        """.trimIndent()

        val result = FirebaseAiTextGenerator.parseStreamingLyricsText(
            rawText = raw,
            request = AiLyricsRequest(
                title = "Roots in Zion",
                genre = "African Gospel Worship",
                language = "English",
                mood = "Powerful, uplifting, worshipful"
            ),
            modelName = FreeSparkModels.DEFAULT_FREE_MODEL
        )

        assertEquals("Roots in Zion", result.title)
        assertFalse(result.isDemo)
        assertFalse(result.lyrics.contains("Write an original Christian Gospel song"))
        assertFalse(result.lyrics.contains("- Exclusive Genre:"))
        assertFalse(result.lyrics.contains("Here are the lyrics"))
        assertFalse(result.lyrics.contains("simulated locally"))
        // Consecutive duplicate chorus was collapsed while second chorus after Verse 2 stayed
        assertEquals(2, Regex("\\[Chorus\\]").findAll(result.lyrics).count())
        assertTrue(result.lyrics.contains("[Verse 1]"))
        assertTrue(result.lyrics.contains("[Verse 2]"))
        assertTrue(result.lyrics.contains("[Bridge]"))
    }

    @Test
    fun incompleteStream_neverEmitsPartialSuccess() = runTest(testDispatcher) {
        val incompleteStreamGen = object : AiTextGenerator {
            override val modelName: String = FreeSparkModels.DEFAULT_FREE_MODEL
            override suspend fun generateLyrics(request: AiLyricsRequest): AiLyricsResult = error("unused")
            override fun generateLyricsStream(request: AiLyricsRequest) = kotlinx.coroutines.flow.flow {
                emit(LyricsStreamEvent.Chunk("[Verse 1]\nPartial unfinished line..."))
                // Stream terminates without Completed event
            }
            override suspend fun generateIdeas(
                category: String?,
                genre: String?,
                mood: String?,
                language: String,
                batchSeed: Int
            ): AiIdeasResult = AiIdeasResult(emptyList(), modelName, isDemo = false)
        }

        val repository = MusicRepository(firestore, auth, aiTextGenerator = incompleteStreamGen)
        val viewModel = LyricsViewModel(repository)
        viewModel.generateLyrics(
            AiLyricsRequest(
                title = "Roots in Zion",
                genre = "African Gospel Worship",
                language = "English",
                mood = "Powerful, uplifting, worshipful"
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Incomplete stream must fail honestly with Retry", state is LyricsUiState.Error)
        assertTrue((state as LyricsUiState.Error).message.contains("Retry"))
    }

    @Test
    fun mapAiException_distinguishesAllFiveFailureCategoriesAndUiHeadlines() {
        val readyDebugStatus = AppCheckStatus(
            initialized = true,
            initializedBeforeAi = true,
            providerName = "DebugAppCheckProviderFactory",
            isDebugBuild = true,
            tokenCheckAttempted = true,
            tokenObtained = true
        )

        // 1. App Check token missing
        val missingToken = FirebaseAiTextGenerator.mapAiException(
            error = sdkError<PermissionMissingException>("HTTP 403 No AppCheckProvider installed"),
            appCheckStatus = AppCheckStatus(initialized = false)
        )
        assertEquals("APP_CHECK_TOKEN_MISSING", missingToken.diagnosticCode)
        assertEquals(
            "App Check Token Missing",
            lyricsErrorHeadline(missingToken.reason, missingToken.diagnosticCode)
        )

        // 2. App Check attestation failed
        val attestationFailed = FirebaseAiTextGenerator.mapAiException(
            error = Exception("HTTP 403 Error returned from API: App attestation failed"),
            appCheckStatus = readyDebugStatus.copy(tokenObtained = false, lastTokenError = "403 App attestation failed")
        )
        assertEquals("APP_CHECK_ATTESTATION_FAILED", attestationFailed.diagnosticCode)
        assertTrue(attestationFailed.message!!.contains("Logcat (tag: AppCheckInit)"))
        assertEquals(
            "App Check Attestation Failed",
            lyricsErrorHeadline(attestationFailed.reason, attestationFailed.diagnosticCode)
        )

        // 3. Firebase permission denied (token present, backend denied permission)
        val permissionDenied = FirebaseAiTextGenerator.mapAiException(
            error = sdkError<PermissionMissingException>("HTTP 403 PERMISSION_DENIED: Caller does not have permission"),
            appCheckStatus = readyDebugStatus
        )
        assertEquals("FIREBASE_PERMISSION_DENIED", permissionDenied.diagnosticCode)
        assertEquals(
            "Firebase Permission Denied",
            lyricsErrorHeadline(permissionDenied.reason, permissionDenied.diagnosticCode)
        )

        // 4. Network unavailable
        val networkUnavailable = FirebaseAiTextGenerator.mapAiException(
            error = UnknownHostException("Unable to resolve host"),
            appCheckStatus = readyDebugStatus
        )
        assertEquals("NETWORK_ERROR", networkUnavailable.diagnosticCode)
        assertEquals(
            "Network Unavailable",
            lyricsErrorHeadline(networkUnavailable.reason, networkUnavailable.diagnosticCode)
        )

        // 5. Gemini generation failure
        val geminiFailure = FirebaseAiTextGenerator.mapAiException(
            error = sdkError<ServerException>("HTTP 503 Backend overloaded"),
            appCheckStatus = readyDebugStatus
        )
        assertEquals(
            "Gemini Generation Failure",
            lyricsErrorHeadline(geminiFailure.reason, geminiFailure.diagnosticCode)
        )
        assertTrue(geminiFailure.message!!.contains("Gemini generation failure"))
    }
}
