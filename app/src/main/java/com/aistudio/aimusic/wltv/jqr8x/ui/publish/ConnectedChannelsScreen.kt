package com.aistudio.aimusic.wltv.jqr8x.ui.publish

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.R
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.ConnectedChannelSession
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.FacebookPageTarget
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishPlatform
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingEnvironmentConfig
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingRepository
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.YouTubeAuthOutcome
import com.aistudio.aimusic.wltv.jqr8x.data.social.SecureExternalLinkOpener
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.BackgroundWhite
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.GoldAccent
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurpleDark
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurpleLight
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurplePrimary
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.SurfacePurple
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedChannelsScreen(
    onNavigateBack: () -> Unit,
    publishingRepositoryOverride: PublishingRepository? = null,
    onOpenYouTubePublisher: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(publishingRepositoryOverride, context) {
        publishingRepositoryOverride ?: PublishingRepository.getInstance(context)
    }
    val envConfig = remember(repository) { repository.getEnvironmentConfig() }
    val sessions by repository.sessionsFlow.collectAsState()
    val feedback by repository.lastAuthFeedback.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val youtubeResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        repository.handleYouTubeResolutionResult(result.data)
    }

    var pageSelectorPages by remember { mutableStateOf<List<FacebookPageTarget>>(emptyList()) }
    var selectedPageId by remember { mutableStateOf("") }
    var showPagePicker by remember { mutableStateOf(false) }

    LaunchedEffect(feedback) {
        val msg = feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        repository.clearAuthFeedback()
    }

    val connectedCount = sessions.values.count { it != null }

    Scaffold(
        containerColor = BackgroundWhite,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.publishing_channels_title),
                        color = BackgroundWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("publishing_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BackgroundWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PurplePrimary,
                    titleContentColor = BackgroundWhite
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundWhite)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .testTag("connected_channels_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    PublishingHeroCard(connectedCount = connectedCount)
                }

                item {
                    SecurityAssuranceBanner()
                }

                items(
                    items = PublishPlatform.entries,
                    key = { it.key }
                ) { platform ->
                    val session = sessions[platform]
                    val isOAuthReady = envConfig.isPlatformOAuthReady(platform)
                    PublishPlatformCard(
                        platform = platform,
                        session = session,
                        envConfig = envConfig,
                        isOAuthReady = isOAuthReady,
                        onConnectClick = {
                            if (platform == PublishPlatform.YOUTUBE) {
                                val activity = context.findActivity()
                                if (activity == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unable to locate Activity for Google AuthorizationClient.")
                                    }
                                } else {
                                    scope.launch {
                                        when (val outcome = repository.authorizeYouTube(activity)) {
                                            is YouTubeAuthOutcome.NeedsUserResolution -> {
                                                val req = IntentSenderRequest.Builder(
                                                    outcome.pendingIntent.intentSender
                                                ).build()
                                                youtubeResolutionLauncher.launch(req)
                                            }
                                            else -> Unit
                                        }
                                    }
                                }
                            } else {
                                repository.startOAuthConnection(context, platform)
                            }
                        },
                        onDisconnectClick = {
                            repository.disconnectPlatform(platform)
                        },
                        onOpenChannelClick = {
                            SecureExternalLinkOpener.openExternalUrl(context, platform.verifiedUrl)
                        },
                        onOpenYouTubePublisher = onOpenYouTubePublisher,
                        onChooseFacebookPageClick = {
                            val pages = session?.availablePages.orEmpty()
                            if (pages.isNotEmpty()) {
                                pageSelectorPages = pages
                                selectedPageId = session?.targetPageId.orEmpty()
                                showPagePicker = true
                            }
                        }
                    )
                }
            }
        }
    }

    if (showPagePicker && pageSelectorPages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showPagePicker = false },
            containerColor = BackgroundWhite,
            title = {
                Text(
                    text = "Select Facebook Page",
                    color = PurplePrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Choose the Word & Light TV Facebook Page managed by your account:",
                        style = MaterialTheme.typography.bodySmall,
                        color = PurpleDark
                    )
                    pageSelectorPages.forEach { page ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                        ) {
                            RadioButton(
                                selected = selectedPageId == page.pageId,
                                onClick = { selectedPageId = page.pageId }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = page.pageName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PurplePrimary
                                )
                                Text(
                                    text = "Page ID: ${page.pageId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pageSelectorPages.firstOrNull { it.pageId == selectedPageId }?.let {
                            repository.selectFacebookPage(it)
                        }
                        showPagePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = GoldAccent
                    )
                ) {
                    Text("Use Selected Page", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPagePicker = false }) {
                    Text("Cancel", color = PurplePrimary)
                }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PublishingHeroCard(connectedCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("publishing_hero_card"),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, GoldAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = listOf(PurplePrimary, PurpleLight)))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = GoldAccent, shape = CircleShape) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Official OAuth 2.0 Publishing",
                                tint = PurpleDark,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "DIRECT PUBLISHING",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldAccent
                        )
                    }

                    Surface(color = GoldAccent, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            text = "$connectedCount/3 Linked",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurpleDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Word & Light TV Official Channels",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BackgroundWhite
                )

                Text(
                    text = stringResource(R.string.publishing_channels_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = BackgroundWhite.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
private fun SecurityAssuranceBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("publishing_security_banner"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePurple),
        border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Zero-Secret OAuth Security",
                tint = PurplePrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Security Guarantee: YouTube uses Google Play Services AuthorizationClient (youtube.upload only); Meta & TikTok use OAuth 2.0 + PKCE. Tokens are AES-256-GCM Keystore encrypted and never backed up.",
                style = MaterialTheme.typography.bodySmall,
                color = PurpleDark
            )
        }
    }
}

@Composable
private fun PublishPlatformCard(
    platform: PublishPlatform,
    session: ConnectedChannelSession?,
    envConfig: PublishingEnvironmentConfig,
    isOAuthReady: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onOpenChannelClick: () -> Unit,
    onOpenYouTubePublisher: () -> Unit,
    onChooseFacebookPageClick: () -> Unit
) {
    var showSetupDetails by remember { mutableStateOf(false) }
    val missingReqs = remember(platform, envConfig) { envConfig.missingConfigRequirements(platform) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("publish_platform_card_${platform.key}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (session != null) GoldAccent else PurplePrimary.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = BackgroundWhite,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, GoldAccent)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Brush.linearGradient(listOf(PurplePrimary, PurpleLight)))
                            .padding(11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(platform.iconRes),
                            contentDescription = "${platform.displayName} icon",
                            tint = GoldAccent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platform.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary
                    )
                    Text(
                        text = session?.effectiveDisplayLabel() ?: platform.officialTargetName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PurpleDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = platform.officialHandleOrLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Surface(
                    color = when {
                        session != null -> GoldAccent
                        else -> SurfacePurple
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (session != null) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = null,
                            tint = PurpleDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                session != null -> "Authorized"
                                isOAuthReady -> "OAuth Ready"
                                else -> "Setup Needed"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PurpleDark
                        )
                    }
                }
            }

            if (session != null) {
                if (platform == PublishPlatform.YOUTUBE) {
                    Button(
                        onClick = onOpenYouTubePublisher,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurplePrimary,
                            contentColor = GoldAccent
                        ),
                        border = BorderStroke(1.5.dp, GoldAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("publish_open_youtube_uploader")
                    ) {
                        Text(
                            text = "Upload Video to YouTube",
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldAccent
                        )
                    }
                }
                if (platform == PublishPlatform.FACEBOOK && session.availablePages.size > 1) {
                    OutlinedButton(
                        onClick = onChooseFacebookPageClick,
                        border = BorderStroke(1.dp, PurplePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = "Switch Facebook Page (${session.targetPageName.ifBlank { "Select" }})",
                            color = PurplePrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenChannelClick,
                        border = BorderStroke(1.dp, PurplePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Channel", color = PurplePrimary, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDisconnectClick,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("publish_disconnect_${platform.key}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Disconnect", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isOAuthReady) {
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = GoldAccent
                    ),
                    border = BorderStroke(1.dp, GoldAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("publish_connect_${platform.key}")
                ) {
                    Text(
                        text = if (platform == PublishPlatform.YOUTUBE) {
                            "Authorize YouTube via Google"
                        } else {
                            "Authorize ${platform.displayName} via OAuth 2.0"
                        },
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent
                    )
                }
                if (platform == PublishPlatform.YOUTUBE) {
                    Text(
                        text = "Requests only https://www.googleapis.com/auth/youtube.upload using Android OAuth Client.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.testTag("youtube_scope_notice")
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSetupDetails = !showSetupDetails },
                        border = BorderStroke(1.2.dp, PurplePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("publish_setup_toggle_${platform.key}")
                    ) {
                        Icon(
                            imageVector = if (showSetupDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Developer Setup", color = PurplePrimary, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenChannelClick,
                        border = BorderStroke(1.dp, GoldAccent),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("publish_open_channel_${platform.key}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open ${platform.displayName}",
                            tint = PurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showSetupDetails) {
                    Surface(
                        color = SurfacePurple,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("publish_setup_details_${platform.key}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Required Public Build Config (No Secrets in APK):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PurplePrimary
                            )
                            missingReqs.forEach { req ->
                                Text(
                                    text = "• $req",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PurpleDark
                                )
                            }
                            if (platform.requiresBrokerBackend) {
                                Text(
                                    text = "• Store ${platform.displayName} client_secret exclusively inside Cloudflare Worker secrets (/social-broker).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
