package com.aistudio.aimusic.wltv.jqr8x.ui.create

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsRequest
import com.aistudio.aimusic.wltv.jqr8x.data.ai.FreeSparkModels
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository

private val LYRICS_GENRE_CHIPS = listOf(
    "Gospel", "Worship", "Praise", "Reggae", "Highlife", "Hip-hop", "Afrobeat", "Contemporary", "Pop", "R&B"
)

private val LYRICS_MOOD_CHIPS = listOf(
    "Uplifting", "Joyful", "Reverent", "Peaceful", "Soulful", "Energetic", "Reflective"
)

data class DisplayedLyricsResult(
    val title: String,
    val lyrics: String,
    val genre: String,
    val mood: String,
    val topic: String,
    val language: String,
    val isDemo: Boolean,
    val modelName: String,
    val scriptureReference: String,
    val captionCues: List<TimedBilingualCue> = emptyList()
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiLyricsGeneratorScreen(
    onNavigateBack: () -> Unit,
    onUseLyricsInAudioStudio: (CreativeIdea) -> Unit = {},
    repositoryOverride: MusicRepository? = null,
    lyricsViewModelOverride: LyricsViewModel? = null
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val lyricsViewModel: LyricsViewModel = lyricsViewModelOverride ?: viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return LyricsViewModel(repository) as T
            }
        }
    )

    val uiState by lyricsViewModel.uiState.collectAsState()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var topic by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("Gospel") }
    var mood by rememberSaveable { mutableStateOf("Uplifting") }
    var language by rememberSaveable { mutableStateOf("English") }
    var songTitle by rememberSaveable { mutableStateOf("") }
    var userInstructions by rememberSaveable { mutableStateOf("") }
    var scriptureReference by rememberSaveable { mutableStateOf("") }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var useDemoMode by rememberSaveable { mutableStateOf(false) }

    var displayedResult by remember { mutableStateOf<DisplayedLyricsResult?>(null) }
    var editableLyrics by rememberSaveable { mutableStateOf("") }
    var isEditingResult by rememberSaveable { mutableStateOf(false) }
    var showCaptionsEditor by rememberSaveable { mutableStateOf(false) }
    var isSavingRequest by remember { mutableStateOf(false) }
    var savedBannerTitle by remember { mutableStateOf<String?>(null) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LyricsUiState.Success -> {
                isSavingRequest = false
                savedBannerTitle = null
                isEditingResult = false
                editableLyrics = state.lyrics
                val resolvedTitle = state.title.ifBlank {
                    songTitle.trim().ifBlank { topic.trim().ifBlank { "Untitled Song" } }
                }
                val resolvedCues = state.captionCues.ifEmpty {
                    BilingualGospelHelper.generateCuesFromLyrics(state.lyrics)
                }
                displayedResult = DisplayedLyricsResult(
                    title = resolvedTitle,
                    lyrics = state.lyrics,
                    genre = genre.trim().ifBlank { "Gospel" },
                    mood = mood.trim().ifBlank { "Uplifting" },
                    topic = topic.trim(),
                    language = language,
                    isDemo = state.isDemo,
                    modelName = state.modelName,
                    scriptureReference = state.scriptureReference,
                    captionCues = resolvedCues
                )
            }
            is LyricsUiState.Saved -> {
                isSavingRequest = false
                savedBannerTitle = state.title
            }
            is LyricsUiState.Error -> {
                isSavingRequest = false
            }
            else -> Unit
        }
    }

    fun buildRequest(): AiLyricsRequest = AiLyricsRequest(
        title = songTitle.trim(),
        topic = topic.trim(),
        genre = genre.trim().ifBlank { "Gospel" },
        mood = mood.trim().ifBlank { "Uplifting" },
        language = language,
        sectionLanguages = if (BilingualGospelHelper.isMixedEnglishTwi(language)) {
            BilingualGospelHelper.defaultSectionLanguages(language)
        } else {
            emptyMap()
        },
        scriptureReference = scriptureReference.trim(),
        userInstructions = userInstructions.trim()
    )

    BackHandler(onBack = onNavigateBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("ai_lyrics_generator_screen")
    ) {
        // Royal Purple + Gold Top Bar matching AI Studio visual language
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lyrics_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Lyrics Generator",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("lyrics_screen_header")
                        )
                        Text(
                            text = if (useDemoMode) {
                                "Free Demo Mode • Offline Local Templates"
                            } else {
                                "Gemini API • ${FreeSparkModels.DEFAULT_FREE_MODEL}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                        )
                    }
                }
                Surface(
                    onClick = { useDemoMode = !useDemoMode },
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("lyrics_screen_mode_pill")
                ) {
                    Text(
                        text = if (useDemoMode) "FREE DEMO" else "GEMINI AI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Input Parameters Card: Topic, Genre, Mood + Optional Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Songwriting Prompt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 1. Topic Field
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Topic") },
                        placeholder = { Text("e.g., Walking in grace, hope in the storm, Sunday thanksgiving") },
                        supportingText = { Text("What is your song about?") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lyrics_gen_topic_input")
                    )

                    // 2. Genre Field + Quick Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Genre",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LYRICS_GENRE_CHIPS.forEach { chipGenre ->
                                FilterChip(
                                    selected = genre.equals(chipGenre, ignoreCase = true),
                                    onClick = { genre = chipGenre },
                                    modifier = Modifier.testTag(
                                        "lyrics_gen_genre_chip_${chipGenre.lowercase().replace("-", "_").replace("&", "and")}"
                                    ),
                                    label = { Text(chipGenre) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text("Genre") },
                            placeholder = { Text("Enter or select a genre (e.g., Gospel, Worship, Pop)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lyrics_gen_genre_input")
                        )
                    }

                    // 3. Mood Field + Quick Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Mood",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LYRICS_MOOD_CHIPS.forEach { chipMood ->
                                FilterChip(
                                    selected = mood.equals(chipMood, ignoreCase = true),
                                    onClick = { mood = chipMood },
                                    modifier = Modifier.testTag("lyrics_gen_mood_chip_${chipMood.lowercase()}"),
                                    label = { Text(chipMood) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = mood,
                            onValueChange = { mood = it },
                            label = { Text("Mood") },
                            placeholder = { Text("Enter or select a mood (e.g., Uplifting, Reverent, Joyful)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lyrics_gen_mood_input")
                        )
                    }

                    // Optional Song Details Toggle (Language, Title, Scripture Theme, Instructions)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Language: $language",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showAdvancedOptions = !showAdvancedOptions },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("lyrics_gen_advanced_toggle")
                        ) {
                            Text(if (showAdvancedOptions) "Hide Extra Options" else "Language & Title Options")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAdvancedOptions) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Language",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            LanguageSelector(
                                selected = language,
                                onSelected = { language = it }
                            )
                            OutlinedTextField(
                                value = songTitle,
                                onValueChange = { songTitle = it },
                                label = { Text("Preferred Title (Optional)") },
                                placeholder = { Text("Leave blank to let Gemini suggest a title") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("lyrics_gen_title_input")
                            )
                            OutlinedTextField(
                                value = scriptureReference,
                                onValueChange = { scriptureReference = it },
                                label = { Text("Scripture Theme Reference (Optional)") },
                                placeholder = { Text("e.g., Psalm 23:1 (inspiration only)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("lyrics_gen_scripture_input")
                            )
                            OutlinedTextField(
                                value = userInstructions,
                                onValueChange = { userInstructions = it },
                                label = { Text("Songwriter Notes (Optional)") },
                                placeholder = { Text("e.g., Include a call-and-response chorus") },
                                minLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("lyrics_gen_instructions_input")
                            )
                        }
                    }

                    val isGenerating = (uiState is LyricsUiState.Loading || uiState is LyricsUiState.Streaming) && !isSavingRequest
                    Button(
                        onClick = {
                            savedBannerTitle = null
                            actionFeedback = null
                            isSavingRequest = false
                            lyricsViewModel.generateLyrics(
                                request = buildRequest(),
                                useDemoMode = useDemoMode
                            )
                        },
                        enabled = topic.isNotBlank() &&
                            genre.isNotBlank() &&
                            mood.isNotBlank() &&
                            !isGenerating,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("lyrics_gen_submit_button")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (useDemoMode) "Building Demo Lyrics..." else "Generating with Gemini...",
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (useDemoMode) "Generate Demo Lyrics" else "Generate Lyrics with Gemini",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Results Display Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lyrics_gen_results_area"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Generated Lyrics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (displayedResult != null) {
                        Surface(
                            color = if (displayedResult!!.isDemo) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                            },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = if (displayedResult!!.isDemo) {
                                    "Demo Template"
                                } else {
                                    "Gemini • ${displayedResult!!.modelName}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("lyrics_gen_result_badge")
                            )
                        }
                    }
                }

                // Error Banner (with Retry + Demo Fallback)
                val errorState = uiState as? LyricsUiState.Error
                if (errorState != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lyrics_gen_error_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = errorState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        isSavingRequest = false
                                        lyricsViewModel.generateLyrics(
                                            request = buildRequest(),
                                            useDemoMode = useDemoMode
                                        )
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_gen_retry_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry")
                                }
                                if (errorState.canFallbackToDemo) {
                                    Button(
                                        onClick = {
                                            useDemoMode = true
                                            lyricsViewModel.generateDemoFallback(buildRequest())
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("lyrics_gen_fallback_demo_button")
                                    ) {
                                        Text("Use Demo Template")
                                    }
                                }
                            }
                        }
                    }
                }

                // Loading Card
                if (uiState is LyricsUiState.Loading && !isSavingRequest) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lyrics_gen_loading_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Composing verses, chorus, and bridge...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Live Streaming Card
                val streamingState = uiState as? LyricsUiState.Streaming
                if (streamingState != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lyrics_gen_streaming_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Streaming live from ${streamingState.modelName}...",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("lyrics_gen_streaming_status")
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            FormattedLyricsDisplay(
                                lyricsText = streamingState.partialLyrics,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("lyrics_gen_streaming_body")
                            )
                        }
                    }
                }

                val currentResult = displayedResult
                if (currentResult != null && streamingState == null) {
                    val activeBody = if (isEditingResult) editableLyrics else currentResult.lyrics

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lyrics_gen_result_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentResult.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("lyrics_gen_result_title")
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${currentResult.genre} • ${currentResult.mood} • ${currentResult.language}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isEditingResult) {
                                            displayedResult = currentResult.copy(lyrics = editableLyrics)
                                        } else {
                                            editableLyrics = currentResult.lyrics
                                        }
                                        isEditingResult = !isEditingResult
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_gen_edit_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isEditingResult) Icons.Default.Visibility else Icons.Default.Edit,
                                        contentDescription = if (isEditingResult) "Preview formatted lyrics" else "Edit lyrics",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (currentResult.scriptureReference.isNotBlank()) {
                                Text(
                                    text = "Theme Reference: ${currentResult.scriptureReference}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))

                            if (isEditingResult) {
                                OutlinedTextField(
                                    value = editableLyrics,
                                    onValueChange = {
                                        editableLyrics = it
                                        displayedResult = currentResult.copy(lyrics = it)
                                    },
                                    label = { Text("Edit Generated Lyrics") },
                                    minLines = 10,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("lyrics_gen_result_editor")
                                )
                            } else {
                                SelectionContainer {
                                    FormattedLyricsDisplay(
                                        lyricsText = activeBody,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("lyrics_gen_result_body")
                                    )
                                }
                            }

                            if (savedBannerTitle != null) {
                                AssistChip(
                                    onClick = { savedBannerTitle = null },
                                    modifier = Modifier.testTag("lyrics_gen_saved_chip"),
                                    label = { Text("Saved \"$savedBannerTitle\" to My Projects") }
                                )
                            }

                            if (actionFeedback != null) {
                                AssistChip(
                                    onClick = { actionFeedback = null },
                                    modifier = Modifier.testTag("lyrics_gen_feedback_chip"),
                                    label = { Text(actionFeedback!!) }
                                )
                            }

                            // Bilingual English & Twi Caption Sync & Preview Toggle
                            Surface(
                                onClick = { showCaptionsEditor = !showCaptionsEditor },
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("lyrics_gen_captions_toggle")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Subtitles,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Bilingual Captions (${currentResult.captionCues.size} cues)",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (showCaptionsEditor) {
                                                    "Tap to collapse timed English & Twi preview"
                                                } else {
                                                    "Tap to sync & preview English / Twi timed cues"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (showCaptionsEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showCaptionsEditor) "Collapse captions" else "Expand captions",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showCaptionsEditor) {
                                BilingualCaptionCuesEditorCard(
                                    tagPrefix = "lyrics_gen",
                                    cues = currentResult.captionCues,
                                    onCuesChange = { updatedCues ->
                                        displayedResult = currentResult.copy(captionCues = updatedCues)
                                    },
                                    onAutoSyncFromLyrics = {
                                        val synced = BilingualGospelHelper.generateCuesFromLyrics(activeBody)
                                        displayedResult = currentResult.copy(captionCues = synced)
                                        actionFeedback = if (synced.isEmpty()) {
                                            "Add structured lyric lines first to auto-sync captions"
                                        } else {
                                            "Synced ${synced.size} English & Twi caption cues"
                                        }
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val fullText = buildString {
                                            appendLine(currentResult.title)
                                            appendLine("${currentResult.genre} • ${currentResult.mood}")
                                            appendLine()
                                            append(activeBody)
                                        }.trim()
                                        clipboardManager.setText(AnnotatedString(fullText))
                                        actionFeedback = "Copied lyrics to clipboard"
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_gen_copy_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy")
                                }

                                OutlinedButton(
                                    onClick = {
                                        val shareText = "${currentResult.title}\n\n$activeBody"
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, currentResult.title)
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Lyrics"))
                                        }
                                        actionFeedback = "Prepared lyrics for sharing"
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_gen_share_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isSavingRequest = true
                                        lyricsViewModel.saveLyrics(
                                            title = currentResult.title,
                                            lyrics = activeBody,
                                            topic = currentResult.topic.ifBlank { currentResult.title },
                                            genre = currentResult.genre,
                                            mood = currentResult.mood,
                                            language = currentResult.language,
                                            scriptureReference = currentResult.scriptureReference,
                                            userInstructions = userInstructions.trim(),
                                            generationSource = if (currentResult.isDemo) "DEMO" else "AI_GEMINI",
                                            sectionLanguages = if (BilingualGospelHelper.isMixedEnglishTwi(currentResult.language)) {
                                                BilingualGospelHelper.defaultSectionLanguages(currentResult.language)
                                            } else {
                                                emptyMap()
                                            },
                                            captionCues = currentResult.captionCues
                                        )
                                    },
                                    enabled = activeBody.isNotBlank() && !isSavingRequest,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_gen_save_button")
                                ) {
                                    if (isSavingRequest) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Saving...")
                                    } else {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save")
                                    }
                                }

                                Button(
                                    onClick = {
                                        onUseLyricsInAudioStudio(
                                            CreativeIdea(
                                                id = "lyrics_gen_${System.currentTimeMillis()}",
                                                category = "Lyrics Ideas",
                                                title = currentResult.title,
                                                summary = currentResult.topic,
                                                topic = currentResult.topic.ifBlank { currentResult.title },
                                                genre = currentResult.genre,
                                                mood = currentResult.mood,
                                                language = currentResult.language,
                                                lyricsPreview = activeBody,
                                                bibleVerse = currentResult.scriptureReference,
                                                targetStudio = "LYRICS",
                                                isDemo = currentResult.isDemo
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("lyrics_gen_use_in_studio_button")
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Use in Studio")
                                }
                            }
                        }
                    }
                } else if (uiState !is LyricsUiState.Loading && uiState !is LyricsUiState.Streaming) {
                    // Empty state before first generation
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lyrics_gen_empty_state")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lyrics,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Ready to Write Your Song",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Enter a topic, choose a genre and mood above, then tap Generate Lyrics with Gemini to see structured verses, chorus, and bridge here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormattedLyricsDisplay(
    lyricsText: String,
    modifier: Modifier = Modifier
) {
    val sectionColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onBackground
    val annotated = remember(lyricsText, sectionColor, bodyColor) {
        buildAnnotatedString {
            val lines = lyricsText.lines()
            lines.forEachIndexed { index, rawLine ->
                val trimmed = rawLine.trim()
                val isSectionHeader = (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
                    trimmed.startsWith("Title:", ignoreCase = true)
                if (isSectionHeader) {
                    withStyle(
                        SpanStyle(
                            color = sectionColor,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(rawLine)
                    }
                } else {
                    withStyle(SpanStyle(color = bodyColor)) {
                        append(rawLine)
                    }
                }
                if (index < lines.lastIndex) {
                    append('\n')
                }
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
    )
}
