package com.aistudio.aimusic.wltv.jqr8x.ui.create

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsRequest
import com.aistudio.aimusic.wltv.jqr8x.data.ai.FreeSparkModels
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.model.StudioIdeaDraft
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

private val STANDARD_GENRES = listOf(
    "Gospel", "Worship", "Praise", "Reggae", "Highlife", "Hip-hop", "Afrobeat", "Contemporary", "Other"
)
private val SUPPORTED_LANGUAGES = BilingualGospelHelper.SUPPORTED_LANGUAGES
private val MOOD_OPTIONS = listOf("Uplifting", "Joyful", "Reverent", "Peaceful", "Soulful", "Energetic")
private val TEMPO_OPTIONS = listOf("Slow (65 BPM)", "Moderate (95 BPM)", "Upbeat (120 BPM)", "Fast (140 BPM)")
private val INSTRUMENT_OPTIONS = listOf(
    "Piano", "Organ", "Acoustic Guitar", "Bass", "Drums", "Strings", "Talking Drum", "Saxophone"
)
private val VOCAL_STYLE_OPTIONS = listOf(
    "Solo Male", "Solo Female", "Gospel Choir", "Call & Response", "Duet", "Instrumental"
)

@Stable
class SimpleModeFormState {
    var description by mutableStateOf("")
    var genreSelection by mutableStateOf("Gospel")
    var customGenre by mutableStateOf("")
    var title by mutableStateOf("")
    var language by mutableStateOf("English")
    var mood by mutableStateOf("Uplifting")
    var tempo by mutableStateOf("Moderate (95 BPM)")
    var instruments by mutableStateOf(listOf("Piano"))
    var vocalStyle by mutableStateOf("Gospel Choir")
    var isShortClip by mutableStateOf(false)
}

@Stable
class CustomModeFormState {
    var lyrics by mutableStateOf("")
    var title by mutableStateOf("")
    var genreSelection by mutableStateOf("Worship")
    var customGenre by mutableStateOf("")
    var topic by mutableStateOf("")
    var mood by mutableStateOf("Uplifting")
    var language by mutableStateOf("English")
    var sectionLanguages by mutableStateOf(BilingualGospelHelper.defaultSectionLanguages("Mixed English + Twi"))
    var captionCues by mutableStateOf<List<TimedBilingualCue>>(emptyList())
    var tempo by mutableStateOf("Moderate (95 BPM)")
    var instruments by mutableStateOf(listOf("Piano", "Acoustic Guitar"))
    var vocalStyle by mutableStateOf("Gospel Choir")
    var scriptureReference by mutableStateOf("")
    var userInstructions by mutableStateOf("")
    var useDemoModeForLyrics by mutableStateOf(false)
    var generationSource by mutableStateOf("DEMO")
    var lyricsModelLabel by mutableStateOf("")
    var isShortClip by mutableStateOf(false)
}

@Composable
fun CreateScreen(
    onNavigateToLibrary: () -> Unit,
    repositoryOverride: MusicRepository? = null,
    ideaDraft: StudioIdeaDraft? = null,
    ideaDraftFlow: StateFlow<StudioIdeaDraft?>? = null,
    onConsumeIdeaDraft: (String) -> Unit = {},
    onNavigateToAudioMixer: () -> Unit = {}
) {
    val fallbackFlow = remember(ideaDraft) { MutableStateFlow(ideaDraft) }
    val observedDraft by (ideaDraftFlow ?: fallbackFlow).collectAsStateWithLifecycle()
    val activeDraft = observedDraft ?: ideaDraft

    var selectedTab by rememberSaveable {
        mutableStateOf(if (activeDraft?.idea?.targetStudio == "LYRICS") 1 else 0)
    }
    val tabs = listOf("Simple", "Custom")
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }

    val simpleFormState = remember { SimpleModeFormState() }
    val customFormState = remember { CustomModeFormState() }
    var lastAppliedTransferId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastTransferredIdeaTitle by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(activeDraft?.transferId) {
        val draft = activeDraft ?: return@LaunchedEffect
        if (draft.transferId == lastAppliedTransferId) return@LaunchedEffect
        val idea = draft.idea
        val isStandardGenre = idea.genre in STANDARD_GENRES

        // Populate Simple (Audio Studio) fields
        simpleFormState.title = idea.title
        simpleFormState.description = buildString {
            if (idea.topic.isNotBlank() && !idea.summary.contains(idea.topic, ignoreCase = true)) {
                append(idea.topic)
                append(" — ")
            }
            append(idea.summary.ifBlank { idea.lyricsPreview })
        }
        if (isStandardGenre) {
            simpleFormState.genreSelection = idea.genre
            simpleFormState.customGenre = ""
        } else if (idea.genre.isNotBlank()) {
            simpleFormState.genreSelection = "Other"
            simpleFormState.customGenre = idea.genre
        }
        if (idea.language in SUPPORTED_LANGUAGES) simpleFormState.language = idea.language
        if (idea.mood.isNotBlank()) simpleFormState.mood = idea.mood
        if (idea.tempo.isNotBlank()) simpleFormState.tempo = idea.tempo
        if (idea.instruments.isNotEmpty()) simpleFormState.instruments = idea.instruments
        if (idea.vocalStyle.isNotBlank()) simpleFormState.vocalStyle = idea.vocalStyle

        // Populate Custom (Lyrics Studio) fields
        customFormState.title = idea.title
        customFormState.topic = idea.topic.ifBlank { idea.title }
        customFormState.lyrics = idea.lyricsPreview.ifBlank { idea.summary }
        customFormState.scriptureReference = idea.bibleVerse
        customFormState.generationSource = if (idea.isDemo) "DEMO" else "AI_GEMINI"
        if (isStandardGenre) {
            customFormState.genreSelection = idea.genre
            customFormState.customGenre = ""
        } else if (idea.genre.isNotBlank()) {
            customFormState.genreSelection = "Other"
            customFormState.customGenre = idea.genre
        }
        if (idea.language in SUPPORTED_LANGUAGES) customFormState.language = idea.language
        if (idea.mood.isNotBlank()) customFormState.mood = idea.mood
        if (idea.tempo.isNotBlank()) customFormState.tempo = idea.tempo
        if (idea.instruments.isNotEmpty()) customFormState.instruments = idea.instruments
        if (idea.vocalStyle.isNotBlank()) customFormState.vocalStyle = idea.vocalStyle

        selectedTab = if (idea.targetStudio == "LYRICS") 1 else 0
        lastTransferredIdeaTitle = idea.title
        lastAppliedTransferId = draft.transferId
        onConsumeIdeaDraft(draft.transferId)
    }

    val musicViewModel: MusicViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return MusicViewModel(repository) as T
        }
    })
    val lyricsViewModel: LyricsViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return LyricsViewModel(repository) as T
        }
    })

    var savedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var savedLyrics by remember { mutableStateOf<List<Lyric>>(emptyList()) }

    LaunchedEffect(repository) {
        repository.getMySongs().collectLatest { savedSongs = it }
    }
    LaunchedEffect(repository) {
        repository.getMyLyrics().collectLatest { savedLyrics = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Audio Studio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Compose music styles and write or save lyrics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            if (lastTransferredIdeaTitle != null) {
                Spacer(modifier = Modifier.height(6.dp))
                AssistChip(
                    onClick = { lastTransferredIdeaTitle = null },
                    modifier = Modifier.testTag("create_idea_transferred_banner"),
                    label = { Text("Loaded idea: $lastTransferredIdeaTitle") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OpenAudioMixerBannerButton(onOpenAudioMixer = onNavigateToAudioMixer)
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag("create_tab_${title.lowercase()}"),
                    text = { Text(title) }
                )
            }
        }

        val musicState by musicViewModel.uiState.collectAsState()
        val lyricsState by lyricsViewModel.uiState.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                SimpleModeScreen(
                    savedSongs = savedSongs,
                    formState = simpleFormState,
                    onGenerate = { title, desc, genre, language, mood, tempo, instruments, vocalStyle, isShortClip ->
                        musicViewModel.generateMusic(
                            title = title,
                            prompt = desc,
                            lyrics = "",
                            genre = genre,
                            isShortClip = isShortClip,
                            language = language,
                            mood = mood,
                            tempo = tempo,
                            instruments = instruments,
                            vocalStyle = vocalStyle
                        )
                    }
                )
            } else {
                CustomModeScreen(
                    lyricsState = lyricsState,
                    savedSongs = savedSongs,
                    savedLyrics = savedLyrics,
                    formState = customFormState,
                    onResetLyricsState = { lyricsViewModel.reset() },
                    onGenerateLyrics = { topic, genre, mood, language ->
                        lyricsViewModel.generateLyrics(topic, genre, mood, language)
                    },
                    onGenerateAiLyrics = { request, useDemoMode ->
                        lyricsViewModel.generateLyrics(request, useDemoMode = useDemoMode)
                    },
                    onGenerateDemoFallback = { request ->
                        lyricsViewModel.generateDemoFallback(request)
                    },
                    onSaveLyrics = { title, lyricsText, topic, genre, mood, language ->
                        lyricsViewModel.saveLyrics(title, lyricsText, topic, genre, mood, language)
                    },
                    onSaveLyricsWithMeta = { title, lyricsText, topic, genre, mood, language, scriptureRef, instructions, genSource ->
                        lyricsViewModel.saveLyrics(
                            title = title,
                            lyrics = lyricsText,
                            topic = topic,
                            genre = genre,
                            mood = mood,
                            language = language,
                            scriptureReference = scriptureRef,
                            userInstructions = instructions,
                            generationSource = genSource,
                            sectionLanguages = if (BilingualGospelHelper.isMixedEnglishTwi(language)) {
                                customFormState.sectionLanguages
                            } else {
                                emptyMap()
                            },
                            captionCues = customFormState.captionCues
                        )
                    },
                    onGenerateMusic = { title, lyrics, genre, language, mood, tempo, instruments, vocalStyle, isShortClip ->
                        musicViewModel.generateMusic(
                            title = title,
                            prompt = "",
                            lyrics = lyrics,
                            genre = genre,
                            isShortClip = isShortClip,
                            language = language,
                            mood = mood,
                            tempo = tempo,
                            instruments = instruments,
                            vocalStyle = vocalStyle
                        )
                    }
                )
            }

            if (musicState is MusicUiState.Generating) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (MusicRepository.IS_DEMO_MODE) "Saving Demo Track..." else "Composing your masterpiece...",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (musicState is MusicUiState.Success) {
                val song = (musicState as MusicUiState.Success).song
                AlertDialog(
                    onDismissRequest = { musicViewModel.resetState() },
                    title = { Text(if (song.isDemo) "Demo Song Saved!" else "Song Created!") },
                    text = {
                        Text(
                            "\"${song.title}\" (${song.genre}, ${song.language}) has been saved to My Projects."
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            musicViewModel.resetState()
                            onNavigateToLibrary()
                        }) {
                            Text("Go to My Projects")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { musicViewModel.resetState() }) {
                            Text("Close")
                        }
                    }
                )
            }

            if (musicState is MusicUiState.Error) {
                val message = (musicState as MusicUiState.Error).message
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { musicViewModel.resetState() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(message)
                }
            }
        }
    }
}

@Composable
fun SimpleModeScreen(
    savedSongs: List<Song>,
    formState: SimpleModeFormState = remember { SimpleModeFormState() },
    prefillDraft: StudioIdeaDraft? = null,
    onPrefillConsumed: () -> Unit = {},
    onGenerate: (
        title: String,
        desc: String,
        genre: String,
        language: String,
        mood: String,
        tempo: String,
        instruments: List<String>,
        vocalStyle: String,
        isShortClip: Boolean
    ) -> Unit
) {
    var showReuseStyleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(prefillDraft?.transferId) {
        val draft = prefillDraft ?: return@LaunchedEffect
        val idea = draft.idea
        formState.title = idea.title
        formState.description = buildString {
            if (idea.topic.isNotBlank() && !idea.summary.contains(idea.topic, ignoreCase = true)) {
                append(idea.topic)
                append(" — ")
            }
            append(idea.summary.ifBlank { idea.lyricsPreview })
        }
        if (idea.genre in STANDARD_GENRES) {
            formState.genreSelection = idea.genre
            formState.customGenre = ""
        } else if (idea.genre.isNotBlank()) {
            formState.genreSelection = "Other"
            formState.customGenre = idea.genre
        }
        if (idea.language in SUPPORTED_LANGUAGES) formState.language = idea.language
        if (idea.mood.isNotBlank()) formState.mood = idea.mood
        if (idea.tempo.isNotBlank()) formState.tempo = idea.tempo
        if (idea.instruments.isNotEmpty()) formState.instruments = idea.instruments
        if (idea.vocalStyle.isNotBlank()) formState.vocalStyle = idea.vocalStyle
        onPrefillConsumed()
    }

    val resolvedGenre = if (formState.genreSelection == "Other" && formState.customGenre.isNotBlank()) {
        formState.customGenre.trim()
    } else {
        formState.genreSelection
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (MusicRepository.IS_DEMO_MODE) {
            DemoNotice()
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (savedSongs.isNotEmpty()) {
            OutlinedButton(
                onClick = { showReuseStyleDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simple_reuse_style_button")
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reuse Previous Music Style")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Describe your song", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = formState.description,
            onValueChange = { formState.description = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("simple_description_input"),
            placeholder = { Text("e.g. A soul-lifting worship song about hope") },
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Song Title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = formState.title,
            onValueChange = { formState.title = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("simple_title_input"),
            placeholder = { Text("Enter a title") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Genre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        GenreSelector(
            selected = formState.genreSelection,
            customGenre = formState.customGenre,
            onSelected = { formState.genreSelection = it },
            onCustomGenreChange = { formState.customGenre = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LanguageSelector(selected = formState.language, onSelected = { formState.language = it })

        Spacer(modifier = Modifier.height(16.dp))

        MusicStyleSection(
            mood = formState.mood,
            onMoodChange = { formState.mood = it },
            tempo = formState.tempo,
            onTempoChange = { formState.tempo = it },
            selectedInstruments = formState.instruments,
            onToggleInstrument = { inst ->
                formState.instruments = if (formState.instruments.contains(inst)) {
                    formState.instruments - inst
                } else {
                    formState.instruments + inst
                }
            },
            vocalStyle = formState.vocalStyle,
            onVocalStyleChange = { formState.vocalStyle = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = formState.isShortClip, onCheckedChange = { formState.isShortClip = it })
            Text("Short Clip (up to 30s)", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onGenerate(
                    formState.title,
                    formState.description,
                    resolvedGenre,
                    formState.language,
                    formState.mood,
                    formState.tempo,
                    formState.instruments,
                    formState.vocalStyle,
                    formState.isShortClip
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("simple_compose_button"),
            enabled = formState.description.isNotBlank() && formState.title.isNotBlank() && resolvedGenre.isNotBlank()
        ) {
            Text(
                if (MusicRepository.IS_DEMO_MODE) "Compose Demo Track" else "Create Song",
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showReuseStyleDialog) {
        ReusePreviousSongDialog(
            songs = savedSongs,
            includeLyrics = false,
            onDismiss = { showReuseStyleDialog = false },
            onSelectSong = { song ->
                if (song.genre in STANDARD_GENRES) {
                    formState.genreSelection = song.genre
                    formState.customGenre = ""
                } else if (song.genre.isNotBlank()) {
                    formState.genreSelection = "Other"
                    formState.customGenre = song.genre
                }
                if (song.language.isNotBlank()) formState.language = song.language
                if (song.mood.isNotBlank()) formState.mood = song.mood
                if (song.tempo.isNotBlank()) formState.tempo = song.tempo
                if (song.instruments.isNotEmpty()) formState.instruments = song.instruments
                if (song.vocalStyle.isNotBlank()) formState.vocalStyle = song.vocalStyle
                showReuseStyleDialog = false
            }
        )
    }
}

@Composable
fun DemoNotice() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Free Demo Mode is active. AI music/lyrics synthesis is simulated locally with sample audio and templates; your lyrics, titles, and music styles are saved to your account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun CustomModeScreen(
    lyricsState: LyricsUiState,
    savedSongs: List<Song>,
    savedLyrics: List<Lyric>,
    formState: CustomModeFormState = remember { CustomModeFormState() },
    prefillDraft: StudioIdeaDraft? = null,
    onPrefillConsumed: () -> Unit = {},
    onResetLyricsState: () -> Unit,
    onGenerateLyrics: (topic: String, genre: String, mood: String, language: String) -> Unit,
    onGenerateAiLyrics: ((request: AiLyricsRequest, useDemoMode: Boolean) -> Unit)? = null,
    onGenerateDemoFallback: ((request: AiLyricsRequest) -> Unit)? = null,
    onSaveLyrics: (title: String, lyrics: String, topic: String, genre: String, mood: String, language: String) -> Unit,
    onSaveLyricsWithMeta: ((
        title: String,
        lyrics: String,
        topic: String,
        genre: String,
        mood: String,
        language: String,
        scriptureReference: String,
        userInstructions: String,
        generationSource: String
    ) -> Unit)? = null,
    onGenerateMusic: (
        title: String,
        lyrics: String,
        genre: String,
        language: String,
        mood: String,
        tempo: String,
        instruments: List<String>,
        vocalStyle: String,
        isShortClip: Boolean
    ) -> Unit
) {
    var showReuseLyricsDialog by remember { mutableStateOf(false) }
    var showReuseStyleDialog by remember { mutableStateOf(false) }
    var localActionFeedback by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(prefillDraft?.transferId) {
        val draft = prefillDraft ?: return@LaunchedEffect
        val idea = draft.idea
        formState.title = idea.title
        formState.topic = idea.topic.ifBlank { idea.title }
        formState.lyrics = idea.lyricsPreview.ifBlank { idea.summary }
        formState.scriptureReference = idea.bibleVerse
        formState.generationSource = if (idea.isDemo) "DEMO" else "AI_GEMINI"
        if (idea.genre in STANDARD_GENRES) {
            formState.genreSelection = idea.genre
            formState.customGenre = ""
        } else if (idea.genre.isNotBlank()) {
            formState.genreSelection = "Other"
            formState.customGenre = idea.genre
        }
        if (idea.language in SUPPORTED_LANGUAGES) formState.language = idea.language
        if (idea.mood.isNotBlank()) formState.mood = idea.mood
        if (idea.tempo.isNotBlank()) formState.tempo = idea.tempo
        if (idea.instruments.isNotEmpty()) formState.instruments = idea.instruments
        if (idea.vocalStyle.isNotBlank()) formState.vocalStyle = idea.vocalStyle
        onPrefillConsumed()
    }

    val resolvedGenre = if (formState.genreSelection == "Other" && formState.customGenre.isNotBlank()) {
        formState.customGenre.trim()
    } else {
        formState.genreSelection
    }

    fun currentLyricsRequest(): AiLyricsRequest = AiLyricsRequest(
        title = formState.title,
        topic = formState.topic,
        genre = resolvedGenre,
        mood = formState.mood,
        tempo = formState.tempo,
        language = formState.language,
        sectionLanguages = if (BilingualGospelHelper.isMixedEnglishTwi(formState.language)) {
            formState.sectionLanguages
        } else {
            emptyMap()
        },
        instruments = formState.instruments,
        vocalStyle = formState.vocalStyle,
        scriptureReference = formState.scriptureReference,
        userInstructions = formState.userInstructions
    )

    LaunchedEffect(lyricsState) {
        when (lyricsState) {
            is LyricsUiState.Streaming -> {
                formState.lyrics = lyricsState.partialLyrics
                formState.lyricsModelLabel = "Streaming • ${lyricsState.modelName}"
            }
            is LyricsUiState.Success -> {
                formState.lyrics = lyricsState.lyrics
                if (lyricsState.captionCues.isNotEmpty()) {
                    formState.captionCues = lyricsState.captionCues
                } else if (formState.captionCues.isEmpty()) {
                    formState.captionCues = BilingualGospelHelper.generateCuesFromLyrics(lyricsState.lyrics)
                }
                if (lyricsState.title.isNotBlank() && formState.title.isBlank()) {
                    formState.title = lyricsState.title
                } else if (formState.title.isBlank() && formState.topic.isNotBlank()) {
                    formState.title = formState.topic.trim().take(60)
                }
                formState.generationSource = if (lyricsState.isDemo) "DEMO" else "AI_GEMINI"
                formState.lyricsModelLabel = if (lyricsState.isDemo) {
                    "Demo Template"
                } else {
                    "Live AI • ${lyricsState.modelName}"
                }
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Lyrics Engine Status & Toggle Card (Live Free Spark AI vs Offline Demo Fallback)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (formState.useDemoModeForLyrics) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (formState.useDemoModeForLyrics) Icons.Default.Info else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (formState.useDemoModeForLyrics) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (formState.useDemoModeForLyrics) {
                                "Lyrics: Free Demo Mode"
                            } else {
                                "Lyrics: Live AI (${FreeSparkModels.DEFAULT_FREE_MODEL})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FilterChip(
                        selected = !formState.useDemoModeForLyrics,
                        onClick = { formState.useDemoModeForLyrics = !formState.useDemoModeForLyrics },
                        modifier = Modifier.testTag("lyrics_mode_toggle"),
                        label = {
                            Text(if (formState.useDemoModeForLyrics) "Switch to Live AI" else "Live AI On")
                        }
                    )
                }
                Text(
                    text = if (formState.useDemoModeForLyrics) {
                        "Using offline local templates in English, Twi, Akan, Ga, and Ewe. Tap Switch to Live AI for Firebase AI Logic (${FreeSparkModels.DEFAULT_FREE_MODEL}) on Spark."
                    } else {
                        "Real AI lyrics powered by Firebase AI Logic (${FreeSparkModels.DEFAULT_FREE_MODEL}) on the free Spark plan. Audio composition stays in Free Demo Mode."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showReuseLyricsDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("custom_reuse_lyrics_button"),
                enabled = savedLyrics.isNotEmpty() || savedSongs.any { it.lyrics.isNotBlank() }
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reuse Lyrics", maxLines = 1)
            }

            OutlinedButton(
                onClick = { showReuseStyleDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("custom_reuse_style_button"),
                enabled = savedSongs.isNotEmpty()
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reuse Style", maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Song Topic", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = formState.topic,
            onValueChange = { formState.topic = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_topic_input"),
            placeholder = { Text("Enter a song topic or theme") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Song Title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = formState.title,
            onValueChange = { formState.title = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_title_input"),
            placeholder = { Text("Enter your song title") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Scripture Reference (Separate)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = formState.scriptureReference,
            onValueChange = { formState.scriptureReference = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_scripture_input"),
            placeholder = { Text("e.g. Psalm 23:1 or Romans 12:12 (optional)") },
            supportingText = {
                Text("Kept separate from generated lyrics; AI never invents Bible quotations.")
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Songwriter Instructions (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = formState.userInstructions,
            onValueChange = { formState.userInstructions = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_instructions_input"),
            placeholder = { Text("e.g. Emphasize gratitude, include joyful call-and-response refrain") },
            minLines = 2
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Genre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        GenreSelector(
            selected = formState.genreSelection,
            customGenre = formState.customGenre,
            onSelected = { formState.genreSelection = it },
            onCustomGenreChange = { formState.customGenre = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LanguageSelector(
            selected = formState.language,
            onSelected = { newLang ->
                formState.language = newLang
                if (BilingualGospelHelper.isMixedEnglishTwi(newLang) && formState.sectionLanguages.isEmpty()) {
                    formState.sectionLanguages = BilingualGospelHelper.defaultSectionLanguages(newLang)
                }
            }
        )

        if (BilingualGospelHelper.isMixedEnglishTwi(formState.language)) {
            Spacer(modifier = Modifier.height(12.dp))
            SectionLanguagePlannerCard(
                sectionLanguages = formState.sectionLanguages,
                onSectionLanguageChange = { section, langChoice ->
                    formState.sectionLanguages = formState.sectionLanguages.toMutableMap().apply {
                        put(section, langChoice)
                    }
                },
                onApplyPreset = { presetMap ->
                    formState.sectionLanguages = presetMap
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        MusicStyleSection(
            mood = formState.mood,
            onMoodChange = { formState.mood = it },
            tempo = formState.tempo,
            onTempoChange = { formState.tempo = it },
            selectedInstruments = formState.instruments,
            onToggleInstrument = { inst ->
                formState.instruments = if (formState.instruments.contains(inst)) {
                    formState.instruments - inst
                } else {
                    formState.instruments + inst
                }
            },
            vocalStyle = formState.vocalStyle,
            onVocalStyleChange = { formState.vocalStyle = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val isLyricsBusy = lyricsState is LyricsUiState.Loading || lyricsState is LyricsUiState.Streaming
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val req = currentLyricsRequest()
                    if (onGenerateAiLyrics != null) {
                        onGenerateAiLyrics(req, formState.useDemoModeForLyrics)
                    } else {
                        onGenerateLyrics(
                            formState.topic.ifBlank { formState.title },
                            resolvedGenre,
                            formState.mood,
                            formState.language
                        )
                    }
                },
                enabled = (formState.topic.isNotBlank() || formState.title.isNotBlank()) && !isLyricsBusy,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("generate_lyrics_button")
            ) {
                if (isLyricsBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (formState.useDemoModeForLyrics) "Demo Lyrics" else "Generate AI Lyrics")
                }
            }

            OutlinedButton(
                onClick = {
                    val effectiveTitle = formState.title.trim()
                        .ifBlank { formState.topic.trim().ifBlank { "Untitled Lyrics" } }
                    if (onSaveLyricsWithMeta != null) {
                        onSaveLyricsWithMeta(
                            effectiveTitle,
                            formState.lyrics,
                            formState.topic,
                            resolvedGenre,
                            formState.mood,
                            formState.language,
                            formState.scriptureReference,
                            formState.userInstructions,
                            formState.generationSource
                        )
                    } else {
                        onSaveLyrics(
                            effectiveTitle,
                            formState.lyrics,
                            formState.topic,
                            resolvedGenre,
                            formState.mood,
                            formState.language
                        )
                    }
                },
                enabled = formState.lyrics.isNotBlank() &&
                    (formState.title.isNotBlank() || formState.topic.isNotBlank()) &&
                    !isLyricsBusy,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("save_lyrics_button")
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Lyrics")
            }
        }

        if (lyricsState is LyricsUiState.Saved) {
            Spacer(modifier = Modifier.height(8.dp))
            AssistChip(
                onClick = onResetLyricsState,
                modifier = Modifier.testTag("lyrics_saved_chip"),
                label = { Text("Saved \"${lyricsState.title}\" to your Lyrics library") }
            )
        } else if (lyricsState is LyricsUiState.Error) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lyrics_error_banner")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = lyricsState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (lyricsState.canFallbackToDemo) {
                        OutlinedButton(
                            onClick = {
                                formState.useDemoModeForLyrics = true
                                val req = currentLyricsRequest()
                                if (onGenerateDemoFallback != null) {
                                    onGenerateDemoFallback(req)
                                } else {
                                    onGenerateLyrics(
                                        formState.topic.ifBlank { formState.title },
                                        resolvedGenre,
                                        formState.mood,
                                        formState.language
                                    )
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("lyrics_fallback_demo_button")
                        ) {
                            Text("Use Demo Mode Instead")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Write / Edit Lyrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (formState.lyricsModelLabel.isNotBlank()) {
                AssistChip(
                    onClick = {},
                    modifier = Modifier.testTag("lyrics_source_badge"),
                    label = { Text(formState.lyricsModelLabel, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = formState.lyrics,
            onValueChange = { formState.lyrics = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_lyrics_input"),
            placeholder = { Text("Write your own lyrics in English, Twi, Akan, Ga, or Ewe, or tap Generate AI Lyrics above...") },
            minLines = 8
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val shareText = buildLyricsShareText(formState)
                    clipboardManager.setText(AnnotatedString(shareText))
                    localActionFeedback = "Copied lyrics to clipboard"
                },
                enabled = formState.lyrics.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("copy_lyrics_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Lyrics")
            }

            OutlinedButton(
                onClick = {
                    val shareText = buildLyricsShareText(formState)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, formState.title.ifBlank { "Word & Light Lyrics" })
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(sendIntent, "Share Lyrics"))
                    }
                    localActionFeedback = "Prepared lyrics for sharing"
                },
                enabled = formState.lyrics.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("share_lyrics_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Lyrics")
            }
        }

        if (localActionFeedback != null) {
            Spacer(modifier = Modifier.height(6.dp))
            AssistChip(
                onClick = { localActionFeedback = null },
                modifier = Modifier.testTag("lyrics_action_feedback_chip"),
                label = { Text(localActionFeedback!!) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        BilingualCaptionCuesEditorCard(
            tagPrefix = "lyrics",
            cues = formState.captionCues,
            onCuesChange = { formState.captionCues = it },
            onAutoSyncFromLyrics = {
                val synced = BilingualGospelHelper.generateCuesFromLyrics(formState.lyrics)
                formState.captionCues = synced
                localActionFeedback = if (synced.isEmpty()) {
                    "Write or generate lyrics first to auto-sync captions"
                } else {
                    "Synced ${synced.size} English & Twi caption cues"
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = formState.isShortClip, onCheckedChange = { formState.isShortClip = it })
            Text("Short Clip (up to 30s)", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onGenerateMusic(
                    formState.title,
                    formState.lyrics,
                    resolvedGenre,
                    formState.language,
                    formState.mood,
                    formState.tempo,
                    formState.instruments,
                    formState.vocalStyle,
                    formState.isShortClip
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("custom_compose_button"),
            enabled = formState.lyrics.isNotBlank() && formState.title.isNotBlank() && resolvedGenre.isNotBlank()
        ) {
            Text(
                if (MusicRepository.IS_DEMO_MODE) "Compose Demo Track" else "Generate Music",
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showReuseLyricsDialog) {
        ReuseSavedLyricsDialog(
            savedLyrics = savedLyrics,
            savedSongs = savedSongs,
            onDismiss = { showReuseLyricsDialog = false },
            onSelectLyric = { item ->
                formState.title = item.title
                formState.lyrics = item.lyrics
                if (item.topic.isNotBlank()) formState.topic = item.topic
                if (item.scriptureReference.isNotBlank()) formState.scriptureReference = item.scriptureReference
                if (item.userInstructions.isNotBlank()) formState.userInstructions = item.userInstructions
                if (item.generationSource.isNotBlank()) formState.generationSource = item.generationSource
                if (item.sectionLanguages.isNotEmpty()) formState.sectionLanguages = item.sectionLanguages
                if (item.captionCues.isNotEmpty()) formState.captionCues = item.captionCues
                if (item.genre in STANDARD_GENRES) {
                    formState.genreSelection = item.genre
                    formState.customGenre = ""
                } else if (item.genre.isNotBlank()) {
                    formState.genreSelection = "Other"
                    formState.customGenre = item.genre
                }
                if (item.mood.isNotBlank()) formState.mood = item.mood
                if (item.language.isNotBlank()) formState.language = item.language
                showReuseLyricsDialog = false
            },
            onSelectSong = { song ->
                formState.title = song.title
                formState.lyrics = song.lyrics
                if (song.genre in STANDARD_GENRES) {
                    formState.genreSelection = song.genre
                    formState.customGenre = ""
                } else if (song.genre.isNotBlank()) {
                    formState.genreSelection = "Other"
                    formState.customGenre = song.genre
                }
                if (song.language.isNotBlank()) formState.language = song.language
                if (song.mood.isNotBlank()) formState.mood = song.mood
                if (song.tempo.isNotBlank()) formState.tempo = song.tempo
                if (song.instruments.isNotEmpty()) formState.instruments = song.instruments
                if (song.vocalStyle.isNotBlank()) formState.vocalStyle = song.vocalStyle
                showReuseLyricsDialog = false
            }
        )
    }

    if (showReuseStyleDialog) {
        ReusePreviousSongDialog(
            songs = savedSongs,
            includeLyrics = false,
            onDismiss = { showReuseStyleDialog = false },
            onSelectSong = { song ->
                if (song.genre in STANDARD_GENRES) {
                    formState.genreSelection = song.genre
                    formState.customGenre = ""
                } else if (song.genre.isNotBlank()) {
                    formState.genreSelection = "Other"
                    formState.customGenre = song.genre
                }
                if (song.language.isNotBlank()) formState.language = song.language
                if (song.mood.isNotBlank()) formState.mood = song.mood
                if (song.tempo.isNotBlank()) formState.tempo = song.tempo
                if (song.instruments.isNotEmpty()) formState.instruments = song.instruments
                if (song.vocalStyle.isNotBlank()) formState.vocalStyle = song.vocalStyle
                showReuseStyleDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicStyleSection(
    mood: String,
    onMoodChange: (String) -> Unit,
    tempo: String,
    onTempoChange: (String) -> Unit,
    selectedInstruments: List<String>,
    onToggleInstrument: (String) -> Unit,
    vocalStyle: String,
    onVocalStyleChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mood", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MOOD_OPTIONS.forEach { option ->
                FilterChip(
                    selected = mood == option,
                    onClick = { onMoodChange(option) },
                    modifier = Modifier.testTag("mood_chip_${option.lowercase()}"),
                    label = { Text(option) }
                )
            }
        }
        OutlinedTextField(
            value = mood,
            onValueChange = onMoodChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_mood_input"),
            label = { Text("Custom or Selected Mood") },
            singleLine = true
        )

        Text("Tempo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TEMPO_OPTIONS.forEach { option ->
                FilterChip(
                    selected = tempo == option,
                    onClick = { onTempoChange(option) },
                    modifier = Modifier.testTag("tempo_chip_${option.substringBefore(" ").lowercase()}"),
                    label = { Text(option) }
                )
            }
        }

        Text("Instruments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            INSTRUMENT_OPTIONS.forEach { instrument ->
                FilterChip(
                    selected = selectedInstruments.contains(instrument),
                    onClick = { onToggleInstrument(instrument) },
                    modifier = Modifier.testTag("instrument_chip_${instrument.lowercase().replace(" ", "_")}"),
                    label = { Text(instrument) }
                )
            }
        }

        Text("Vocal Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VOCAL_STYLE_OPTIONS.forEach { option ->
                FilterChip(
                    selected = vocalStyle == option,
                    onClick = { onVocalStyleChange(option) },
                    modifier = Modifier.testTag("vocal_chip_${option.lowercase().replace(" ", "_").replace("&", "and")}"),
                    label = { Text(option) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreSelector(
    selected: String,
    customGenre: String = "",
    onSelected: (String) -> Unit,
    onCustomGenreChange: (String) -> Unit = {}
) {
    Column {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            STANDARD_GENRES.forEach { g ->
                FilterChip(
                    selected = selected == g,
                    onClick = { onSelected(g) },
                    modifier = Modifier.testTag("genre_chip_${g.lowercase().replace("-", "_")}"),
                    label = { Text(g) }
                )
            }
        }
        if (selected == "Other") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customGenre,
                onValueChange = onCustomGenreChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_genre_input"),
                label = { Text("Enter other genre") },
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageSelector(selected: String, onSelected: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SUPPORTED_LANGUAGES.forEach { l ->
            FilterChip(
                selected = selected == l,
                onClick = { onSelected(l) },
                modifier = Modifier.testTag("language_chip_${BilingualGospelHelper.languageTestTagSuffix(l)}"),
                label = { Text(l) }
            )
        }
    }
}

@Composable
private fun ReuseSavedLyricsDialog(
    savedLyrics: List<Lyric>,
    savedSongs: List<Song>,
    onDismiss: () -> Unit,
    onSelectLyric: (Lyric) -> Unit,
    onSelectSong: (Song) -> Unit
) {
    val songsWithLyrics = remember(savedSongs) { savedSongs.filter { it.lyrics.isNotBlank() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reuse Saved Lyrics", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedLyrics, key = { "lyric_${it.id}" }) { item ->
                    Surface(
                        onClick = { onSelectLyric(item) },
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.title.ifBlank { "Untitled Lyrics" }, fontWeight = FontWeight.Bold)
                            Text(
                                "${item.genre.ifBlank { "General" }} • ${item.language.ifBlank { "English" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                items(songsWithLyrics, key = { "song_${it.id}" }) { song ->
                    Surface(
                        onClick = { onSelectSong(song) },
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(song.title, fontWeight = FontWeight.Bold)
                                Text(
                                    "${song.genre} • ${song.language} (From Song)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ReusePreviousSongDialog(
    songs: List<Song>,
    includeLyrics: Boolean,
    onDismiss: () -> Unit,
    onSelectSong: (Song) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reuse Previous Music Style", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    val styleDesc = listOfNotNull(
                        song.genre.takeIf { it.isNotBlank() },
                        song.language.takeIf { it.isNotBlank() },
                        song.mood.takeIf { it.isNotBlank() },
                        song.tempo.takeIf { it.isNotBlank() },
                        song.vocalStyle.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    Surface(
                        onClick = { onSelectSong(song) },
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(song.title, fontWeight = FontWeight.Bold)
                            Text(
                                styleDesc.ifBlank { song.prompt },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun buildLyricsShareText(formState: CustomModeFormState): String = buildString {
    if (formState.title.isNotBlank()) appendLine(formState.title.trim())
    if (formState.scriptureReference.isNotBlank()) {
        appendLine("Scripture Reference (Separate): ${formState.scriptureReference.trim()}")
    }
    appendLine()
    append(formState.lyrics.trim())
}.trim()
