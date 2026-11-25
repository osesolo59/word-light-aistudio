package com.aistudio.aimusic.wltv.jqr8x.ui.connect

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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.R
import com.aistudio.aimusic.wltv.jqr8x.data.social.ConfiguredSocialLink
import com.aistudio.aimusic.wltv.jqr8x.data.social.ExternalLinkOpenResult
import com.aistudio.aimusic.wltv.jqr8x.data.social.SecureExternalLinkOpener
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinksRepository
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialPlatform
import com.aistudio.aimusic.wltv.jqr8x.data.social.VERIFIED_WHATSAPP_CONTACT_NUMBER
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
fun ConnectWithUsScreen(
    onNavigateBack: () -> Unit,
    socialLinksRepositoryOverride: SocialLinksRepository? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val socialLinksRepository = remember(socialLinksRepositoryOverride, context) {
        socialLinksRepositoryOverride ?: SocialLinksRepository.getInstance(context)
    }

    val configuredLinks by socialLinksRepository.configuredLinksFlow.collectAsState()
    var showAdminDialog by remember { mutableStateOf(false) }
    var fallbackDialogTitle by remember { mutableStateOf<String?>(null) }
    var fallbackDialogUrl by remember { mutableStateOf<String?>(null) }
    var errorNotice by remember { mutableStateOf<String?>(null) }

    val openSocialLink: (ConfiguredSocialLink) -> Unit = { link ->
        when (val result = SecureExternalLinkOpener.openExternalUrl(context, link.url)) {
            is ExternalLinkOpenResult.Opened -> Unit
            is ExternalLinkOpenResult.NoAppAvailable -> {
                fallbackDialogTitle = link.platform.displayName
                fallbackDialogUrl = result.url
            }
            is ExternalLinkOpenResult.InvalidUrl -> {
                errorNotice = result.reason
            }
        }
    }

    Scaffold(
        containerColor = BackgroundWhite,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.connect_with_us_title),
                        color = BackgroundWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("connect_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BackgroundWhite
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAdminDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("connect_admin_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Configure Social Media Links",
                            tint = GoldAccent
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
            val isWideScreen = maxWidth >= 600.dp
            val rowPairs = remember(configuredLinks, isWideScreen) {
                if (isWideScreen) configuredLinks.chunked(2) else configuredLinks.map { listOf(it) }
            }

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .testTag("connect_with_us_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ConnectMinistryHeroCard(activeCount = configuredLinks.size)
                }

                if (configuredLinks.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connect_empty_state"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfacePurple),
                            border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "No social links configured",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "No Official Links Configured Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PurplePrimary
                                )
                                Text(
                                    text = "Configure verified https:// ministry social media links in Admin Settings to display them here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PurpleDark
                                )
                                Button(
                                    onClick = { showAdminDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PurplePrimary,
                                        contentColor = BackgroundWhite
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("connect_empty_configure_button")
                                ) {
                                    Text("Configure Links", color = GoldAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Official Channels",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PurplePrimary
                            )
                            Surface(
                                color = SurfacePurple,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = "${configuredLinks.size} verified",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PurplePrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    items(
                        items = rowPairs,
                        key = { pair -> pair.joinToString("_") { it.platform.key } }
                    ) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { link ->
                                SocialPlatformCard(
                                    link = link,
                                    onClick = { openSocialLink(link) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (isWideScreen && rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_admin_footer_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfacePurple),
                        border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Admin Link Management",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PurplePrimary
                                )
                                Text(
                                    text = "Add or update official YouTube, Facebook, TikTok, Instagram, WhatsApp, Telegram, X, or Website links.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PurpleDark.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = { showAdminDialog = true },
                                border = BorderStroke(1.2.dp, PurplePrimary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("connect_open_admin_footer_button")
                            ) {
                                Text("Configure", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdminDialog) {
        SocialLinksAdminConfigDialog(
            socialLinksRepository = socialLinksRepository,
            onDismiss = { showAdminDialog = false },
            onSaved = { activeCount ->
                scope.launch {
                    snackbarHostState.showSnackbar("Saved $activeCount active Connect With Us link(s).")
                }
            }
        )
    }

    if (fallbackDialogTitle != null && fallbackDialogUrl != null) {
        val url = fallbackDialogUrl!!
        AlertDialog(
            onDismissRequest = {
                fallbackDialogTitle = null
                fallbackDialogUrl = null
            },
            modifier = Modifier.testTag("connect_fallback_dialog"),
            containerColor = BackgroundWhite,
            title = {
                Text(
                    text = "Open ${fallbackDialogTitle!!}",
                    color = PurplePrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "No web browser or app handler is currently available on this device. You can copy this verified official link:",
                        style = MaterialTheme.typography.bodySmall,
                        color = PurpleDark
                    )
                    Surface(
                        color = SurfacePurple,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PurplePrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(url))
                        fallbackDialogTitle = null
                        fallbackDialogUrl = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Copied official link to clipboard.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = GoldAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Link", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    fallbackDialogTitle = null
                    fallbackDialogUrl = null
                }) {
                    Text("Close", color = PurplePrimary)
                }
            }
        )
    }

    if (errorNotice != null) {
        AlertDialog(
            onDismissRequest = { errorNotice = null },
            title = { Text("Invalid External Link", color = PurplePrimary, fontWeight = FontWeight.Bold) },
            text = { Text(errorNotice!!, color = PurpleDark) },
            confirmButton = {
                TextButton(onClick = { errorNotice = null }) {
                    Text("OK", color = PurplePrimary)
                }
            }
        )
    }
}

@Composable
private fun ConnectMinistryHeroCard(activeCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connect_header_card"),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, GoldAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(PurplePrimary, PurpleLight)
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = GoldAccent,
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Official Ministry Channels",
                                tint = PurpleDark,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "WORD & LIGHT TV",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldAccent
                        )
                    }

                    Surface(
                        color = GoldAccent,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = if (activeCount == 1) "1 Channel" else "$activeCount Channels",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurpleDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Christians Strong Foundation Ministry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BackgroundWhite
                )

                Text(
                    text = stringResource(R.string.connect_with_us_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = BackgroundWhite.copy(alpha = 0.92f)
                )

                Surface(
                    color = PurpleDark.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "Psalm 119:105 • \"Your word is a lamp for my feet, a light on my path.\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialPlatformCard(
    link: ConfiguredSocialLink,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("connect_platform_card_${link.platform.key}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(1.5.dp, PurplePrimary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = BackgroundWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, GoldAccent),
                    shadowElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PurplePrimary, PurpleLight)
                                )
                            )
                            .padding(13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(link.platform.iconRes),
                            contentDescription = "${link.platform.displayName} icon",
                            tint = GoldAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = link.platform.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (link.platform == SocialPlatform.WHATSAPP) {
                            "Word & Light TV • $VERIFIED_WHATSAPP_CONTACT_NUMBER"
                        } else {
                            link.platform.description
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (link.platform == SocialPlatform.WHATSAPP) PurpleDark else TextSecondary,
                        fontWeight = if (link.platform == SocialPlatform.WHATSAPP) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = SurfacePurple,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = link.displayHostAndPath,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PurplePrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = GoldAccent,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open ${link.platform.displayName}",
                        tint = PurpleDark,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
            }

            if (link.platform == SocialPlatform.WHATSAPP) {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = GoldAccent
                    ),
                    border = BorderStroke(1.dp, GoldAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("connect_whatsapp_chat_button")
                ) {
                    Icon(
                        painter = painterResource(link.platform.iconRes),
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CHAT WITH US",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldAccent
                    )
                }
            }
        }
    }
}
