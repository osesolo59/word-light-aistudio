package com.aistudio.aimusic.wltv.jqr8x.data.bilingual

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsRequest
import com.aistudio.aimusic.wltv.jqr8x.data.ai.FirebaseAiTextGenerator
import com.aistudio.aimusic.wltv.jqr8x.data.media.Media3SlideshowExporter
import com.aistudio.aimusic.wltv.jqr8x.data.model.BilingualCaptionMode
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BilingualGospelAndCuesTest {

    @Test
    fun buildNaturalBilingualLyricsAndCues_respectsPerSectionEnglishTwiAndMixedAssignment() {
        val sectionMap = mapOf(
            "Intro" to "English",
            "Verse 1" to "Twi",
            "Verse 2" to "English",
            "Chorus" to "Mixed",
            "Bridge" to "Twi",
            "Outro" to "Mixed"
        )
        val (lyrics, cues) = BilingualGospelHelper.buildNaturalBilingualLyricsAndCues(
            title = "Ayeyi Ndwom",
            topic = "Morning Thanksgiving",
            genre = "Highlife",
            mood = "Joyful",
            primaryLanguage = BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI,
            sectionLanguages = sectionMap
        )

        assertTrue(lyrics.contains("[Intro — English]"))
        assertTrue(lyrics.contains("[Verse 1 — Twi]"))
        assertTrue(lyrics.contains("[Verse 2 — English]"))
        assertTrue(lyrics.contains("[Chorus — Mixed]"))
        assertTrue(lyrics.contains("[Bridge — Twi]"))
        assertTrue(lyrics.contains("[Outro — Mixed]"))
        // Natural Ghanaian Asante Twi diacritics & worship terms
        assertTrue(lyrics.contains("Onyankopɔn") || lyrics.contains("mmɔborɔhunu") || lyrics.contains("Ayeyi"))
        assertEquals(6, cues.size)
        assertTrue(cues.all { it.englishText.isNotBlank() && it.twiText.isNotBlank() })
        assertTrue(cues.zipWithNext().all { (a, b) -> a.endSec <= b.startSec })
    }

    @Test
    fun applyPreset_configuresExpectedSectionAssignments() {
        val twiVerses = BilingualGospelHelper.applyPreset("Twi Verses + English Chorus")
        assertEquals("Twi", twiVerses["Verse 1"])
        assertEquals("Twi", twiVerses["Verse 2"])
        assertEquals("English", twiVerses["Chorus"])

        val engVerses = BilingualGospelHelper.applyPreset("English Verses + Twi Chorus")
        assertEquals("English", engVerses["Verse 1"])
        assertEquals("Twi", engVerses["Chorus"])
    }

    @Test
    fun parseLyricsJson_extractsSynchronizedCuesAndFallsBackGracefully() {
        val jsonWithCues = """
            {
              "title": "Onyame Ye",
              "intro": "Lift your voice to the Lord",
              "verse1": "Anɔpa hann biara hyerɛn",
              "verse2": "Your mercy stands forever",
              "chorus": "Ayeyi nka Wo / All glory to Your name",
              "bridge": "Woyɛ Otumfoɔ",
              "outro": "Amen and Amen",
              "cues": [
                {
                  "startSec": 0,
                  "endSec": 5,
                  "section": "Verse 1",
                  "english": "Every morning light shines bright",
                  "twi": "Anɔpa hann biara hyerɛn"
                },
                {
                  "startSec": 5,
                  "endSec": 12,
                  "section": "Chorus",
                  "english": "All glory to Your name",
                  "twi": "Ayeyi nka Wo din"
                }
              ]
            }
        """.trimIndent()

        val req = AiLyricsRequest(
            topic = "Grace",
            language = BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI,
            sectionLanguages = BilingualGospelHelper.defaultSectionLanguages(BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI)
        )
        val parsed = FirebaseAiTextGenerator.parseLyricsJson(jsonWithCues, req, "gemini-3.8-flash")
        assertEquals("Onyame Ye", parsed.title)
        assertEquals(2, parsed.captionCues.size)
        assertEquals("Verse 1", parsed.captionCues[0].sectionLabel)
        assertEquals("Anɔpa hann biara hyerɛn", parsed.captionCues[0].twiText)

        // When JSON lacks cues, auto-sync helper builds timed cues from lyrics
        val jsonWithoutCues = """
            {
              "title": "Grace Song",
              "intro": "We praise You",
              "verse1": "Onyankopɔn yɛ ɔhene",
              "verse2": "Forever faithful Lord",
              "chorus": "Hallelujah / Ayeyi nka Wo",
              "bridge": "Jesus reigns",
              "outro": "Amen"
            }
        """.trimIndent()
        val fallbackParsed = FirebaseAiTextGenerator.parseLyricsJson(jsonWithoutCues, req, "gemini-3.8-flash")
        assertFalse(fallbackParsed.captionCues.isEmpty())
    }

    @OptIn(UnstableApi::class)
    @Test
    fun media3SlideshowCueResolutionFormattingAndBitmapRendering_workForAllModes() {
        val cues = listOf(
            TimedBilingualCue("c1", 0, 4, "Verse 1", "Lord You are good", "Awurade Woyɛ"),
            TimedBilingualCue("c2", 4, 9, "Chorus", "We lift Your name", "Yɛpagya Wo din")
        )

        assertEquals(0, Media3SlideshowExporter.resolveActiveCueIndex(cues, 1500L))
        assertEquals(1, Media3SlideshowExporter.resolveActiveCueIndex(cues, 6000L))
        // Wrapping modulo total cue duration (9s): 14s % 9 = 5s -> cue 1; 20s % 9 = 2s -> cue 0
        assertEquals(1, Media3SlideshowExporter.resolveActiveCueIndex(cues, 14_000L))
        assertEquals(0, Media3SlideshowExporter.resolveActiveCueIndex(cues, 20_000L))

        val (both1, both2) = Media3SlideshowExporter.formatCueLines(cues[1], BilingualCaptionMode.BOTH, "")
        assertEquals("Yɛpagya Wo din", both1)
        assertEquals("We lift Your name", both2)

        val (engOnly1, engOnly2) = Media3SlideshowExporter.formatCueLines(cues[1], BilingualCaptionMode.ENGLISH_ONLY, "")
        assertEquals("We lift Your name", engOnly1)
        assertEquals("", engOnly2)

        val (twiOnly1, twiOnly2) = Media3SlideshowExporter.formatCueLines(cues[1], BilingualCaptionMode.TWI_ONLY, "")
        assertEquals("Yɛpagya Wo din", twiOnly1)
        assertEquals("", twiOnly2)

        val emptyBmp = Media3SlideshowExporter.createCueOverlayBitmap(
            title = "",
            bibleVerse = "",
            fallbackCaptions = "",
            activeCue = null,
            mode = BilingualCaptionMode.BOTH,
            targetWidth = 1280
        )
        assertNull(emptyBmp)

        val bmpBoth = Media3SlideshowExporter.createCueOverlayBitmap(
            title = "Onyame Ye",
            bibleVerse = "Psalm 100:4",
            fallbackCaptions = "",
            activeCue = cues[0],
            mode = BilingualCaptionMode.BOTH,
            targetWidth = 1280
        )
        val bmpEngOnly = Media3SlideshowExporter.createCueOverlayBitmap(
            title = "Onyame Ye",
            bibleVerse = "Psalm 100:4",
            fallbackCaptions = "",
            activeCue = cues[0],
            mode = BilingualCaptionMode.ENGLISH_ONLY,
            targetWidth = 1280
        )
        assertNotNull(bmpBoth)
        assertNotNull(bmpEngOnly)
        assertTrue(bmpBoth!!.width > 200 && bmpBoth.height > 40)
        assertTrue(bmpBoth.height >= bmpEngOnly!!.height)
        bmpBoth.recycle()
        bmpEngOnly.recycle()
    }
}
