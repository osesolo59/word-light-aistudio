package com.aistudio.aimusic.wltv.jqr8x.data.bilingual

import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import java.util.UUID

object BilingualGospelHelper {

    const val LANGUAGE_ENGLISH = "English"
    const val LANGUAGE_TWI = "Twi"
    const val LANGUAGE_MIXED_ENGLISH_TWI = "Mixed English + Twi"
    const val LANGUAGE_AKAN = "Akan"
    const val LANGUAGE_GA = "Ga"
    const val LANGUAGE_EWE = "Ewe"

    val SUPPORTED_LANGUAGES = listOf(
        LANGUAGE_ENGLISH,
        LANGUAGE_TWI,
        LANGUAGE_MIXED_ENGLISH_TWI,
        LANGUAGE_AKAN,
        LANGUAGE_GA,
        LANGUAGE_EWE
    )

    val SONG_SECTIONS = listOf(
        "Intro",
        "Verse 1",
        "Verse 2",
        "Chorus",
        "Bridge",
        "Outro"
    )

    val SECTION_LANGUAGE_OPTIONS = listOf("English", "Twi", "Mixed")

    fun languageTestTagSuffix(language: String): String =
        language.lowercase()
            .replace("+", " ")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    fun isMixedEnglishTwi(language: String): Boolean =
        language.equals(LANGUAGE_MIXED_ENGLISH_TWI, ignoreCase = true)

    fun defaultSectionLanguages(language: String): Map<String, String> = when {
        isMixedEnglishTwi(language) -> mapOf(
            "Intro" to "English",
            "Verse 1" to "Twi",
            "Verse 2" to "English",
            "Chorus" to "Mixed",
            "Bridge" to "Twi",
            "Outro" to "Mixed"
        )
        language.equals(LANGUAGE_TWI, ignoreCase = true) || language.equals(LANGUAGE_AKAN, ignoreCase = true) ->
            SONG_SECTIONS.associateWith { "Twi" }
        else ->
            SONG_SECTIONS.associateWith { "English" }
    }

    fun applyPreset(presetName: String): Map<String, String> = when (presetName) {
        "Twi Verses + English Chorus" -> mapOf(
            "Intro" to "Twi",
            "Verse 1" to "Twi",
            "Verse 2" to "Twi",
            "Chorus" to "English",
            "Bridge" to "Mixed",
            "Outro" to "English"
        )
        "English Verses + Twi Chorus" -> mapOf(
            "Intro" to "English",
            "Verse 1" to "English",
            "Verse 2" to "English",
            "Chorus" to "Twi",
            "Bridge" to "Mixed",
            "Outro" to "Twi"
        )
        else -> mapOf(
            "Intro" to "Mixed",
            "Verse 1" to "Mixed",
            "Verse 2" to "Mixed",
            "Chorus" to "Mixed",
            "Bridge" to "Twi",
            "Outro" to "Mixed"
        )
    }

    private data class BilingualSectionLines(
        val english: List<String>,
        val twi: List<String>
    )

    private fun buildSectionContent(
        section: String,
        topic: String,
        genre: String,
        mood: String
    ): BilingualSectionLines {
        val cleanTopic = topic.trim().ifBlank { "Your Steadfast Grace" }
        return when (section) {
            "Intro" -> BilingualSectionLines(
                english = listOf(
                    "($mood $genre prelude — lift your hearts to the Lord)",
                    "We welcome Your glory in this sanctuary"
                ),
                twi = listOf(
                    "($mood $genre mfitiaseɛ — yɛde nkamfoɔ ma Awurade)",
                    "Yɛgye Wo animuonyam wɔ ha nnɛ, Onyame"
                )
            )
            "Verse 1" -> BilingualSectionLines(
                english = listOf(
                    "Morning light reveals the wonder of $cleanTopic",
                    "Every breath within my soul proclaims Your mercy",
                    "Through the storm Your gentle hand has kept me steady",
                    "You alone are worthy of my highest praise"
                ),
                twi = listOf(
                    "Anɔpa biara Wo hann hyerɛn wɔ yɛn so ($cleanTopic)",
                    "Onyankopɔn, Wo mmɔborɔhunu yɛ foforɔ daa nyinaa",
                    "Sɛ ahoyeraw ba mpo a, Wo nsa kura me mu yie",
                    "Wo nko ara na ayeyi ne nkamfoɔ sɛ Wo"
                )
            )
            "Verse 2" -> BilingualSectionLines(
                english = listOf(
                    "When the road was steep You opened doors before me",
                    "Turned my quiet tears into a song of triumph",
                    "Standing in the freedom of Your living promise",
                    "All my days belong to Jesus Christ my King"
                ),
                twi = listOf(
                    "Bere a ɔkwan no mu yɛɛ den no, Wobuee apono maa me",
                    "Wodan me awerɛhoɔ yɛɛ no anigyeɛ ne aseda dwom",
                    "Megyina Wo bɔhyɛ kɛseɛ mu a mensuro hwee bio",
                    "Me nkwa nna nyinaa yɛ Wo dea, Awurade Yesu"
                )
            )
            "Chorus" -> BilingualSectionLines(
                english = listOf(
                    "Ayeyi — all glory and honor belong to Jesus",
                    "Your grace overflows and makes our spirit new",
                    "Heaven and earth rejoice, singing Hallelujah",
                    "Faithful Savior, forever we will worship You"
                ),
                twi = listOf(
                    "Ayeyi nka Wo din, Awurade Yesu Kristo",
                    "Wo adom dɔɔso ma yɛn akoma nya ahomegyeɛ",
                    "Ɔsoro ne asaase bɔ ose to Halleluya",
                    "Ɔhene Kronkron, yɛbɛsom Wo daa ne daa"
                )
            )
            "Bridge" -> BilingualSectionLines(
                english = listOf(
                    "You are Mighty, You are Holy, our Redeemer and our Shield",
                    "Every tongue shall confess that Jesus reigns in victory"
                ),
                twi = listOf(
                    "Woyɛ Otumfoɔ, Woyɛ Kronkron, yɛn Gyefoɔ ne yɛn Kyɛm",
                    "Tɛkyerɛma biara bɛpaem aka sɛ Yesu di hene daa"
                )
            )
            else -> BilingualSectionLines( // Outro
                english = listOf(
                    "Thank You, Lord — forever in Your Word and Light",
                    "Amen, Amen — Hallelujah"
                ),
                twi = listOf(
                    "Aseda sɛ Wo, Awurade — yɛwɔ Wo hann mu daa",
                    "Amen, Amen — Halleluya, Ɔbɔadeɛ ayɛ bi"
                )
            )
        }
    }

    fun buildNaturalBilingualLyricsAndCues(
        title: String,
        topic: String,
        genre: String,
        mood: String,
        primaryLanguage: String,
        sectionLanguages: Map<String, String> = defaultSectionLanguages(primaryLanguage)
    ): Pair<String, List<TimedBilingualCue>> {
        val cleanTitle = title.trim().ifBlank { topic.trim().ifBlank { "Aseda Ne Hann (Grace & Light)" } }
        val cleanGenre = genre.trim().ifBlank { "Gospel" }
        val cleanMood = mood.trim().ifBlank { "Uplifting" }
        val resolvedSections = SONG_SECTIONS.associateWith { section ->
            sectionLanguages[section]?.takeIf { it.isNotBlank() }
                ?: defaultSectionLanguages(primaryLanguage)[section]
                ?: "English"
        }

        val cues = mutableListOf<TimedBilingualCue>()
        var cursorSec = 0

        val formatted = buildString {
            appendLine("[Demo Lyrics — $cleanGenre | $primaryLanguage | $cleanMood]")
            appendLine("Title: $cleanTitle")
            appendLine()
            SONG_SECTIONS.forEachIndexed { idx, section ->
                val secLang = resolvedSections[section] ?: "English"
                val content = buildSectionContent(section, topic.ifBlank { cleanTitle }, cleanGenre, cleanMood)
                val headerTag = if (isMixedEnglishTwi(primaryLanguage) || secLang != primaryLanguage) {
                    "[$section — $secLang]"
                } else {
                    "[$section]"
                }
                appendLine(headerTag)
                when {
                    secLang.equals("Twi", ignoreCase = true) -> {
                        content.twi.forEach { appendLine(it) }
                    }
                    secLang.equals("Mixed", ignoreCase = true) -> {
                        val maxLines = maxOf(content.english.size, content.twi.size)
                        for (i in 0 until maxLines) {
                            val eng = content.english.getOrNull(i)
                            val twi = content.twi.getOrNull(i)
                            if (eng != null && twi != null) {
                                appendLine("$eng / $twi")
                            } else {
                                appendLine(twi ?: eng.orEmpty())
                            }
                        }
                    }
                    else -> {
                        content.english.forEach { appendLine(it) }
                    }
                }
                if (idx < SONG_SECTIONS.lastIndex) appendLine()

                val durationSec = if (section == "Chorus" || section.startsWith("Verse")) 6 else 4
                cues.add(
                    TimedBilingualCue(
                        id = "cue_${idx + 1}",
                        startSec = cursorSec,
                        endSec = cursorSec + durationSec,
                        sectionLabel = "$section ($secLang)",
                        englishText = content.english.take(2).joinToString(" • "),
                        twiText = content.twi.take(2).joinToString(" • ")
                    )
                )
                cursorSec += durationSec
            }
            appendLine()
            append("(Demo Mode: AI lyric generation is simulated locally using natural Gospel templates — edit freely or use Live Gemini AI)")
        }.trim()

        return formatted to cues
    }

    fun looksLikeTwiLine(line: String): Boolean {
        val lower = line.lowercase()
        if (lower.contains('ɛ') || lower.contains('ɔ') || lower.contains('ŋ')) return true
        val twiTokens = listOf(
            "onyame", "nyankopɔn", "awurade", "ayeyi", "aseda", "adom", "hann", "nkwa",
            "mmɔborɔhunu", "asomdwoe", "nkonim", "kronkron", "bɔade", "guankɔbea",
            "momma", "yɛda", "yɛbɔ", "yɛpagya", "anɔpa", "gyefo", "otumfo"
        )
        return twiTokens.any { lower.contains(it) }
    }

    fun generateCuesFromLyrics(
        formattedLyrics: String,
        totalDurationSecHint: Int = 0,
        defaultCueSeconds: Int = 5
    ): List<TimedBilingualCue> {
        if (formattedLyrics.isBlank()) return emptyList()

        data class RawBlock(val section: String, val lines: List<String>)

        val blocks = mutableListOf<RawBlock>()
        var currentSection = "Chorus"
        val currentLines = mutableListOf<String>()

        formattedLyrics.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() ||
                trimmed.startsWith("Title:", ignoreCase = true) ||
                trimmed.startsWith("[Demo Lyrics", ignoreCase = true) ||
                trimmed.startsWith("(Demo Mode", ignoreCase = true)
            ) {
                return@forEach
            }
            val headerMatch = Regex("^\\[([^\\]]+)\\]$").find(trimmed)
            if (headerMatch != null) {
                if (currentLines.isNotEmpty()) {
                    blocks.add(RawBlock(currentSection, currentLines.toList()))
                    currentLines.clear()
                }
                currentSection = headerMatch.groupValues[1].trim()
            } else {
                currentLines.add(trimmed)
            }
        }
        if (currentLines.isNotEmpty()) {
            blocks.add(RawBlock(currentSection, currentLines.toList()))
        }
        if (blocks.isEmpty()) return emptyList()

        val rawCues = mutableListOf<TimedBilingualCue>()
        blocks.forEachIndexed { blockIdx, block ->
            // Group up to 2 lines per caption cue for crisp readability over video
            val chunks = block.lines.chunked(2)
            chunks.forEachIndexed { chunkIdx, lines ->
                val engParts = mutableListOf<String>()
                val twiParts = mutableListOf<String>()
                lines.forEach { line ->
                    if (line.contains(" / ")) {
                        val left = line.substringBefore(" / ").trim()
                        val right = line.substringAfter(" / ").trim()
                        when {
                            looksLikeTwiLine(left) && !looksLikeTwiLine(right) -> {
                                twiParts.add(left)
                                engParts.add(right)
                            }
                            else -> {
                                engParts.add(left)
                                twiParts.add(right)
                            }
                        }
                    } else if (looksLikeTwiLine(line)) {
                        twiParts.add(line)
                    } else {
                        engParts.add(line)
                    }
                }
                rawCues.add(
                    TimedBilingualCue(
                        id = "cue_${blockIdx}_${chunkIdx}_${UUID.randomUUID().toString().take(4)}",
                        startSec = 0,
                        endSec = defaultCueSeconds,
                        sectionLabel = block.section,
                        englishText = engParts.joinToString(" • "),
                        twiText = twiParts.joinToString(" • ")
                    )
                )
            }
        }

        if (rawCues.isEmpty()) return emptyList()

        val effectiveStep = if (totalDurationSecHint > 0) {
            (totalDurationSecHint / rawCues.size).coerceIn(2, 10)
        } else {
            defaultCueSeconds.coerceIn(2, 10)
        }

        var timeCursor = 0
        return rawCues.map { cue ->
            val start = timeCursor
            val end = start + effectiveStep
            timeCursor = end
            cue.copy(startSec = start, endSec = end)
        }
    }

    fun cuesToFirestoreMaps(cues: List<TimedBilingualCue>): List<Map<String, Any>> =
        cues.take(100).map { cue ->
            mapOf(
                "id" to cue.id.ifBlank { UUID.randomUUID().toString().take(8) },
                "startSec" to cue.startSec.coerceAtLeast(0),
                "endSec" to cue.endSec.coerceAtLeast(cue.startSec + 1),
                "sectionLabel" to cue.sectionLabel.trim().take(60),
                "englishText" to cue.englishText.trim().take(300),
                "twiText" to cue.twiText.trim().take(300)
            )
        }

    fun tracksToFirestoreMaps(tracks: List<AudioMixTrack>): List<Map<String, Any>> =
        tracks.take(16).map { track ->
            mapOf(
                "id" to track.id.ifBlank { UUID.randomUUID().toString().take(8) },
                "displayName" to track.displayName.trim().take(120),
                "formatLabel" to track.formatLabel.trim().take(16),
                "localUriString" to track.localUriString.trim().take(800),
                "durationMs" to track.durationMs.coerceAtLeast(0L),
                "trimStartMs" to track.trimStartMs.coerceAtLeast(0L),
                "trimEndMs" to track.trimEndMs.coerceAtLeast(0L),
                "volume" to track.volume.coerceIn(0f, 1.5f).toDouble(),
                "isMuted" to track.isMuted,
                "offsetMs" to track.offsetMs.coerceAtLeast(0L)
            )
        }
}
