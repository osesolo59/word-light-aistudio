package com.aistudio.aimusic.wltv.jqr8x.ui.ideas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository

@Composable
fun AiIdeaGeneratorScreen(
    onNavigateBack: () -> Unit,
    onUseIdea: (CreativeIdea) -> Unit,
    ideaGeneratorViewModel: IdeaGeneratorViewModel? = null,
    repositoryOverride: MusicRepository? = null
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val resolvedViewModel: IdeaGeneratorViewModel = ideaGeneratorViewModel ?: viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return IdeaGeneratorViewModel(repository) as T
            }
        }
    )
    val uiState by resolvedViewModel.uiState.collectAsState()

    BackHandler(onBack = onNavigateBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("ai_idea_generator_screen")
    ) {
        // Dedicated Royal Purple + Gold Top Header Bar
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
                            .testTag("idea_screen_back_button")
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
                            imageVector = Icons.Default.AutoAwesome,
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
                            text = "AI Idea Generator",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("idea_screen_header")
                        )
                        Text(
                            text = if (uiState.useDemoMode) {
                                "Free Demo Mode • Offline Local Templates"
                            } else {
                                "Firebase AI Logic • ${uiState.activeModelLabel}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f)
                        )
                    }
                }
                Surface(
                    onClick = { resolvedViewModel.setDemoMode(!uiState.useDemoMode) },
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("idea_screen_header_mode_pill")
                ) {
                    Text(
                        text = if (uiState.useDemoMode) "FREE DEMO" else "LIVE AI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            AiIdeaGeneratorSection(
                uiState = uiState,
                onSelectCategory = resolvedViewModel::selectCategory,
                onSelectGenre = resolvedViewModel::selectGenre,
                onSelectMood = resolvedViewModel::selectMood,
                onSelectLanguage = resolvedViewModel::selectLanguage,
                onGenerateIdeas = resolvedViewModel::generateIdeas,
                onGenerateMore = resolvedViewModel::generateMore,
                onUseIdea = onUseIdea,
                onSaveIdea = resolvedViewModel::saveIdea,
                onDeleteSavedIdea = resolvedViewModel::deleteSavedIdea,
                onIdeaCopied = resolvedViewModel::notifyCopied,
                onDismissFeedback = resolvedViewModel::clearFeedback,
                onToggleDemoMode = resolvedViewModel::setDemoMode,
                onUseDemoFallback = resolvedViewModel::generateDemoFallback,
                tagPrefix = "idea_screen"
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
