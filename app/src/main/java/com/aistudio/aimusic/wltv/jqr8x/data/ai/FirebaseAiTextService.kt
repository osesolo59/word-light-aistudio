package com.aistudio.aimusic.wltv.jqr8x.data.ai

import android.util.Log
import com.aistudio.aimusic.wltv.jqr8x.data.demo.IdeaGeneratorEngine
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnknownException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

data class AiLyricsRequest(
    val title: String = "",
    val topic: String = "",
    val genre: String = "Gospel",
    val mood: String = "Uplifting",
    val tempo: String = "Moderate (95 BPM)",
    val language: String = "English",
    val sectionLanguages: Map<String, String> = emptyMap(),
    val instruments: List<String> = emptyList(),
    val vocalStyle: String = "Gospel Choir",
    val scriptureReference: String = "",
    val userInstructions: String = ""
)

data class AiLyricsResult(
    val title: String,
    val lyrics: String,
    val scriptureReference: String,
    val modelName: String,
    val isDemo: Boolean,
    val captionCues: List<TimedBilingualCue> = emptyList()
)

data class AiIdeasResult(
    val ideas: List<CreativeIdea>,
    val modelName: String,
    val isDemo: Boolean
)

enum class AiFailureReason {
    QUOTA_EXCEEDED,
    PERMISSION_OR_APP_CHECK,
    NETWORK_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    UNKNOWN
}

class AiGenerationException(
    message: String,
    val reason: AiFailureReason,
    cause: Throwable? = null,
    val httpStatus: Int? = null,
    val diagnosticCode: String = ""
) : Exception(message, cause)

sealed interface LyricsStreamEvent {
    data class Chunk(val partialText: String) : LyricsStreamEvent
    data class Completed(val result: AiLyricsResult) : LyricsStreamEvent
}

interface AiTextGenerator {
    val modelName: String
    suspend fun generateLyrics(request: AiLyricsRequest): AiLyricsResult
    fun generateLyricsStream(request: AiLyricsRequest): Flow<LyricsStreamEvent> = flow {
        emit(LyricsStreamEvent.Completed(generateLyrics(request)))
    }
    suspend fun generateIdeas(
        category: String?,
        genre: String?,
        mood: String?,
        language: String,
        batchSeed: Int
    ): AiIdeasResult
}

object FreeSparkModels {
    // Eligible free-tier Gemini Developer API model on Firebase Spark plan (no Blaze billing required)
    const val DEFAULT_FREE_MODEL = "gemini-3.8-flash"
    const val LITE_FREE_MODEL = "gemini-3.5-flash-lite"
}

class FirebaseAiTextGenerator(
    override val modelName: String = FreeSparkModels.DEFAULT_FREE_MODEL
) : AiTextGenerator {

    private val lyricsSchema by lazy {
        val cueSchema = Schema.obj(
            properties = mapOf(
                "startSec" to Schema.integer(description = "Cue start second (e.g., 0)"),
                "endSec" to Schema.integer(description = "Cue end second (e.g., 5)"),
                "section" to Schema.string(description = "Section name such as Verse 1 or Chorus"),
                "english" to Schema.string(description = "Natural English caption line or translation"),
                "twi" to Schema.string(description = "Natural Ghanaian Asante Twi caption line or translation")
            )
        )
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Original song title"),
                "intro" to Schema.string(description = "Optional short 2 to 3 line intro without bracket header"),
                "verse1" to Schema.string(description = "Verse 1 lyrics (4 to 6 complete, original singable lines)"),
                "preChorus" to Schema.string(description = "Optional 2-line Pre-Chorus lifting into the chorus"),
                "chorus" to Schema.string(description = "Memorable Gospel chorus lyrics (4 to 6 complete singable lines)"),
                "verse2" to Schema.string(description = "Verse 2 lyrics (4 to 6 completely new lines distinct from Verse 1)"),
                "bridge" to Schema.string(description = "Expressive Gospel bridge lyrics (2 to 4 lines)"),
                "finalChorus" to Schema.string(description = "Optional lifted closing chorus variation (4 lines)"),
                "outro" to Schema.string(description = "Optional uplifting closing outro lines (2 to 3 lines)"),
                "cues" to Schema.array(
                    items = cueSchema,
                    description = "Optional synchronized English + Twi timed caption cues when bilingual/Twi is requested"
                )
            ),
            optionalProperties = listOf("intro", "preChorus", "bridge", "finalChorus", "outro", "cues")
        )
    }

    private val ideasSchema by lazy {
        val ideaItemSchema = Schema.obj(
            properties = mapOf(
                "category" to Schema.string(description = "One of the 7 creative categories"),
                "title" to Schema.string(description = "Short compelling project or song title"),
                "summary" to Schema.string(description = "1 to 2 sentence creative concept summary"),
                "topic" to Schema.string(description = "Core Gospel or inspirational topic"),
                "genre" to Schema.string(description = "Music genre such as Gospel, Worship, Praise, Reggae, Highlife, Hip-hop"),
                "mood" to Schema.string(description = "Mood such as Uplifting, Joyful, Reverent, Peaceful, Soulful, Energetic"),
                "tempo" to Schema.string(description = "Tempo string e.g. Moderate (95 BPM)"),
                "instruments" to Schema.array(items = Schema.string(), description = "2 to 4 suggested instruments"),
                "vocalStyle" to Schema.string(description = "Vocal style e.g. Gospel Choir, Call & Response, Solo Female"),
                "lyricsPreview" to Schema.string(description = "Short sample Verse 1 and Chorus lines when applicable, or empty string for video ideas"),
                "videoPrompt" to Schema.string(description = "Visual scene prompt for video/music video ideas"),
                "videoTemplateName" to Schema.string(description = "Template name: Sunlight Over Cross, Ocean Waves, Mountain Majesty, or Church Interior"),
                "bibleVerse" to Schema.string(description = "Scripture reference label only (e.g. Psalm 23:1), never an invented quotation"),
                "targetStudio" to Schema.enumeration(
                    values = listOf("AUDIO", "LYRICS", "VIDEO", "MUSIC_VIDEO"),
                    description = "Target studio screen"
                )
            )
        )
        Schema.obj(
            properties = mapOf(
                "ideas" to Schema.array(
                    items = ideaItemSchema,
                    description = "Exactly 5 distinct creative ideas"
                )
            )
        )
    }

    private val geminiDeveloperBackend: GenerativeBackend = selectedBackend()

    private val lyricsModel by lazy {
        Firebase.ai(backend = geminiDeveloperBackend).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.85f
                responseMimeType = "application/json"
                responseSchema = lyricsSchema
            },
            systemInstruction = content {
                text(LYRICS_SYSTEM_INSTRUCTION)
            }
        )
    }

    private val streamingLyricsModel by lazy {
        Firebase.ai(backend = geminiDeveloperBackend).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.85f
            },
            systemInstruction = content {
                text(STREAMING_LYRICS_SYSTEM_INSTRUCTION)
            }
        )
    }

    private val ideasModel by lazy {
        Firebase.ai(backend = geminiDeveloperBackend).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.9f
                responseMimeType = "application/json"
                responseSchema = ideasSchema
            },
            systemInstruction = content {
                text(IDEAS_SYSTEM_INSTRUCTION)
            }
        )
    }

    override suspend fun generateLyrics(request: AiLyricsRequest): AiLyricsResult {
        val prompt = buildLyricsPrompt(request)
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                lyricsModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw AiGenerationException(
                    message = "Gemini returned an empty response. Please tap Retry.",
                    reason = AiFailureReason.UNKNOWN,
                    diagnosticCode = "EMPTY_LYRICS_RESPONSE"
                )
            parseLyricsJson(rawJson, request, modelName)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                val mapped = AiGenerationException(
                    message = "Internet connection problem or network timeout after 60 seconds. Check your connection and tap Retry.",
                    reason = AiFailureReason.NETWORK_UNAVAILABLE,
                    cause = e,
                    diagnosticCode = "CLIENT_TIMEOUT"
                )
                logSafeFailure("lyrics", modelName, e, mapped)
                throw mapped
            }
            throw e
        } catch (e: AiGenerationException) {
            logSafeFailure("lyrics", modelName, e, e)
            throw e
        } catch (e: Exception) {
            val mapped = mapAiException(e, modelName)
            logSafeFailure("lyrics", modelName, e, mapped)
            throw mapped
        }
    }

    override fun generateLyricsStream(request: AiLyricsRequest): Flow<LyricsStreamEvent> = flow {
        val prompt = buildLyricsPrompt(request, forStreaming = true)
        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                streamingLyricsModel.generateContentStream(prompt).collect { responseChunk ->
                    val chunkText = responseChunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        val livePreview = sanitizeStreamingPreview(accumulated.toString())
                        if (livePreview.isNotBlank()) {
                            emit(LyricsStreamEvent.Chunk(livePreview))
                        }
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isBlank()) {
                // Fall back to structured schema call before reporting failure
                emit(LyricsStreamEvent.Completed(generateLyrics(request)))
            } else {
                val parsedOrNull = runCatching {
                    parseStreamingLyricsText(finalText, request, modelName)
                }.getOrNull()
                if (parsedOrNull != null) {
                    emit(LyricsStreamEvent.Completed(parsedOrNull))
                } else {
                    Log.w(TAG, "Streamed output was incomplete or malformed; requesting structured JSON completion.")
                    emit(LyricsStreamEvent.Completed(generateLyrics(request)))
                }
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                val mapped = AiGenerationException(
                    message = "Internet connection problem or network timeout after 60 seconds. Check your connection and tap Retry.",
                    reason = AiFailureReason.NETWORK_UNAVAILABLE,
                    cause = e,
                    diagnosticCode = "CLIENT_TIMEOUT"
                )
                logSafeFailure("lyrics_stream", modelName, e, mapped)
                throw mapped
            }
            throw e
        } catch (e: AiGenerationException) {
            logSafeFailure("lyrics_stream", modelName, e, e)
            throw e
        } catch (e: Exception) {
            val mapped = mapAiException(e, modelName)
            logSafeFailure("lyrics_stream", modelName, e, mapped)
            throw mapped
        }
    }

    override suspend fun generateIdeas(
        category: String?,
        genre: String?,
        mood: String?,
        language: String,
        batchSeed: Int
    ): AiIdeasResult {
        val prompt = buildIdeasPrompt(category, genre, mood, language, batchSeed)
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                ideasModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw AiGenerationException(
                    message = "Live AI returned an empty ideas response. Please tap Retry.",
                    reason = AiFailureReason.UNKNOWN,
                    diagnosticCode = "EMPTY_IDEAS_RESPONSE"
                )
            val parsed = parseIdeasJson(
                rawJson = rawJson,
                fallbackCategory = category,
                fallbackGenre = genre,
                fallbackMood = mood,
                language = language
            )
            if (parsed.isEmpty()) {
                throw AiGenerationException(
                    message = "Live AI returned no usable ideas in JSON response. Please tap Retry.",
                    reason = AiFailureReason.UNKNOWN,
                    diagnosticCode = "EMPTY_IDEAS_ARRAY"
                )
            }
            AiIdeasResult(
                ideas = parsed,
                modelName = modelName,
                isDemo = false
            )
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                val mapped = AiGenerationException(
                    message = "Internet connection problem or network timeout while generating ideas. Check your connection and tap Retry.",
                    reason = AiFailureReason.NETWORK_UNAVAILABLE,
                    cause = e,
                    diagnosticCode = "CLIENT_TIMEOUT"
                )
                logSafeFailure("ideas", modelName, e, mapped)
                throw mapped
            }
            throw e
        } catch (e: AiGenerationException) {
            logSafeFailure("ideas", modelName, e, e)
            throw e
        } catch (e: Exception) {
            val mapped = mapAiException(e, modelName)
            logSafeFailure("ideas", modelName, e, mapped)
            throw mapped
        }
    }

    companion object {
        private const val TAG = "FirebaseAiTextGen"
        private const val AI_TIMEOUT_MS = 60_000L
        internal const val PROJECT_ID = "gen-lang-client-0518223416"

        internal fun selectedBackend(): GenerativeBackend = GenerativeBackend.googleAI()

        private const val LYRICS_SYSTEM_INSTRUCTION = """
You are an accomplished Christian Gospel songwriter and worship lyricist.
Write 100% original, complete, coherent, singable, biblically faithful song lyrics tailored strictly to the user's title, theme, genre, mood, language, tempo, instruments, and vocal style.

STRICT RULES YOU MUST OBEY:
1. ORIGINALITY & COMPLETENESS:
   - Write fresh, meaningful, poetic lyrics with complete grammatical sentences.
   - Never copy existing published songs. Never write placeholder filler, random words, or incomplete lines.
   - Never repeat Verse 1 as Verse 2. Each verse must advance the song's message.
   - Do not repeat the song title mindlessly inside unrelated verse lines.
   - Do not duplicate identical chorus blocks back-to-back.
2. STRICT GENRE CONTROL:
   - Write exclusively in the SINGLE requested genre (e.g., Gospel Worship, African Gospel, African Gospel Worship, Ghanaian Gospel, Reggae Gospel, Contemporary Worship, Afro-Gospel, Gospel Highlife, Gospel Praise, or Instrumental).
   - Never mix or blend other genres unless the user explicitly enters a fusion genre.
3. STRICT LANGUAGE FIDELITY:
   - English: write completely in natural, rich English. Do NOT inject Twi or other languages unless requested.
   - Twi / Akan: authentic, idiomatic Ghanaian Asante Twi Gospel worship expressions with proper orthography (ɛ, ɔ). Avoid robotic literal translations.
   - Mixed English + Twi: weave English and Ghanaian Twi according to the per-section language plan.
   - Ga / Ewe: write naturally in the requested Ghanaian language.
4. ZERO PROMPT ECHOING & ZERO COMMENTARY:
   - NEVER repeat, echo, or include any prompt instructions, metadata labels (Genre:, Mood:, Language:, Theme:, Prompt:), app/studio names, or conversational commentary ("Here are your lyrics", "Enjoy", etc.).
   - NEVER invent or quote fake Bible verse citations inside the sung lyrics; keep scripture references strictly separate from lyrics.
5. Keep each JSON section field clean (do NOT include bracket headers like [Verse 1] inside JSON field values). Provide synchronized English + Twi cues only when bilingual/Twi lyrics are requested.
"""

        private const val STREAMING_LYRICS_SYSTEM_INSTRUCTION = """
You are an accomplished Christian Gospel songwriter and worship lyricist.
Write 100% original, complete, coherent, singable, biblically faithful song lyrics tailored strictly to the user's title, theme, genre, mood, language, tempo, instruments, and vocal style.

STRICT RULES YOU MUST OBEY:
1. Write ONLY in the single requested genre (do not mix genres unless fusion is requested) and strictly in the requested language (pure English if English is chosen; no unrequested language mixing).
2. Verse 1 and Verse 2 MUST be completely different and meaningful. Never duplicate verses, never repeat chorus blocks back-to-back, and never insert random words or unfinished sentences.
3. NEVER include songwriting prompt instructions, field labels (Genre:, Mood:, Language:, Theme:), app names, disclaimers, or conversational notes anywhere in the output.
4. NEVER invent or quote fake scripture text inside the lyrics.
5. Output ONLY clean formatted song lyrics as plain text (NO JSON, NO markdown fences, NO commentary) using natural Gospel structure adjusted to the genre:
Title: <Song Title>

[Intro] (optional)
...

[Verse 1]
...

[Pre-Chorus] (optional)
...

[Chorus]
...

[Verse 2]
...

[Bridge]
...

[Final Chorus] (optional variation)
...

[Outro] (optional)
...
"""

        private const val IDEAS_SYSTEM_INSTRUCTION = """
You are a creative music and Gospel video producer for Word & Light AI STUDIO.
Generate exactly 5 distinct, practical, culturally relevant Gospel & inspirational ideas in the requested language (English, Twi, Mixed English + Twi, Akan, Ga, or Ewe).
Respect these category-to-targetStudio mappings:
- "Lyrics Ideas" -> targetStudio = "LYRICS"
- "Christian Video Ideas" -> targetStudio = "VIDEO"
- "Music Video Ideas" -> targetStudio = "MUSIC_VIDEO"
- "Song Ideas", "Music Style Ideas", "Gospel Worship Ideas", "Praise and Reggae Ideas" -> targetStudio = "AUDIO"
Never invent fake Bible quotations; provide only a valid book/chapter:verse reference label in `bibleVerse` (e.g., Psalm 23:1 or Romans 12:12).
"""

        internal fun buildLyricsPrompt(
            request: AiLyricsRequest,
            forStreaming: Boolean = false
        ): String {
            val cleanTitle = request.title.trim()
            val cleanTopic = request.topic.trim()
            val effectiveTheme = cleanTopic.ifBlank { cleanTitle }.ifBlank { "Walking in God's Grace and Light" }
            val cleanGenre = request.genre.trim().ifBlank { "Gospel Worship" }
            val cleanMood = request.mood.trim().ifBlank { "Uplifting" }
            val cleanLanguage = request.language.trim().ifBlank { "English" }
            val instrumentsText = if (request.instruments.isNotEmpty()) {
                request.instruments.joinToString(", ")
            } else {
                ""
            }
            val effectiveSections = if (request.sectionLanguages.isNotEmpty()) {
                request.sectionLanguages
            } else if (BilingualGospelHelper.isMixedEnglishTwi(cleanLanguage)) {
                BilingualGospelHelper.defaultSectionLanguages(cleanLanguage)
            } else {
                emptyMap()
            }
            val isInstrumental = cleanGenre.contains("Instrumental", ignoreCase = true)
            return buildString {
                appendLine("Write an original Christian Gospel song matching this exact specification (do NOT copy or echo this specification block into the output):")
                if (cleanTitle.isNotBlank()) appendLine("- Song Title: $cleanTitle")
                appendLine("- Core Theme / Message: $effectiveTheme")
                appendLine("- Exclusive Genre (stay strictly within this single genre): $cleanGenre")
                appendLine("- Mood & Atmosphere: $cleanMood")
                appendLine("- Language (write strictly in this language): $cleanLanguage")
                if (request.tempo.isNotBlank()) appendLine("- Tempo Feel: ${request.tempo.trim()}")
                if (instrumentsText.isNotBlank()) appendLine("- Musical Arrangement Feel: $instrumentsText")
                if (request.vocalStyle.isNotBlank()) appendLine("- Vocal Delivery: ${request.vocalStyle.trim()}")
                if (effectiveSections.isNotEmpty()) {
                    appendLine("- Per-Section Language Assignment (OBEY STRICTLY for each section):")
                    BilingualGospelHelper.SONG_SECTIONS.forEach { sec ->
                        val assigned = effectiveSections[sec]
                        if (!assigned.isNullOrBlank()) {
                            appendLine("  * $sec -> $assigned")
                        }
                    }
                    appendLine("  For sections marked Mixed, blend natural English and Asante Twi lines.")
                }
                if (request.scriptureReference.isNotBlank()) {
                    appendLine("- Thematic Scripture Inspiration (DO NOT insert quoted Bible text or chapter:verse citations inside the lyrics): ${request.scriptureReference.trim()}")
                }
                if (request.userInstructions.isNotBlank()) {
                    appendLine("- Additional Songwriter Notes: ${request.userInstructions.trim()}")
                }
                if (isInstrumental) {
                    appendLine("- Note: This is an Instrumental worship piece — provide concise musical/spoken worship arrangement cues and an optional brief refrain rather than dense verses.")
                } else {
                    appendLine("- Required Quality: distinct Verse 1 and Verse 2, a memorable Chorus, a Bridge, optional Intro/Pre-Chorus/Final Chorus/Outro, natural grammar, complete lines, zero duplicated verses, zero commentary.")
                }
                if (forStreaming) {
                    appendLine("Return ONLY the finished song lyrics starting with 'Title: ...' and bracketed section headers ([Intro], [Verse 1], [Pre-Chorus], [Chorus], [Verse 2], [Bridge], [Final Chorus], [Outro] as fits $cleanGenre).")
                } else {
                    appendLine("Return valid JSON matching the schema with original, distinct sections (never repeat verse1 in verse2).")
                }
            }
        }

        internal fun buildIdeasPrompt(
            category: String?,
            genre: String?,
            mood: String?,
            language: String,
            batchSeed: Int
        ): String = buildString {
            appendLine("Generate 5 fresh, original Gospel/Christian creative studio ideas.")
            appendLine("- Language: ${language.ifBlank { "English" }}")
            appendLine("- Category preference: ${category ?: "Mix across Song Ideas, Lyrics Ideas, Music Style Ideas, Gospel Worship Ideas, Praise and Reggae Ideas, Music Video Ideas, and Christian Video Ideas"}")
            if (!genre.isNullOrBlank()) appendLine("- Genre filter: $genre")
            if (!mood.isNullOrBlank()) appendLine("- Mood filter: $mood")
            appendLine("- Creative variation seed: $batchSeed (make titles and angles distinct from standard defaults)")
        }

        private val PROMPT_LEAK_PATTERNS = listOf(
            Regex("^create original gospel song lyrics.*$", RegexOption.IGNORE_CASE),
            Regex("^write an original christian gospel song.*$", RegexOption.IGNORE_CASE),
            Regex("^-\\s*(song title or topic|preferred song title|song title|core theme|exclusive genre|genre|music style|vocal style|vocal delivery|mood|tempo|language|per-section language|thematic scripture|separate scripture|additional songwriter).*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*(here (are|is) (the|your) (original )?lyrics.*|note:.*|disclaimer:.*)\\s*$", RegexOption.IGNORE_CASE),
            Regex(".*\\(demo mode:.*\\).*", RegexOption.IGNORE_CASE),
            Regex("^\\[demo lyrics.*\\]$", RegexOption.IGNORE_CASE),
            Regex(".*simulated locally.*", RegexOption.IGNORE_CASE)
        )

        internal fun stripLeakedPromptLines(text: String): String =
            text.lines()
                .filterNot { line ->
                    val t = line.trim()
                    t.isNotEmpty() && PROMPT_LEAK_PATTERNS.any { it.matches(t) }
                }
                .joinToString("\n")
                .trim()

        internal fun sanitizeStreamingPreview(rawAccumulated: String): String {
            val unfenced = rawAccumulated
                .replace(Regex("^```[a-zA-Z]*\\s*"), "")
                .replace(Regex("\\s*```$"), "")
            return stripLeakedPromptLines(unfenced)
        }

        private fun normalizeForDedup(sectionText: String): String =
            sectionText.lowercase()
                .replace(Regex("[^a-z0-9\\p{L}]+"), " ")
                .trim()

        internal fun parseLyricsJson(
            rawJson: String,
            request: AiLyricsRequest,
            modelName: String
        ): AiLyricsResult {
            val json = JSONObject(rawJson)
            val resolvedTitle = request.title.trim()
                .ifBlank { json.optString("title").trim() }
                .ifBlank { request.topic.trim() }
                .ifBlank { "Song of Praise" }
                .take(100)

            fun cleanSection(key: String): String {
                val raw = json.optString(key).trim()
                val withoutHeader = raw.replace(
                    Regex("^\\[(intro|verse\\s*1|verse\\s*2|pre[-\\s]*chorus|chorus|bridge|final\\s*chorus|outro)[^\\]]*\\]\\s*", RegexOption.IGNORE_CASE),
                    ""
                ).trim()
                return stripLeakedPromptLines(withoutHeader)
            }

            val isInstrumental = request.genre.contains("Instrumental", ignoreCase = true)
            val intro = cleanSection("intro")
            val verse1 = cleanSection("verse1")
            val preChorus = cleanSection("preChorus")
            val chorus = cleanSection("chorus")
            val verse2 = cleanSection("verse2")
            val bridge = cleanSection("bridge")
            val finalChorus = cleanSection("finalChorus")
            val outro = cleanSection("outro")

            if (!isInstrumental) {
                if (verse1.isBlank() || chorus.isBlank()) {
                    throw AiGenerationException(
                        message = "Gemini returned an incomplete song without Verse 1 and Chorus. Please tap Retry.",
                        reason = AiFailureReason.UNKNOWN,
                        diagnosticCode = "INCOMPLETE_LYRICS_SECTIONS"
                    )
                }
                if (verse2.isNotBlank() && normalizeForDedup(verse1) == normalizeForDedup(verse2)) {
                    throw AiGenerationException(
                        message = "Gemini returned duplicated verses. Please tap Retry.",
                        reason = AiFailureReason.UNKNOWN,
                        diagnosticCode = "DUPLICATE_LYRICS_VERSES"
                    )
                }
            } else if (intro.isBlank() && verse1.isBlank() && chorus.isBlank() && outro.isBlank()) {
                throw AiGenerationException(
                    message = "Gemini returned an empty instrumental arrangement. Please tap Retry.",
                    reason = AiFailureReason.UNKNOWN,
                    diagnosticCode = "EMPTY_INSTRUMENTAL_RESPONSE"
                )
            }

            val secLang = request.sectionLanguages
            fun header(name: String): String {
                val assigned = secLang[name]
                return if (!assigned.isNullOrBlank() && BilingualGospelHelper.isMixedEnglishTwi(request.language)) {
                    "[$name — $assigned]"
                } else {
                    "[$name]"
                }
            }

            val formattedLyrics = buildString {
                appendLine("Title: $resolvedTitle")
                if (intro.isNotBlank()) {
                    appendLine()
                    appendLine(header("Intro"))
                    appendLine(intro)
                }
                if (verse1.isNotBlank()) {
                    appendLine()
                    appendLine(header("Verse 1"))
                    appendLine(verse1)
                }
                if (preChorus.isNotBlank()) {
                    appendLine()
                    appendLine(header("Pre-Chorus"))
                    appendLine(preChorus)
                }
                if (chorus.isNotBlank()) {
                    appendLine()
                    appendLine(header("Chorus"))
                    appendLine(chorus)
                }
                if (verse2.isNotBlank()) {
                    appendLine()
                    appendLine(header("Verse 2"))
                    appendLine(verse2)
                }
                if (bridge.isNotBlank() && normalizeForDedup(bridge) != normalizeForDedup(chorus)) {
                    appendLine()
                    appendLine(header("Bridge"))
                    appendLine(bridge)
                }
                if (finalChorus.isNotBlank() && normalizeForDedup(finalChorus) != normalizeForDedup(chorus)) {
                    appendLine()
                    appendLine(header("Final Chorus"))
                    appendLine(finalChorus)
                }
                if (outro.isNotBlank()) {
                    appendLine()
                    appendLine(header("Outro"))
                    append(outro)
                }
            }.trim()

            val parsedCues = buildList {
                val cuesArr = json.optJSONArray("cues")
                if (cuesArr != null) {
                    for (i in 0 until cuesArr.length()) {
                        val item = cuesArr.optJSONObject(i) ?: continue
                        val eng = item.optString("english").trim()
                        val twi = item.optString("twi").trim()
                        if (eng.isBlank() && twi.isBlank()) continue
                        val startSec = item.optInt("startSec", i * 5).coerceAtLeast(0)
                        val endSec = item.optInt("endSec", startSec + 5).coerceAtLeast(startSec + 1)
                        add(
                            TimedBilingualCue(
                                id = "ai_cue_${i + 1}",
                                startSec = startSec,
                                endSec = endSec,
                                sectionLabel = item.optString("section").trim().ifBlank { "Verse" },
                                englishText = eng,
                                twiText = twi
                            )
                        )
                    }
                }
            }.ifEmpty {
                BilingualGospelHelper.generateCuesFromLyrics(formattedLyrics)
            }

            return AiLyricsResult(
                title = resolvedTitle,
                lyrics = formattedLyrics,
                scriptureReference = request.scriptureReference.trim(),
                modelName = modelName,
                isDemo = false,
                captionCues = parsedCues
            )
        }

        internal fun parseStreamingLyricsText(
            rawText: String,
            request: AiLyricsRequest,
            modelName: String
        ): AiLyricsResult {
            val cleaned = stripLeakedPromptLines(
                rawText
                    .replace(Regex("^```[a-zA-Z]*\\s*"), "")
                    .replace(Regex("\\s*```$"), "")
                    .trim()
            )
            val lines = cleaned.lines()
            val titleLine = lines.firstOrNull { it.trim().startsWith("Title:", ignoreCase = true) }
            val extractedTitle = titleLine
                ?.substringAfter(":")
                ?.trim()
                ?.removeSurrounding("\"")
                .orEmpty()
            val resolvedTitle = request.title.trim()
                .ifBlank { extractedTitle }
                .ifBlank { request.topic.trim() }
                .ifBlank { "Song of Praise" }
                .take(100)

            // Parse sections in natural song order and reject incomplete / duplicated streamed responses
            val sections = mutableListOf<Pair<String, MutableList<String>>>()
            var currentLines: MutableList<String>? = null
            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("Title:", ignoreCase = true)) continue
                val bracketMatch = Regex("^\\[([^\\]]+)\\]$").find(trimmed)
                if (bracketMatch != null) {
                    val hdr = bracketMatch.groupValues[1].trim()
                    val list = mutableListOf<String>()
                    sections.add(hdr to list)
                    currentLines = list
                } else {
                    if (currentLines == null) {
                        val list = mutableListOf<String>()
                        sections.add("Verse 1" to list)
                        currentLines = list
                    }
                    currentLines.add(trimmed)
                }
            }

            // Drop empty sections and accidental consecutive identical chorus/section duplicates
            val dedupedSections = mutableListOf<Pair<String, List<String>>>()
            for ((hdr, secLines) in sections) {
                if (secLines.isEmpty()) continue
                val normCurr = normalizeForDedup(secLines.joinToString("\n"))
                val prevNorm = dedupedSections.lastOrNull()?.second?.let { normalizeForDedup(it.joinToString("\n")) }
                if (normCurr.isNotEmpty() && normCurr == prevNorm) {
                    continue
                }
                dedupedSections.add(hdr to secLines.toList())
            }

            val isInstrumental = request.genre.contains("Instrumental", ignoreCase = true)
            val bodyAfterTitle = buildString {
                appendLine("Title: $resolvedTitle")
                dedupedSections.forEach { (hdr, secLines) ->
                    appendLine()
                    appendLine("[$hdr]")
                    secLines.forEach { appendLine(it) }
                }
            }.trim()

            if (!isInstrumental) {
                val v1 = dedupedSections.firstOrNull { it.first.startsWith("Verse 1", ignoreCase = true) }?.second?.joinToString("\n").orEmpty()
                val v2 = dedupedSections.firstOrNull { it.first.startsWith("Verse 2", ignoreCase = true) }?.second?.joinToString("\n").orEmpty()
                val ch = dedupedSections.firstOrNull {
                    it.first.equals("Chorus", ignoreCase = true) || it.first.contains("Chorus", ignoreCase = true)
                }?.second?.joinToString("\n").orEmpty()
                if (v1.isBlank() || ch.isBlank()) {
                    throw AiGenerationException(
                        message = "Gemini returned incomplete lyric sections. Please tap Retry.",
                        reason = AiFailureReason.UNKNOWN,
                        diagnosticCode = "INCOMPLETE_STREAMED_LYRICS"
                    )
                }
                if (v2.isNotBlank() && normalizeForDedup(v1) == normalizeForDedup(v2)) {
                    throw AiGenerationException(
                        message = "Gemini returned duplicated verses. Please tap Retry.",
                        reason = AiFailureReason.UNKNOWN,
                        diagnosticCode = "DUPLICATE_STREAMED_VERSES"
                    )
                }
            } else if (dedupedSections.isEmpty()) {
                throw AiGenerationException(
                    message = "Gemini returned an empty response. Please tap Retry.",
                    reason = AiFailureReason.UNKNOWN,
                    diagnosticCode = "EMPTY_STREAMED_LYRICS"
                )
            }

            val parsedCues = BilingualGospelHelper.generateCuesFromLyrics(bodyAfterTitle)
            return AiLyricsResult(
                title = resolvedTitle,
                lyrics = bodyAfterTitle,
                scriptureReference = request.scriptureReference.trim(),
                modelName = modelName,
                isDemo = false,
                captionCues = parsedCues
            )
        }

        internal fun parseIdeasJson(
            rawJson: String,
            fallbackCategory: String?,
            fallbackGenre: String?,
            fallbackMood: String?,
            language: String
        ): List<CreativeIdea> {
            val root = JSONObject(rawJson)
            val array = root.optJSONArray("ideas") ?: return emptyList()
            val results = mutableListOf<CreativeIdea>()
            val count = minOf(array.length(), 5)
            for (i in 0 until count) {
                val item = array.optJSONObject(i) ?: continue
                val category = item.optString("category")
                    .ifBlank { fallbackCategory ?: IdeaGeneratorEngine.CATEGORIES[i % IdeaGeneratorEngine.CATEGORIES.size] }
                val rawTarget = item.optString("targetStudio").trim().uppercase()
                val targetStudio = when {
                    rawTarget in setOf("AUDIO", "LYRICS", "VIDEO", "MUSIC_VIDEO") -> rawTarget
                    category == "Lyrics Ideas" -> "LYRICS"
                    category == "Christian Video Ideas" -> "VIDEO"
                    category == "Music Video Ideas" -> "MUSIC_VIDEO"
                    else -> "AUDIO"
                }
                val instArr = item.optJSONArray("instruments")
                val instruments = buildList {
                    if (instArr != null) {
                        for (j in 0 until instArr.length()) {
                            val value = instArr.optString(j).trim()
                            if (value.isNotEmpty()) add(value)
                        }
                    }
                    if (isEmpty()) add("Piano")
                }
                val resolvedTitle = item.optString("title").trim().ifBlank { "Word & Light Inspiration ${i + 1}" }
                results.add(
                    CreativeIdea(
                        id = "ai_${UUID.randomUUID().toString().take(8)}",
                        category = category,
                        title = resolvedTitle,
                        summary = item.optString("summary").trim().ifBlank { "AI-suggested concept for $resolvedTitle." },
                        topic = item.optString("topic").trim().ifBlank { resolvedTitle },
                        genre = fallbackGenre ?: item.optString("genre").trim().ifBlank { "Gospel" },
                        mood = fallbackMood ?: item.optString("mood").trim().ifBlank { "Uplifting" },
                        language = language,
                        tempo = item.optString("tempo").trim().ifBlank { "Moderate (95 BPM)" },
                        instruments = instruments,
                        vocalStyle = item.optString("vocalStyle").trim().ifBlank { "Gospel Choir" },
                        lyricsPreview = item.optString("lyricsPreview").trim(),
                        videoPrompt = item.optString("videoPrompt").trim(),
                        videoTemplateName = item.optString("videoTemplateName").trim().ifBlank { "Sunlight Over Cross" },
                        bibleVerse = item.optString("bibleVerse").trim(),
                        targetStudio = targetStudio,
                        isDemo = false
                    )
                )
            }
            return results
        }

        internal fun sanitizeDiagnostic(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw
                .replace(Regex("AIza[0-9A-Za-z_-]{10,}"), "[REDACTED_KEY]")
                .replace(Regex("Bearer\\s+[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE), "Bearer [REDACTED_TOKEN]")
                .replace(Regex("eyJ[A-Za-z0-9_-]{10,}(?:\\.[A-Za-z0-9_-]+)*"), "[REDACTED_JWT]")
                .replace(Regex("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"), "[REDACTED_TOKEN]")
                .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[REDACTED_EMAIL]")
                .replace(Regex("(?i)(api_?key|token|secret|authorization|auth)[=:]\\s*[^\\s,;]+"), "$1=[REDACTED]")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(160)
        }

        internal fun extractHttpStatus(chainMessages: String): Int? {
            val match = Regex("\\b(400|401|403|404|429|500|502|503|504)\\b").find(chainMessages)
            return match?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        private fun logSafeFailure(
            operation: String,
            modelName: String,
            original: Throwable,
            mapped: AiGenerationException
        ) {
            val causeTypes = generateSequence(original) { it.cause }
                .joinToString(" -> ") { it.javaClass.simpleName }
            val safeMsg = sanitizeDiagnostic(original.message)
            Log.e(
                TAG,
                "Live AI failure op=$operation provider=GeminiDeveloperAPI model=$modelName " +
                    "reason=${mapped.reason} code=${mapped.diagnosticCode} http=${mapped.httpStatus ?: "n/a"} " +
                    "chain=[$causeTypes] detail=\"$safeMsg\""
            )
        }

        internal fun mapAiException(
            error: Throwable,
            modelName: String = FreeSparkModels.DEFAULT_FREE_MODEL
        ): AiGenerationException {
            val causeChain = generateSequence(error) { it.cause }.toList()
            val rawChainMessages = causeChain
                .mapNotNull { it.message }
                .joinToString(" | ")
            val lower = rawChainMessages.lowercase()
            val safeDetail = sanitizeDiagnostic(rawChainMessages)
            val rootType = causeChain.lastOrNull()?.javaClass?.simpleName ?: "Exception"
            val detectedStatus = extractHttpStatus(rawChainMessages)

            fun hasQuotaError(): Boolean =
                causeChain.any { it is QuotaExceededException } ||
                    detectedStatus == 429 ||
                    lower.contains("quota") ||
                    lower.contains("resource_exhausted") ||
                    lower.contains("rate limit") ||
                    lower.contains("too many requests")

            fun hasServiceDisabled(): Boolean =
                causeChain.any { it is ServiceDisabledException } ||
                    lower.contains("service_disabled") ||
                    lower.contains("has not been used in project") ||
                    lower.contains("it is disabled")

            fun hasInvalidOrRestrictedKey(): Boolean =
                causeChain.any { it is InvalidAPIKeyException } ||
                    lower.contains("api key not valid") ||
                    lower.contains("api_key_invalid") ||
                    lower.contains("api_key_android_app_blocked") ||
                    lower.contains("requests from this android client application")

            fun hasAppCheckOrPermissionError(): Boolean =
                causeChain.any { it is PermissionMissingException } ||
                    detectedStatus == 403 ||
                    detectedStatus == 401 ||
                    lower.contains("app check") ||
                    lower.contains("appcheck") ||
                    lower.contains("app_check") ||
                    lower.contains("attestation") ||
                    lower.contains("permission_denied") ||
                    lower.contains("permission denied") ||
                    lower.contains("unauthenticated") ||
                    lower.contains("unauthorized")

            fun hasApiNotConfigured(): Boolean =
                causeChain.any { it is APINotConfiguredException } ||
                    lower.contains("genai config not found") ||
                    lower.contains("error-genai-config-not-found")

            fun hasModelOrEndpoint404(): Boolean =
                detectedStatus == 404 ||
                    (lower.contains("models/") && lower.contains("not found")) ||
                    lower.contains("model not found") ||
                    lower.contains("was not found") ||
                    lower.contains("not_found") ||
                    lower.contains("url not found")

            fun hasNetworkError(): Boolean =
                causeChain.any {
                    it is RequestTimeoutException ||
                        it is TimeoutCancellationException ||
                        it is UnknownHostException ||
                        it is SocketTimeoutException ||
                        it is ConnectException ||
                        it is IOException
                } ||
                    lower.contains("unable to resolve host") ||
                    lower.contains("failed to connect") ||
                    lower.contains("no address associated") ||
                    lower.contains("network") ||
                    lower.contains("offline") ||
                    lower.contains("timeout")

            fun hasUnsupportedLocation(): Boolean =
                causeChain.any { it is UnsupportedUserLocationException } ||
                    lower.contains("user location is not supported")

            fun hasSafetyOrStoppedError(): Boolean =
                causeChain.any {
                    it is PromptBlockedException ||
                        it is ContentBlockedException ||
                        it is ResponseStoppedException
                }

            fun hasSerializationError(): Boolean =
                causeChain.any { it is SerializationException || it is JSONException }

            return when {
                hasQuotaError() -> AiGenerationException(
                    message = "Free Spark AI quota exhausted (HTTP ${detectedStatus ?: 429}). Wait briefly and tap Retry.",
                    reason = AiFailureReason.QUOTA_EXCEEDED,
                    cause = error,
                    httpStatus = detectedStatus ?: 429,
                    diagnosticCode = "QUOTA_EXCEEDED_429"
                )

                hasServiceDisabled() -> AiGenerationException(
                    message = "Firebase AI Logic service is disabled (HTTP ${detectedStatus ?: 403} • ServiceDisabled) for project $PROJECT_ID. Please retry after backend provisioning completes.",
                    reason = AiFailureReason.PERMISSION_OR_APP_CHECK,
                    cause = error,
                    httpStatus = detectedStatus ?: 403,
                    diagnosticCode = "SERVICE_DISABLED"
                )

                hasInvalidOrRestrictedKey() -> AiGenerationException(
                    message = "App Check / Android API key restriction failed (HTTP ${detectedStatus ?: 403}). Verify google-services.json and SHA-1 fingerprint, then tap Retry.",
                    reason = AiFailureReason.PERMISSION_OR_APP_CHECK,
                    cause = error,
                    httpStatus = detectedStatus ?: 403,
                    diagnosticCode = "ANDROID_KEY_OR_APP_RESTRICTED"
                )

                hasAppCheckOrPermissionError() -> AiGenerationException(
                    message = "App Check or permission problem (HTTP ${detectedStatus ?: 403}). Ensure App Check attestation is active for this build and tap Retry.",
                    reason = AiFailureReason.PERMISSION_OR_APP_CHECK,
                    cause = error,
                    httpStatus = detectedStatus ?: 403,
                    diagnosticCode = "APP_CHECK_OR_PERMISSION_403"
                )

                hasApiNotConfigured() -> AiGenerationException(
                    message = "Firebase AI Logic (Gemini Developer API) configuration was not found (HTTP ${detectedStatus ?: 404} • APINotConfigured). Tap Retry once backend setup is complete.",
                    reason = AiFailureReason.MODEL_UNAVAILABLE,
                    cause = error,
                    httpStatus = detectedStatus ?: 404,
                    diagnosticCode = "API_NOT_CONFIGURED"
                )

                hasModelOrEndpoint404() -> AiGenerationException(
                    message = "Model or Firebase AI configuration problem (HTTP 404 • $modelName). Tap Retry or verify Gemini Developer API configuration.",
                    reason = AiFailureReason.MODEL_UNAVAILABLE,
                    cause = error,
                    httpStatus = 404,
                    diagnosticCode = "MODEL_OR_CONFIG_404"
                )

                hasNetworkError() -> AiGenerationException(
                    message = "Internet connection problem or network timeout. Check Wi-Fi/mobile data and tap Retry.",
                    reason = AiFailureReason.NETWORK_UNAVAILABLE,
                    cause = error,
                    httpStatus = detectedStatus,
                    diagnosticCode = "NETWORK_ERROR"
                )

                hasUnsupportedLocation() -> AiGenerationException(
                    message = "Live Gemini Developer API is not supported in this region ($rootType).",
                    reason = AiFailureReason.UNKNOWN,
                    cause = error,
                    httpStatus = detectedStatus,
                    diagnosticCode = "UNSUPPORTED_LOCATION"
                )

                hasSafetyOrStoppedError() -> AiGenerationException(
                    message = "AI response was blocked or stopped by safety filters ($rootType). Adjust your title or theme and tap Retry.",
                    reason = AiFailureReason.UNKNOWN,
                    cause = error,
                    httpStatus = detectedStatus,
                    diagnosticCode = "SAFETY_OR_STOPPED"
                )

                hasSerializationError() -> AiGenerationException(
                    message = "Gemini returned an unexpected response format ($rootType). Please tap Retry.",
                    reason = AiFailureReason.UNKNOWN,
                    cause = error,
                    httpStatus = detectedStatus,
                    diagnosticCode = "SERIALIZATION_ERROR"
                )

                else -> {
                    val statusTag = detectedStatus?.let { "HTTP $it • " } ?: ""
                    val sdkTag = if (causeChain.any { it is ServerException || it is UnknownException || it is FirebaseAIException }) {
                        rootType
                    } else {
                        rootType
                    }
                    val detailSuffix = if (safeDetail.isNotBlank()) ": $safeDetail" else ""
                    AiGenerationException(
                        message = "Gemini AI request failed ($statusTag$sdkTag$detailSuffix). Please tap Retry.",
                        reason = AiFailureReason.UNKNOWN,
                        cause = error,
                        httpStatus = detectedStatus,
                        diagnosticCode = rootType.uppercase()
                    )
                }
            }
        }
    }
}
