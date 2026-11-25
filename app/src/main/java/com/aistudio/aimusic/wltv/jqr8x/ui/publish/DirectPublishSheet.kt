package com.aistudio.aimusic.wltv.jqr8x.ui.publish

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PlatformPublishStatus
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishPlatform
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishPrivacy
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishVideoMetadata
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.PublishingRepository
import com.aistudio.aimusic.wltv.jqr8x.data.social.SecureExternalLinkOpener
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.BackgroundWhite
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.GoldAccent
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurpleDark
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurplePrimary
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.SurfacePurple

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DirectPublishDialog(
    initialTitle: String,
    initialBibleVerse: String = "",
    initialDescription: String = "",
    localVideoPath: String,
    aspectRatio: String = "16:9",
    codecLabel: String = "H.264",
    onDismiss: () -> Unit,
    onOpenConnectedChannels: () -> Unit,
    onFallbackShareClick: () -> Unit,
    publishingRepositoryOverride: PublishingRepository? = null
) {
    val context = LocalContext.current
    val repository = remember(publishingRepositoryOverride, context) {
        publishingRepositoryOverride ?: PublishingRepository.getInstance(context)
    }
    val sessions by repository.sessionsFlow.collectAsState()
    val publishStatuses by repository.publishStatusesFlow.collectAsState()
    val tikTokCreatorInfo by repository.tikTokCreatorInfo.collectAsState()

    LaunchedEffect(sessions[PublishPlatform.TIKTOK]) {
        if (sessions[PublishPlatform.TIKTOK] != null) {
            repository.refreshTikTokCreatorInfo()
        }
    }

    var title by remember(initialTitle) {
        mutableStateOf(initialTitle.ifBlank { "Word & Light TV Broadcast" })
    }
    var description by remember(initialDescription) {
        mutableStateOf(initialDescription)
    }
    var hashtags by remember {
        mutableStateOf("#WordAndLightTV #GospelMusic #Psalm119105")
    }
    var selectedPrivacy by remember { mutableStateOf(PublishPrivacy.PRIVATE) }
    var allowComments by remember(tikTokCreatorInfo) {
        mutableStateOf(!(tikTokCreatorInfo?.commentDisabled ?: false))
    }
    var allowDuet by remember(tikTokCreatorInfo) {
        mutableStateOf(!(tikTokCreatorInfo?.duetDisabled ?: false))
    }
    var allowStitch by remember(tikTokCreatorInfo) {
        mutableStateOf(!(tikTokCreatorInfo?.stitchDisabled ?: false))
    }
    var selectedPlatforms by remember(sessions) {
        mutableStateOf(
            PublishPlatform.entries.filter { sessions[it] != null }.toSet()
        )
    }

    val anyAuthorized = sessions.values.any { it != null }
    val isHevcWarning = codecLabel.contains("HEVC", ignoreCase = true) ||
        codecLabel.contains("265", ignoreCase = true)
    val tikTokSelected = PublishPlatform.TIKTOK in selectedPlatforms && sessions[PublishPlatform.TIKTOK] != null

    val allowedPrivacies = remember(tikTokSelected, tikTokCreatorInfo) {
        if (tikTokSelected && tikTokCreatorInfo != null) {
            tikTokCreatorInfo!!.allowedPrivacies()
        } else if (tikTokSelected) {
            listOf(PublishPrivacy.PRIVATE)
        } else {
            PublishPrivacy.entries
        }
    }

    LaunchedEffect(allowedPrivacies) {
        if (selectedPrivacy !in allowedPrivacies) {
            selectedPrivacy = allowedPrivacies.firstOrNull() ?: PublishPrivacy.PRIVATE
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("direct_publish_dialog"),
        containerColor = BackgroundWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = PurplePrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Publish Video to Channels",
                    color = PurplePrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select Authorized Targets",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary
                )

                PublishPlatform.entries.forEach { platform ->
                    val session = sessions[platform]
                    val isSelected = platform in selectedPlatforms
                    val status = publishStatuses[platform] ?: PlatformPublishStatus.Idle

                    Surface(
                        color = if (isSelected) SurfacePurple else BackgroundWhite,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) PurplePrimary else PurplePrimary.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("direct_publish_target_${platform.key}")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(platform.iconRes),
                                    contentDescription = platform.displayName,
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = platform.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PurplePrimary
                                    )
                                    Text(
                                        text = session?.effectiveDisplayLabel()
                                            ?: "${platform.officialTargetName} (Not Linked)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PurpleDark
                                    )
                                }

                                if (session != null) {
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedPlatforms = if (isSelected) {
                                                selectedPlatforms - platform
                                            } else {
                                                selectedPlatforms + platform
                                            }
                                        },
                                        label = { Text(if (isSelected) "Selected" else "Include") }
                                    )
                                } else {
                                    TextButton(
                                        onClick = onOpenConnectedChannels,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("direct_publish_authorize_${platform.key}")
                                    ) {
                                        Text("Authorize", color = PurplePrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            when (status) {
                                PlatformPublishStatus.Idle -> Unit
                                is PlatformPublishStatus.Queued -> {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        text = status.stepLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PurplePrimary
                                    )
                                }
                                is PlatformPublishStatus.Uploading -> {
                                    LinearProgressIndicator(
                                        progress = { (status.progressPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = status.stepLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PurplePrimary
                                    )
                                }
                                is PlatformPublishStatus.Processing -> {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        text = status.stepLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PurplePrimary
                                    )
                                }
                                is PlatformPublishStatus.Published -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
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
                                                tint = PurplePrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = status.visibilityNote,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PurpleDark
                                            )
                                        }
                                        if (status.remoteUrl.isNotBlank()) {
                                            TextButton(
                                                onClick = {
                                                    SecureExternalLinkOpener.openExternalUrl(context, status.remoteUrl)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                    contentDescription = "Open Published Video",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Open")
                                            }
                                        }
                                    }
                                }
                                is PlatformPublishStatus.Failed -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = status.errorMessage,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (tikTokSelected) {
                    val info = tikTokCreatorInfo
                    Surface(
                        color = SurfacePurple,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.testTag("tiktok_preflight_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (info == null || info.isSandboxRestrictedOnly()) {
                                        "TikTok Preflight: SELF_ONLY enforced for unaudited sandbox testing."
                                    } else {
                                        "TikTok Creator (${info.creatorNickname.ifBlank { info.creatorUsername }}) • Max ${info.maxVideoPostDurationSec}s"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PurpleDark
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InteractionToggle("Comments", allowComments, !(info?.commentDisabled ?: false)) {
                                    allowComments = it
                                }
                                InteractionToggle("Duet", allowDuet, !(info?.duetDisabled ?: false)) {
                                    allowDuet = it
                                }
                                InteractionToggle("Stitch", allowStitch, !(info?.stitchDisabled ?: false)) {
                                    allowStitch = it
                                }
                            }
                        }
                    }
                }

                if (isHevcWarning) {
                    Surface(
                        color = SurfacePurple,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GoldAccent),
                        modifier = Modifier.testTag("direct_publish_hevc_warning")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = PurplePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HEVC codec detected. If TikTok rejects the file, re-export using H.264 MP4 in Pro Quality settings.",
                                style = MaterialTheme.typography.labelSmall,
                                color = PurpleDark
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Video Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_publish_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Caption / Message") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hashtags,
                    onValueChange = { hashtags = it },
                    label = { Text("Hashtags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Visibility",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allowedPrivacies.forEach { privacy ->
                        FilterChip(
                            selected = selectedPrivacy == privacy,
                            onClick = { selectedPrivacy = privacy },
                            label = { Text(privacy.label) }
                        )
                    }
                }

                if (!anyAuthorized) {
                    OutlinedButton(
                        onClick = onOpenConnectedChannels,
                        border = BorderStroke(1.dp, PurplePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("direct_publish_manage_accounts_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Setup Official OAuth Channels", color = PurplePrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            if (anyAuthorized && selectedPlatforms.isNotEmpty()) {
                Button(
                    onClick = {
                        repository.enqueueDirectPublish(
                            platforms = selectedPlatforms,
                            metadata = PublishVideoMetadata(
                                localVideoPath = localVideoPath,
                                title = title,
                                description = description,
                                hashtags = hashtags,
                                bibleVerse = initialBibleVerse,
                                privacy = selectedPrivacy,
                                aspectRatio = aspectRatio,
                                codecLabel = codecLabel,
                                allowComments = allowComments,
                                allowDuet = allowDuet,
                                allowStitch = allowStitch
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = GoldAccent
                    ),
                    modifier = Modifier.testTag("direct_publish_submit_button")
                ) {
                    Text("Publish Now", fontWeight = FontWeight.Bold, color = GoldAccent)
                }
            } else {
                Button(
                    onClick = onFallbackShareClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = GoldAccent
                    ),
                    modifier = Modifier.testTag("direct_publish_fallback_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share via Device App", fontWeight = FontWeight.Bold, color = GoldAccent)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PurplePrimary)
            }
        }
    )
}

@Composable
private fun InteractionToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked && enabled,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) PurpleDark else PurpleDark.copy(alpha = 0.5f)
        )
    }
}
