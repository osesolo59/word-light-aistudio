package com.aistudio.aimusic.wltv.jqr8x.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.aistudio.aimusic.wltv.jqr8x.data.model.UserProfile
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingRepository
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinksRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.auth.signOut
import com.aistudio.aimusic.wltv.jqr8x.ui.connect.SocialLinksAdminConfigDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    repositoryOverride: MusicRepository? = null,
    onNavigateToConnectWithUs: () -> Unit = {},
    socialLinksRepositoryOverride: SocialLinksRepository? = null,
    onNavigateToConnectedChannels: () -> Unit = {},
    publishingRepositoryOverride: PublishingRepository? = null
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val socialLinksRepository = remember(socialLinksRepositoryOverride, context) {
        socialLinksRepositoryOverride ?: SocialLinksRepository.getInstance(context)
    }
    val publishingRepository = remember(publishingRepositoryOverride, context) {
        publishingRepositoryOverride ?: PublishingRepository.getInstance(context)
    }
    val configuredSocialLinks by socialLinksRepository.configuredLinksFlow.collectAsState()
    val publishingSessions by publishingRepository.sessionsFlow.collectAsState()
    val authorizedChannelsCount = publishingSessions.values.count { it != null }
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showSocialConfigDialog by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        repository.getUserProfile().collectLatest {
            profile = it
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "Account") {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Edit Profile",
                    onClick = onNavigateToEditProfile
                )
                SettingsItem(
                    icon = Icons.Default.Download,
                    title = "My Downloads (Demo)",
                    subtitle = "Manage your offline tracks",
                    onClick = { showDownloadsDialog = true }
                )
            }

            SettingsSection(title = "Preferences") {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Appearance",
                    subtitle = profile?.appearance ?: "System Default",
                    onClick = { showAppearanceDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "Language",
                    subtitle = profile?.language ?: "English",
                    onClick = { showLanguageDialog = true }
                )
            }

            SettingsSection(title = "Connect With Us & Publishing") {
                SettingsItem(
                    icon = Icons.Default.Public,
                    title = "Connect With Us",
                    subtitle = if (configuredSocialLinks.isEmpty()) {
                        "No active channels configured"
                    } else {
                        "${configuredSocialLinks.size} official channel(s) active"
                    },
                    modifier = Modifier.testTag("settings_connect_with_us_item"),
                    onClick = onNavigateToConnectWithUs
                )
                SettingsItem(
                    icon = Icons.Default.CloudUpload,
                    title = "Direct Video Publishing (OAuth)",
                    subtitle = "$authorizedChannelsCount/3 publishing account(s) linked (YouTube, Facebook, TikTok)",
                    modifier = Modifier.testTag("settings_direct_publishing_item"),
                    onClick = onNavigateToConnectedChannels
                )
                SettingsItem(
                    icon = Icons.Default.AdminPanelSettings,
                    title = "Configure Social Media Links",
                    subtitle = "Admin setup for YouTube, Facebook, TikTok, Instagram & more",
                    modifier = Modifier.testTag("settings_manage_social_links_item"),
                    onClick = { showSocialConfigDialog = true }
                )
            }

            SettingsSection(title = "Support & Legal") {
                val envConfig = remember(publishingRepository) { publishingRepository.getEnvironmentConfig() }
                val configuredPrivacyUrl = envConfig.validPrivacyPolicyUrl
                val configuredTermsUrl = envConfig.validTermsOfServiceUrl

                SettingsItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = configuredPrivacyUrl.ifBlank { "Tap to read (public domain pending confirmation)" },
                    modifier = Modifier.testTag("settings_privacy_policy_item"),
                    onClick = {
                        if (configuredPrivacyUrl.isNotBlank()) {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(configuredPrivacyUrl))
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                context.startActivity(intent)
                            }.onFailure {
                                infoDialogTitle = "Privacy Policy"
                                infoDialogMessage = com.aistudio.aimusic.wltv.jqr8x.data.publishing.LegalDocuments.privacyPolicyText
                            }
                        } else {
                            infoDialogTitle = "Privacy Policy"
                            infoDialogMessage = com.aistudio.aimusic.wltv.jqr8x.data.publishing.LegalDocuments.privacyPolicyText
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Description,
                    title = "Terms of Service",
                    subtitle = configuredTermsUrl.ifBlank { "Tap to read (public domain pending confirmation)" },
                    modifier = Modifier.testTag("settings_terms_of_service_item"),
                    onClick = {
                        if (configuredTermsUrl.isNotBlank()) {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(configuredTermsUrl))
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                context.startActivity(intent)
                            }.onFailure {
                                infoDialogTitle = "Terms of Service"
                                infoDialogMessage = com.aistudio.aimusic.wltv.jqr8x.data.publishing.LegalDocuments.termsOfServiceText
                            }
                        } else {
                            infoDialogTitle = "Terms of Service"
                            infoDialogMessage = com.aistudio.aimusic.wltv.jqr8x.data.publishing.LegalDocuments.termsOfServiceText
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Report,
                    title = "Report a Problem (Demo)",
                    onClick = { showReportDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.Call,
                    title = "Call Support",
                    subtitle = "Word & Light Ministry (+233245200758)",
                    onClick = {
                        val phone = "+233245200758"
                        runCatching {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        }.onFailure {
                            infoDialogTitle = "Call Support"
                            infoDialogMessage = "Ministry Support Number:\n$phone"
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Log Out")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out of Word & Light AI Studio?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        signOut(credentialManager, scope, context = context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAppearanceDialog) {
        val options = listOf("Light", "Dark", "System Default")
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("Choose Appearance") },
            text = {
                Column {
                    options.forEach { option ->
                        val selectAppearance: () -> Unit = {
                            scope.launch {
                                runCatching { repository.updateAppearance(option) }
                                    .onFailure {
                                        infoDialogTitle = "Appearance Update Failed"
                                        infoDialogMessage = it.localizedMessage ?: "Unable to update appearance preference."
                                    }
                                showAppearanceDialog = false
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable(onClick = selectAppearance)
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (profile?.appearance ?: "System Default") == option,
                                onClick = selectAppearance
                            )
                            Text(text = option, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showLanguageDialog) {
        val options = listOf("English", "Twi", "Akan", "Ga", "Ewe", "French")
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Choose Language") },
            text = {
                Column {
                    options.forEach { option ->
                        val selectLanguage: () -> Unit = {
                            scope.launch {
                                runCatching { repository.updateLanguage(option) }
                                    .onFailure {
                                        infoDialogTitle = "Language Update Failed"
                                        infoDialogMessage = it.localizedMessage ?: "Unable to update language preference."
                                    }
                                showLanguageDialog = false
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("settings_language_option_${option.lowercase()}")
                                .clickable(onClick = selectLanguage)
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (profile?.language ?: "English") == option,
                                onClick = selectLanguage
                            )
                            Text(text = option, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            title = { Text("My Downloads (Demo-Only)") },
            text = { Text("No offline tracks are downloaded. Offline audio downloading is simulated in Free Demo Mode.") },
            confirmButton = {
                TextButton(onClick = { showDownloadsDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showReportDialog) {
        var problemText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report a Problem (Demo)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Describe the issue you encountered:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = problemText,
                        onValueChange = { problemText = it },
                        label = { Text("Problem description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = problemText.isNotBlank(),
                    onClick = {
                        showReportDialog = false
                        infoDialogTitle = "Report Submitted (Demo)"
                        infoDialogMessage = "Thank you! In Free Demo Mode, problem reports are acknowledged locally. For urgent help, call Word & Light Ministry Support at +233245200758."
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSocialConfigDialog) {
        SocialLinksAdminConfigDialog(
            socialLinksRepository = socialLinksRepository,
            onDismiss = { showSocialConfigDialog = false },
            onSaved = { activeCount ->
                infoDialogTitle = "Social Links Updated"
                infoDialogMessage = "Saved $activeCount active official channel(s) for Connect With Us."
            }
        )
    }

    if (infoDialogTitle != null && infoDialogMessage != null) {
        AlertDialog(
            onDismissRequest = {
                infoDialogTitle = null
                infoDialogMessage = null
            },
            modifier = Modifier.testTag("settings_info_dialog"),
            title = { Text(infoDialogTitle!!) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(infoDialogMessage!!)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    infoDialogTitle = null
                    infoDialogMessage = null
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Column(content = content)
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
