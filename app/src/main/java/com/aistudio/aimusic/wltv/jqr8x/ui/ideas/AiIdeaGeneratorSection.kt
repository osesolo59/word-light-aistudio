package com.aistudio.aimusic.wltv.jqr8x.ui.ideas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.demo.IdeaGeneratorEngine
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiIdeaGeneratorSection(
    uiState: IdeaGeneratorUiState,
    onSelectCategory: (String) -> Unit,
    onSelectGenre: (String) -> Unit,
    onSelectMood: (String) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onGenerateIdeas: () -> Unit,
    onGenerateMore: () -> Unit,
    onUseIdea: (CreativeIdea) -> Unit,
    onSaveIdea: (CreativeIdea) -> Unit,
    onDeleteSavedIdea: (String) -> Unit,
    onIdeaCopied: (CreativeIdea) -> Unit,
    onDismissFeedback: () -> Unit,
    onToggleDemoMode: (Boolean) -> Unit = {},
    onUseDemoFallback: () -> Unit = {},
    tagPrefix: String = "idea",
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var showOptionalFilters by remember { mutableStateOf(false) }
    var showSavedVault by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_section_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Royal Purple + Gold Header Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Idea Generator",
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "AI Idea Generator",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Surface(
                            onClick = { onToggleDemoMode(!uiState.useDemoMode) },
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("${tagPrefix}_mode_toggle")
                        ) {
                            Text(
                                text = if (uiState.useDemoMode) "FREE DEMO" else "LIVE AI • SPARK",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = if (uiState.useDemoMode) {
                            "Offline local templates enabled. Tap LIVE AI • SPARK to switch to free Firebase AI Logic."
                        } else {
                            "Powered by free Firebase AI Logic (${uiState.activeModelLabel}) on Spark, with local demo fallback."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Language Choices (English, Twi, Akan, Ga, Ewe)
                Text(
                    text = "Language",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IdeaGeneratorEngine.LANGUAGES.forEach { language ->
                        FilterChip(
                            selected = uiState.selectedLanguage == language,
                            onClick = { onSelectLanguage(language) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("${tagPrefix}_language_${language.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            label = { Text(language, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                // Creative Categories (Optional)
                Text(
                    text = "Creative Category (Optional)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IdeaGeneratorEngine.CATEGORIES.forEach { category ->
                        val catTag = category.lowercase().replace(" ", "_")
                        FilterChip(
                            selected = uiState.selectedCategory == category,
                            onClick = { onSelectCategory(category) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("${tagPrefix}_category_$catTag"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            label = { Text(category) }
                        )
                    }
                }

                // Optional Genre & Mood toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showOptionalFilters = !showOptionalFilters },
                        modifier = Modifier.testTag("${tagPrefix}_toggle_optional_filters")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val filterSummary = listOfNotNull(
                            uiState.selectedGenre,
                            uiState.selectedMood
                        ).joinToString(" • ")
                        Text(
                            text = if (filterSummary.isBlank()) {
                                "Genre & Mood (Optional)"
                            } else {
                                "Filters: $filterSummary"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showOptionalFilters) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    if (uiState.savedIdeas.isNotEmpty()) {
                        TextButton(
                            onClick = { showSavedVault = !showSavedVault },
                            modifier = Modifier.testTag("${tagPrefix}_saved_vault_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Saved (${uiState.savedIdeas.size})")
                        }
                    }
                }

                AnimatedVisibility(visible = showOptionalFilters) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Genre (Optional)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IdeaGeneratorEngine.OPTIONAL_GENRES.forEach { genre ->
                                FilterChip(
                                    selected = uiState.selectedGenre == genre,
                                    onClick = { onSelectGenre(genre) },
                                    modifier = Modifier.testTag("${tagPrefix}_genre_${genre.lowercase().replace("-", "_")}"),
                                    label = { Text(genre) }
                                )
                            }
                        }

                        Text(
                            text = "Mood (Optional)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IdeaGeneratorEngine.OPTIONAL_MOODS.forEach { mood ->
                                FilterChip(
                                    selected = uiState.selectedMood == mood,
                                    onClick = { onSelectMood(mood) },
                                    modifier = Modifier.testTag("${tagPrefix}_mood_${mood.lowercase()}"),
                                    label = { Text(mood) }
                                )
                            }
                        }
                    }
                }

                // Generate Ideas primary CTA (Zero prompt typing required)
                Button(
                    onClick = onGenerateIdeas,
                    enabled = !uiState.isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("${tagPrefix}_generate_ideas_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generating Ideas...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate Ideas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Live AI error & fallback banner
                if (uiState.errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${tagPrefix}_error_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            OutlinedButton(
                                onClick = onUseDemoFallback,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("${tagPrefix}_fallback_demo_button")
                            ) {
                                Text("Use Demo Ideas Instead")
                            }
                        }
                    }
                }

                // Feedback banner
                if (uiState.feedbackMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${tagPrefix}_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.feedbackMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            TextButton(
                                onClick = onDismissFeedback,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }

                // Saved Ideas Vault (collapsible)
                AnimatedVisibility(visible = showSavedVault && uiState.savedIdeas.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${tagPrefix}_saved_vault_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Saved Ideas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        uiState.savedIdeas.forEach { saved ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = saved.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${saved.category} • ${saved.language} • ${saved.genre}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                                        )
                                    }
                                    TextButton(onClick = { onUseIdea(saved) }) {
                                        Text("Use")
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(IdeaGeneratorEngine.formatForClipboard(saved)))
                                            onIdeaCopied(saved)
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy saved idea")
                                    }
                                    IconButton(onClick = { onDeleteSavedIdea(saved.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete saved idea")
                                    }
                                }
                            }
                        }
                    }
                }

                // 5 Generated Suggestions
                if (uiState.generatedIdeas.isNotEmpty()) {
                    Text(
                        text = "5 Suggested Ideas (${uiState.selectedLanguage})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    uiState.generatedIdeas.forEachIndexed { index, idea ->
                        IdeaSuggestionCard(
                            index = index,
                            idea = idea,
                            isSaved = uiState.savedIdeaIds.contains(idea.id),
                            isSaving = uiState.savingIdeaId == idea.id,
                            isGenerating = uiState.isGenerating,
                            tagPrefix = tagPrefix,
                            onUseIdea = { onUseIdea(idea) },
                            onGenerateMore = onGenerateMore,
                            onSaveIdea = { onSaveIdea(idea) },
                            onCopyIdea = {
                                clipboardManager.setText(AnnotatedString(IdeaGeneratorEngine.formatForClipboard(idea)))
                                onIdeaCopied(idea)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdeaSuggestionCard(
    index: Int,
    idea: CreativeIdea,
    isSaved: Boolean,
    isSaving: Boolean,
    isGenerating: Boolean,
    tagPrefix: String,
    onUseIdea: () -> Unit,
    onGenerateMore: () -> Unit,
    onSaveIdea: () -> Unit,
    onCopyIdea: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_suggestion_card_$index"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        tonalElevation = 1.dp
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
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${index + 1}. ${idea.category}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${idea.language} • ${idea.genre}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = idea.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = idea.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Mood: ${idea.mood} • Tempo: ${idea.tempo} • Vocal: ${idea.vocalStyle}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )

            if (idea.lyricsPreview.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = idea.lyricsPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else if (idea.videoPrompt.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Visual Scene: ${idea.videoPrompt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // 4 Required Action Buttons in a clean 2x2 grid (every touch target >= 48dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onUseIdea,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("${tagPrefix}_use_idea_button_$index"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Use This Idea", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onGenerateMore,
                    enabled = !isGenerating,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("${tagPrefix}_generate_more_button_$index"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generate More", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveIdea,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("${tagPrefix}_save_idea_button_$index"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSaved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSaved) "Saved" else "Save Idea",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onCopyIdea,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("${tagPrefix}_copy_idea_button_$index"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Idea", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
