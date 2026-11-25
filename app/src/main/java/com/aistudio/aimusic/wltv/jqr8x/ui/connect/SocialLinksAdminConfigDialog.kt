package com.aistudio.aimusic.wltv.jqr8x.ui.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.aimusic.wltv.jqr8x.R
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinkValidator
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinksRepository
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialPlatform
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialUrlValidationResult
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.BackgroundWhite
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.GoldAccent
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurpleDark
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.PurplePrimary
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.SurfacePurple
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.TextSecondary

@Composable
fun SocialLinksAdminConfigDialog(
    socialLinksRepository: SocialLinksRepository,
    onDismiss: () -> Unit,
    onSaved: (configuredCount: Int) -> Unit = {}
) {
    val initialLinks = remember(socialLinksRepository) { socialLinksRepository.getAllLinks() }
    val draftValues = remember(initialLinks) {
        mutableStateMapOf<SocialPlatform, String>().apply {
            SocialPlatform.entries.forEach { platform ->
                this[platform] = initialLinks[platform].orEmpty()
            }
        }
    }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }

    val validationErrors: Map<SocialPlatform, String?> = SocialPlatform.entries.associateWith { platform ->
        when (val result = SocialLinkValidator.validate(draftValues[platform].orEmpty())) {
            is SocialUrlValidationResult.Invalid -> result.errorMessage
            SocialUrlValidationResult.Empty,
            is SocialUrlValidationResult.Valid -> null
        }
    }
    val hasValidationError = validationErrors.values.any { it != null }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .testTag("social_admin_config_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = BackgroundWhite,
            border = BorderStroke(1.5.dp, GoldAccent),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header banner in Royal Purple + Gold
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PurplePrimary)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = GoldAccent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin Social Configuration",
                            tint = PurpleDark,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.connect_admin_config_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BackgroundWhite
                        )
                        Text(
                            text = "Word & Light AI STUDIO • Admin Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAccent
                        )
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = SurfacePurple,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.connect_admin_config_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = PurplePrimary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    if (!saveErrorMessage.isNullOrBlank()) {
                        Text(
                            text = saveErrorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    SocialPlatform.entries.forEach { platform ->
                        val currentValue = draftValues[platform].orEmpty()
                        val fieldError = validationErrors[platform]

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = PurplePrimary,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(platform.iconRes),
                                        contentDescription = platform.displayName,
                                        tint = if (currentValue.isNotBlank()) GoldAccent else BackgroundWhite,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(16.dp)
                                    )
                                }
                                Text(
                                    text = platform.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = PurplePrimary
                                )
                                if (currentValue.isBlank()) {
                                    Text(
                                        text = if (platform == SocialPlatform.WHATSAPP) {
                                            "(Always active: +233245200758)"
                                        } else {
                                            "(Hidden)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (platform == SocialPlatform.WHATSAPP) PurplePrimary else TextSecondary
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = { updated ->
                                    draftValues[platform] = updated
                                    saveErrorMessage = null
                                },
                                placeholder = {
                                    Text(
                                        text = platform.placeholderHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                },
                                singleLine = true,
                                isError = fieldError != null,
                                supportingText = fieldError?.let { errorText ->
                                    { Text(text = errorText) }
                                },
                                trailingIcon = {
                                    if (currentValue.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                draftValues[platform] = ""
                                                saveErrorMessage = null
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("social_admin_clear_${platform.key}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear ${platform.displayName} link",
                                                tint = PurplePrimary
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PurplePrimary,
                                    unfocusedBorderColor = PurplePrimary.copy(alpha = 0.35f),
                                    focusedTextColor = PurpleDark,
                                    unfocusedTextColor = PurpleDark,
                                    cursorColor = PurplePrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("social_admin_input_${platform.key}")
                            )
                        }
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfacePurple.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            SocialPlatform.entries.forEach { platform ->
                                draftValues[platform] = platform.defaultUrl
                            }
                            saveErrorMessage = null
                        },
                        border = BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("social_admin_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Default", fontWeight = FontWeight.SemiBold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("social_admin_cancel_button")
                        ) {
                            Text("Cancel", color = PurplePrimary, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            enabled = !hasValidationError,
                            onClick = {
                                val result = socialLinksRepository.updateLinks(draftValues.toMap())
                                result.onSuccess {
                                    onSaved(socialLinksRepository.getConfiguredLinks().size)
                                    onDismiss()
                                }.onFailure { error ->
                                    saveErrorMessage = error.localizedMessage ?: "Unable to save links"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurplePrimary,
                                contentColor = BackgroundWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("social_admin_save_button")
                        ) {
                            Text("Save Links", color = GoldAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
