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
                "intro" to Schema.string(description = "Short 2 to 4 line musical/vocal intro without bracket header"),
                "verse1" to Schema.string(description = "Verse 1 lyrics (4 to 6 singable lines)"),
                "verse2" to Schema.string(description = "Verse 2 lyrics (4 to 6 singable lines)"),
                "chorus" to Schema.string(description = "Memorable Gospel chorus lyrics (4 to 6 singable lines)"),
                "bridge" to Schema.string(description = "Expressive Gospel bridge lyrics (2 to 4 lines)"),
                "outro" to Schema.string(description = "Uplifting closing outro lines (2 to 4 lines)"),
                "cues" to Schema.array(
                    items = cueSchema,
                    description = "Optional synchronized English + Twi timed caption cues matching the song sections"
                )
            )
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
                    message = "Live AI returned an empty lyrics response. Retry or use Free Demo Mode instead.",
                    reason = AiFailureReason.UNKNOWN,
                    diagnosticCode = "EMPTY_LYRICS_RESPONSE"
                )
            parseLyricsJson(rawJson, request, modelName)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                val mapped = AiGenerationException(
                    message = "Internet connection problem or network timeout after 60 seconds. Check your connection or use Free Demo Mode.",
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
                        emit(LyricsStreamEvent.Chunk(accumulated.toString()))
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isBlank()) {
                throw AiGenerationException(
                    message = "Live AI returned an empty lyrics stream. Retry or use Free Demo Mode instead.",
                    reason = AiFailureReason.UNKNOWN,
                    diagnosticCode = "EMPTY_LYRICS_STREAM"
                )
            }
            emit(LyricsStreamEvent.Completed(parseStreamingLyricsText(finalText, request, modelName)))
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                val mapped = AiGenerationException(
                    message = "Internet connection problem or network timeout after 60 seconds. Check your connection or use Free Demo Mode.",
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
                    message = "Live AI returned an empty ideas response. Retry or use Free Demo Mode instead.",
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
                    message = "Live AI returned no usable ideas in JSON response. Retry or use Free Demo Mode instead.",
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
                    message = "Internet connection problem or network timeout while generating ideas. Check your connection or use Free Demo Mode.",
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
You are an expert Christian Gospel songwriter and Ghanaian Twi worship linguist for Word & Light AI STUDIO.
Write original, singable, spiritually uplifting song lyrics tailored to the requested language, section language plan, genre, mood, tempo, instruments, and vocal style.
Rules you MUST obey:
1. Write authentically in the requested language:
   - English: natural contemporary worship/gospel phrasing.
   - Twi / Akan: authentic, idiomatic Ghanaian Asante Twi Gospel worship expressions using proper Twi orthography (ɛ, ɔ) such as Onyankopɔn, Awurade Yesu, Aseda sɛ Wo, Ayeyi nka Wo din, Wo adom dɔɔso, Mmɔborɔhunu, Asomdwoeɛ Hene, Nkonimdifoɔ, Ɔbɔadeɛ. Avoid robotic word-for-word literal translations; use natural Ghanaian praise idioms and call-and-response cadence.
   - Mixed English + Twi: seamlessly weave English and Ghanaian Twi within the same song, strictly following any per-section language assignments (English, Twi, or Mixed English / Twi).
   - Ga: natural Ghanaian Ga Gospel expressions (e.g., Nyɔŋmɔ, Nuŋtsɔ, Yesu, Yijiemɔ, La, Wala).
   - Ewe: natural Ghanaian Ewe Gospel expressions (e.g., Mawu, Aƒetɔ, Yesu, Kafukafu, Akpe, Kekeli, Agbe).
2. NEVER invent, fabricate, or pretend to quote exact Bible verse text inside the lyrics. Keep Bible references strictly separate from the sung lyrics. If a scripture reference is supplied, let only its general theme inspire the original lyrics without quoting fabricated scripture text.
3. Always provide clear content for title, intro, verse1, verse2, chorus, bridge, and outro, plus synchronized English and Twi caption cues in `cues` whenever English, Twi, or Mixed English + Twi is selected.
4. Keep each section field cleanly focused on lyric lines without repeating bracket section labels like [Verse 1].
"""

        private const val STREAMING_LYRICS_SYSTEM_INSTRUCTION = """
You are an expert Christian Gospel songwriter and Ghanaian Twi worship linguist for Word & Light AI STUDIO.
Write original, singable, spiritually uplifting song lyrics tailored to the requested language, section language plan, genre, mood, tempo, instruments, and vocal style.
Rules you MUST obey:
1. Write authentically in the requested language (English, idiomatic Ghanaian Asante Twi with proper ɛ/ɔ orthography, Mixed English + Twi, Ga, or Ewe).
2. NEVER invent, fabricate, or pretend to quote exact Bible verse text inside the lyrics. Keep Bible references strictly separate from the sung lyrics.
3. Output clean formatted song lyrics directly as plain text (NOT JSON, NOT markdown code fences) starting with:
Title: <Original Song Title>

[Intro]
...

[Verse 1]
...

[Verse 2]
...

[Chorus]
...

[Bridge]
...

[Outro]
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
            val subject = listOf(request.title.trim(), request.topic.trim())
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" / ")
                .ifBlank { "God's Grace and Light" }
            val instrumentsText = if (request.instruments.isNotEmpty()) {
                request.instruments.joinToString(", ")
            } else {
                "Piano, Acoustic Guitar"
            }
            val effectiveSections = if (request.sectionLanguages.isNotEmpty()) {
                request.sectionLanguages
            } else if (BilingualGospelHelper.isMixedEnglishTwi(request.language)) {
                BilingualGospelHelper.defaultSectionLanguages(request.language)
            } else {
                emptyMap()
            }
            return buildString {
                appendLine("Create original Gospel song lyrics with the following specification:")
                appendLine("- Song Title or Topic: $subject")
                if (request.title.isNotBlank()) appendLine("- Preferred Song Title: ${request.title.trim()}")
                appendLine("- Genre: ${request.genre.ifBlank { "Gospel" }}")
                appendLine("- Music Style / Instruments: $instrumentsText")
                appendLine("- Vocal Style: ${request.vocalStyle.ifBlank { "Gospel Choir" }}")
                appendLine("- Mood: ${request.mood.ifBlank { "Uplifting" }}")
                appendLine("- Tempo: ${request.tempo.ifBlank { "Moderate (95 BPM)" }}")
                appendLine("- Language: ${request.language.ifBlank { "English" }}")
                if (effectiveSections.isNotEmpty()) {
                    appendLine("- Per-Section Language Assignment (OBEY STRICTLY for each section):")
                    BilingualGospelHelper.SONG_SECTIONS.forEach { sec ->
                        val assigned = effectiveSections[sec]
                        if (!assigned.isNullOrBlank()) {
                            appendLine("  * $sec -> $assigned")
                        }
                    }
                    appendLine("  For any section marked Mixed, blend natural English and Asante Twi lines (using 'English / Twi' or alternating lines).")
                }
                if (request.scriptureReference.isNotBlank()) {
                    appendLine("- Separate Scripture Theme Reference (inspiration only — DO NOT insert quoted verse text or chapter:verse citations inside the lyrics): ${request.scriptureReference.trim()}")
                }
                if (request.userInstructions.isNotBlank()) {
                    appendLine("- Additional Songwriter Instructions: ${request.userInstructions.trim()}")
                }
                if (forStreaming) {
                    appendLine("Output clean plain-text song lyrics starting with 'Title: ...' followed by [Intro], [Verse 1], [Verse 2], [Chorus], [Bridge], and [Outro] sections.")
                } else {
                    appendLine("Return JSON matching the schema with title, intro, verse1, verse2, chorus, bridge, outro, and synchronized `cues` (each cue with startSec, endSec, section, english, twi).")
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
                return raw.replace(Regex("^\\[(intro|verse\\s*1|verse\\s*2|chorus|bridge|outro)[^\\]]*\\]\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
            }

            val intro = cleanSection("intro").ifBlank { "(Warm ${request.instruments.firstOrNull() ?: "Piano"} entrance in ${request.language})" }
            val verse1 = cleanSection("verse1")
            val verse2 = cleanSection("verse2")
            val chorus = cleanSection("chorus")
            val bridge = cleanSection("bridge")
            val outro = cleanSection("outro")

            if (verse1.isBlank() || chorus.isBlank()) {
                throw AiGenerationException(
                    message = "Live AI response was missing core lyric sections. Retry or use Free Demo Mode instead.",
                    reason = AiFailureReason.UNKNOWN,
                    diagnosticCode = "INCOMPLETE_LYRICS_SECTIONS"
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
                appendLine()
                appendLine(header("Intro"))
                appendLine(intro)
                appendLine()
                appendLine(header("Verse 1"))
                appendLine(verse1)
                appendLine()
                appendLine(header("Verse 2"))
                appendLine(verse2.ifBlank { verse1 })
                appendLine()
                appendLine(header("Chorus"))
                appendLine(chorus)
                appendLine()
                appendLine(header("Bridge"))
                appendLine(bridge.ifBlank { chorus })
                appendLine()
                appendLine(header("Outro"))
                append(outro.ifBlank { intro })
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
            val cleaned = rawText
                .replace(Regex("^```[a-zA-Z]*\\s*"), "")
                .replace(Regex("\\s*```$"), "")
                .trim()
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

            val formattedLyrics = if (titleLine == null) {
                "Title: $resolvedTitle\n\n$cleaned"
            } else {
                cleaned
            }
            val parsedCues = BilingualGospelHelper.generateCuesFromLyrics(formattedLyrics)
            return AiLyricsResult(
                title = resolvedTitle,
                lyrics = formattedLyrics,
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
                    message = "Free Spark AI quota exhausted (HTTP ${detectedStatus ?: 429}). Wait briefly or use Free Demo Mode instead.",
                    reason = AiFailureReason.QUOTA_EXCEEDED,
                    cause = error,
                    httpStatus = detectedStatus ?: 429,
                    diagnosticCode = "QUOTA_EXCEEDED_429"
                )

                hasServiceDisabled() -> AiGenerationException(
                    message = "Firebase AI Logic permission/service is disabled (HTTP ${detectedStatus ?: 403} • ServiceDisabled). Verify Firebase AI Logic is enabled for project $PROJECT_ID, or use Free Demo Mode instead.",
                    reason = AiFailureReason.PERMISSION_OR_APP_CHECK,
                    cause = error,
                    httpStatus = detectedStatus ?: 403,
                    diagnosticCode = "SERVICE_DISABLED"
                )

                hasInvalidOrRestrictedKey() -> AiGenerationException(
                    message = "App check / Android API key restriction failed (HTTP ${detectedStatus ?: 403}). Verify google-services.json and SHA-1 client restrictions, or use Free Demo Mode instead.",
                    reason = AiFailureReason.PERMISSION_OR_APP_CHECK,
                    cause = error,
                    httpStatus = detectedStatus ?: 403,
                    diagnosticCode = "ANDROID_KEY_OR_APP_RESTRICTED"
                )

                hasAppCheckOrPermissionError() -> AiGenerationException(
                    message = "App Check or permission problem (HTTP ${detectedStatus ?: 403}). Ensure your debug device token is registered (or Play Integrity/reCAPTCHA is configured for release) in Firebase Console, or use Free Demo Mode instead.",
                    reason = AiFailureReason.PERMISSION_OR_APP_CHECK,
                    cause = error,
                    httpStatus = detectedStatus ?: 403,
                    diagnosticCode = "APP_CHECK_OR_PERMISSION_403"
                )

                hasApiNotConfigured() -> AiGenerationException(
                    message = "Firebase AI Logic (Gemini Developer API) configuration was not found (HTTP ${detectedStatus ?: 404} • APINotConfigured). Enable Gemini Developer API in Firebase Console, or use Free Demo Mode instead.",
                    reason = AiFailureReason.MODEL_UNAVAILABLE,
                    cause = error,
                    httpStatus = detectedStatus ?: 404,
                    diagnosticCode = "API_NOT_CONFIGURED"
                )

                hasModelOrEndpoint404() -> AiGenerationException(
                    message = "Model or Firebase AI configuration problem (HTTP 404 • $modelName). Verify Gemini Developer API setup in Firebase Console, or use Free Demo Mode instead.",
                    reason = AiFailureReason.MODEL_UNAVAILABLE,
                    cause = error,
                    httpStatus = 404,
                    diagnosticCode = "MODEL_OR_CONFIG_404"
                )

                hasNetworkError() -> AiGenerationException(
                    message = "Internet connection problem or network timeout. Check Wi-Fi/mobile data and retry, or use Free Demo Mode for offline lyrics.",
                    reason = AiFailureReason.NETWORK_UNAVAILABLE,
                    cause = error,
                    httpStatus = detectedStatus,
                    diagnosticCode = "NETWORK_ERROR"
                )

                hasUnsupportedLocation() -> AiGenerationException(
                    message = "Live Gemini Developer API is not supported in this region ($rootType). Use Free Demo Mode instead.",
                    reason = AiFailureReason.UNKNOWN,
                    cause = error,
                    httpStatus = detectedStatus,
                    diagnosticCode = "UNSUPPORTED_LOCATION"
                )

                hasSafetyOrStoppedError() -> AiGenerationException(
                    message = "AI response was blocked or stopped by safety filters ($rootType). Adjust your topic/instructions or use Free Demo Mode instead.",
                    reason = AiFailureReason.UNKNOWN,
                    cause = error,
                    httpStatus = detectedStatus,
                    diagnosticCode = "SAFETY_OR_STOPPED"
                )

                hasSerializationError() -> AiGenerationException(
                    message = "Live AI returned an unexpected response format ($rootType). Please retry or use Free Demo Mode instead.",
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
                        message = "Live AI request failed ($statusTag$sdkTag$detailSuffix). You can use Free Demo Mode instead.",
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
