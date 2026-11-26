package com.aistudio.aimusic.wltv.jqr8x.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreateScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun customMode_lyricsAndMusicStyleButtonsAndReuseFlows_workEndToEnd() {
        var generatedTopic = ""
        var generatedGenre = ""
        var generatedLanguage = ""
        var savedLyricTitle = ""
        var savedLyricBody = ""
        var composedGenre = ""
        var composedLanguage = ""
        var composedMood = ""
        var composedTempo = ""
        var composedInstruments = emptyList<String>()
        var composedVocalStyle = ""

        val sampleSavedLyrics = listOf(
            Lyric(
                id = "lyr_1",
                title = "Aseda Twi",
                lyrics = "Ayeyi nka Onyame",
                topic = "Thanksgiving",
                genre = "Highlife",
                mood = "Joyful",
                language = "Twi"
            )
        )
        val sampleSavedSongs = listOf(
            Song(
                id = "song_1",
                title = "Old Reggae Track",
                genre = "Reggae",
                language = "Twi",
                mood = "Peaceful",
                tempo = "Slow (65 BPM)",
                instruments = listOf("Organ", "Bass"),
                vocalStyle = "Solo Male",
                lyrics = "Peace in Zion"
            )
        )

        composeRule.setContent {
            var lyricsState by remember { mutableStateOf<LyricsUiState>(LyricsUiState.Idle) }
            WordAndLightTheme {
                CustomModeScreen(
                    lyricsState = lyricsState,
                    savedSongs = sampleSavedSongs,
                    savedLyrics = sampleSavedLyrics,
                    onResetLyricsState = { lyricsState = LyricsUiState.Idle },
                    onGenerateLyrics = { topic, genre, mood, language ->
                        generatedTopic = topic
                        generatedGenre = genre
                        generatedLanguage = language
                        lyricsState = LyricsUiState.Success(
                            lyrics = "Title: Kingdom Cypher\n\n[Verse 1]\nOnyame ne yɛn hann\n\n[Chorus]\n$topic in $genre ($language | $mood)",
                            title = "Kingdom Cypher",
                            isDemo = false,
                            modelName = "gemini-3.8-flash"
                        )
                    },
                    onSaveLyrics = { title, lyrics, _, _, _, _ ->
                        savedLyricTitle = title
                        savedLyricBody = lyrics
                        lyricsState = LyricsUiState.Saved("lyr_new", title)
                    },
                    onGenerateMusic = { _, _, genre, language, mood, tempo, instruments, vocalStyle, _ ->
                        composedGenre = genre
                        composedLanguage = language
                        composedMood = mood
                        composedTempo = tempo
                        composedInstruments = instruments
                        composedVocalStyle = vocalStyle
                    }
                )
            }
        }

        // Enter topic and title
        composeRule.onNodeWithTag("custom_topic_input").performTextInput("Grace and Light")
        composeRule.onNodeWithTag("custom_title_input").performTextInput("Kingdom Cypher")

        // Select Hip-hop genre and Twi language
        composeRule.onNodeWithTag("genre_chip_hip_hop").performScrollTo().performClick()
        composeRule.onNodeWithTag("language_chip_twi").performScrollTo().performClick()

        // Select Mood, Tempo, Instruments, Vocal Style
        composeRule.onNodeWithTag("mood_chip_energetic").performScrollTo().performClick()
        composeRule.onNodeWithTag("tempo_chip_upbeat").performScrollTo().performClick()
        composeRule.onNodeWithTag("instrument_chip_talking_drum").performScrollTo().performClick()
        composeRule.onNodeWithTag("vocal_chip_call_and_response").performScrollTo().performClick()

        // Click Generate AI Lyrics button and verify Twi + Hip-hop passed and populated editor
        composeRule.onNodeWithTag("generate_lyrics_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Grace and Light", generatedTopic)
        assertEquals("Hip-hop", generatedGenre)
        assertEquals("Twi", generatedLanguage)
        composeRule.onNodeWithTag("custom_lyrics_input")
            .performScrollTo()
            .assertTextContains("Onyame ne yɛn hann", substring = true)

        // Edit lyrics and Save Lyrics
        composeRule.onNodeWithTag("custom_lyrics_input")
            .performTextReplacement("Onyame ne hann\nEdited verse")
        composeRule.onNodeWithTag("save_lyrics_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Kingdom Cypher", savedLyricTitle)
        assertTrue(savedLyricBody.contains("Edited verse"))

        // Compose Demo Track with all selected style attributes
        composeRule.onNodeWithTag("custom_compose_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Hip-hop", composedGenre)
        assertEquals("Twi", composedLanguage)
        assertEquals("Energetic", composedMood)
        assertEquals("Upbeat (120 BPM)", composedTempo)
        assertTrue(composedInstruments.contains("Talking Drum"))
        assertEquals("Call & Response", composedVocalStyle)

        // Reuse Saved Lyrics dialog loads saved Twi lyrics into editor
        composeRule.onNodeWithTag("custom_reuse_lyrics_button").performScrollTo().performClick()
        composeRule.onNodeWithText("Aseda Twi").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("custom_lyrics_input")
            .performScrollTo()
            .assertTextContains("Ayeyi nka Onyame", substring = true)

        // Reuse Previous Music Style dialog loads Reggae/Slow/Solo Male style
        composeRule.onNodeWithTag("custom_reuse_style_button").performScrollTo().performClick()
        composeRule.onNodeWithText("Old Reggae Track").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("custom_compose_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Reggae", composedGenre)
        assertEquals("Slow (65 BPM)", composedTempo)
        assertEquals("Solo Male", composedVocalStyle)
    }

    @Test
    fun simpleMode_supportsOtherCustomGenreTwiAndMusicStyles() {
        var capturedGenre = ""
        var capturedLanguage = ""
        var capturedTempo = ""
        var capturedVocal = ""

        composeRule.setContent {
            WordAndLightTheme {
                SimpleModeScreen(
                    savedSongs = emptyList(),
                    onGenerate = { _, _, genre, language, _, tempo, _, vocalStyle, _ ->
                        capturedGenre = genre
                        capturedLanguage = language
                        capturedTempo = tempo
                        capturedVocal = vocalStyle
                    }
                )
            }
        }

        composeRule.onNodeWithTag("simple_description_input").performTextInput("Acoustic hymn in Twi")
        composeRule.onNodeWithTag("simple_title_input").performTextInput("Kwantuni")
        composeRule.onNodeWithTag("genre_chip_other").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_genre_input").performScrollTo().performTextInput("Neo-Soul Gospel")
        composeRule.onNodeWithTag("language_chip_twi").performScrollTo().performClick()
        composeRule.onNodeWithTag("tempo_chip_fast").performScrollTo().performClick()
        composeRule.onNodeWithTag("vocal_chip_duet").performScrollTo().performClick()

        composeRule.onNodeWithTag("simple_compose_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("Neo-Soul Gospel", capturedGenre)
        assertEquals("Twi", capturedLanguage)
        assertEquals("Fast (140 BPM)", capturedTempo)
        assertEquals("Duet", capturedVocal)
    }

    @Test
    fun customMode_scriptureReferenceUserInstructionsCopyShareAndRetryWork() {
        var generateCalls = 0
        var savedScripture = ""
        var savedInstructions = ""
        var savedGenSource = ""

        composeRule.setContent {
            var lyricsState by remember { mutableStateOf<LyricsUiState>(LyricsUiState.Idle) }
            WordAndLightTheme {
                CustomModeScreen(
                    lyricsState = lyricsState,
                    savedSongs = emptyList(),
                    savedLyrics = emptyList(),
                    onResetLyricsState = { lyricsState = LyricsUiState.Idle },
                    onGenerateLyrics = { _, _, _, _ -> },
                    onGenerateAiLyrics = { req ->
                        generateCalls += 1
                        lyricsState = if (generateCalls == 1) {
                            LyricsUiState.Error(
                                message = "Gemini backend temporarily unreachable. Please tap Retry."
                            )
                        } else {
                            LyricsUiState.Success(
                                lyrics = "Title: ${req.title}\n\n[Verse 1]\nMawu nye kekeli\n\n[Verse 2]\nAkpe na Wò\n\n[Chorus]\nKafukafu na Wò\n\n[Bridge]\nMíedzia ha",
                                title = req.title,
                                isDemo = false,
                                modelName = "gemini-3.8-flash",
                                scriptureReference = req.scriptureReference
                            )
                        }
                    },
                    onSaveLyrics = { _, _, _, _, _, _ -> },
                    onSaveLyricsWithMeta = { _, _, _, _, _, _, scripture, instructions, genSource ->
                        savedScripture = scripture
                        savedInstructions = instructions
                        savedGenSource = genSource
                        lyricsState = LyricsUiState.Saved("lyr_ewe", "Mawu Kekeli")
                    },
                    onGenerateMusic = { _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }

        // Fill topic, title, separate scripture reference, instructions, Ewe language
        composeRule.onNodeWithTag("custom_topic_input").performScrollTo().performTextInput("Mawu Kekeli")
        composeRule.onNodeWithTag("custom_title_input").performScrollTo().performTextInput("Mawu Kekeli")
        composeRule.onNodeWithTag("custom_scripture_input").performScrollTo().performTextInput("Psalm 27:1")
        composeRule.onNodeWithTag("custom_instructions_input").performScrollTo().performTextInput("Include Ewe joyful refrain")
        composeRule.onNodeWithTag("language_chip_ewe").performScrollTo().performClick()

        // Tap Generate AI Lyrics -> surfaces honest error with Retry button (no demo fallback)
        composeRule.onNodeWithTag("generate_lyrics_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, generateCalls)
        composeRule.onNodeWithTag("lyrics_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lyrics_fallback_demo_button").assertDoesNotExist()

        // Tap Retry -> invokes real Gemini callback and populates structured lyrics cleanly
        composeRule.onNodeWithTag("lyrics_retry_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(2, generateCalls)
        composeRule.onNodeWithTag("custom_lyrics_input")
            .performScrollTo()
            .assertTextContains("[Chorus]", substring = true)

        // Test Copy Lyrics & Share Lyrics buttons
        composeRule.onNodeWithTag("copy_lyrics_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lyrics_action_feedback_chip").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("share_lyrics_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lyrics_action_feedback_chip").performScrollTo().assertIsDisplayed()

        // Save Lyrics preserves separate scripture reference and instructions with AI_GEMINI source
        composeRule.onNodeWithTag("save_lyrics_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Psalm 27:1", savedScripture)
        assertEquals("Include Ewe joyful refrain", savedInstructions)
        assertEquals("AI_GEMINI", savedGenSource)
    }
}
