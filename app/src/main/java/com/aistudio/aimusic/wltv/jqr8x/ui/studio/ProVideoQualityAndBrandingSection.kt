package com.aistudio.aimusic.wltv.jqr8x.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.media.DeviceVideoCapabilities
import com.aistudio.aimusic.wltv.jqr8x.data.media.VideoCapabilityProber
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowAspectRatio
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoCodecOption
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoFrameRate
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoResolutionPreset
import com.aistudio.aimusic.wltv.jqr8x.data.model.WordLightBrandingOptions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProVideoQualityAndBrandingSection(
    tagPrefix: String,
    aspectRatio: SlideshowAspectRatio,
    resolutionPreset: VideoResolutionPreset,
    onResolutionChange: (VideoResolutionPreset) -> Unit,
    frameRate: VideoFrameRate,
    onFrameRateChange: (VideoFrameRate) -> Unit,
    videoCodec: VideoCodecOption,
    onVideoCodecChange: (VideoCodecOption) -> Unit,
    branding: WordLightBrandingOptions,
    onBrandingChange: (WordLightBrandingOptions) -> Unit,
    capabilitiesOverride: DeviceVideoCapabilities? = null
) {
    val capabilities = remember(capabilitiesOverride) {
        capabilitiesOverride ?: VideoCapabilityProber.probe()
    }
    val dims = aspectRatio.dimensionsFor(resolutionPreset)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_pro_broadcast_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, Color(0xFF4A148C).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pro Export Quality (${dims.first}×${dims.second} • ${frameRate.label})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = capabilities.summaryText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = Color(0xFF4A148C),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = resolutionPreset.shortLabel,
                        color = Color(0xFFFFD700),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Output Resolution",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VideoResolutionPreset.entries.forEach { preset ->
                    val supported = capabilities.isResolutionSupported(preset, aspectRatio, videoCodec)
                    FilterChip(
                        selected = resolutionPreset == preset,
                        enabled = supported,
                        onClick = {
                            onResolutionChange(preset)
                            if (!capabilities.isFrameRateSupported(frameRate, preset)) {
                                onFrameRateChange(VideoFrameRate.FPS_30)
                            }
                        },
                        modifier = Modifier.testTag("${tagPrefix}_resolution_${preset.name.lowercase()}"),
                        label = {
                            val label = if (!supported && preset == VideoResolutionPreset.UHD_4K) {
                                "${preset.shortLabel} (HW Unsupported)"
                            } else {
                                preset.label
                            }
                            Text(label)
                        }
                    )
                }
            }

            Text(
                text = "Frame Rate & Video Codec",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VideoFrameRate.entries.forEach { fpsOpt ->
                    val supported = capabilities.isFrameRateSupported(fpsOpt, resolutionPreset)
                    FilterChip(
                        selected = frameRate == fpsOpt,
                        enabled = supported,
                        onClick = { onFrameRateChange(fpsOpt) },
                        modifier = Modifier.testTag("${tagPrefix}_fps_${fpsOpt.fps}"),
                        label = {
                            Text(if (supported) fpsOpt.label else "${fpsOpt.label} (HW Unsupported)")
                        }
                    )
                }
                VideoCodecOption.entries.forEach { codecOpt ->
                    val supported = codecOpt == VideoCodecOption.H264 || capabilities.supportsHevc
                    FilterChip(
                        selected = videoCodec == codecOpt,
                        enabled = supported,
                        onClick = { onVideoCodecChange(codecOpt) },
                        modifier = Modifier.testTag("${tagPrefix}_codec_${codecOpt.name.lowercase()}"),
                        label = {
                            Text(if (supported) codecOpt.label else "HEVC (No HW Encoder)")
                        }
                    )
                }
            }

            // Word & Light TV Branding Controls
            Surface(
                color = Color(0xFF4A148C),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFFD700)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${branding.channelName} • ${branding.ministryName}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                    Text(
                        text = "Scripture Footer: ${branding.scriptureReference}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    BrandingToggleRow(
                        label = "Corner Watermark (${branding.channelName})",
                        checked = branding.includeLogoWatermark,
                        testTag = "${tagPrefix}_brand_logo_switch",
                        onCheckedChange = { onBrandingChange(branding.copy(includeLogoWatermark = it)) }
                    )
                    BrandingToggleRow(
                        label = "Opening Title Slate",
                        checked = branding.includeOpeningTitle,
                        testTag = "${tagPrefix}_brand_opening_switch",
                        onCheckedChange = { onBrandingChange(branding.copy(includeOpeningTitle = it)) }
                    )
                    BrandingToggleRow(
                        label = "Closing Credits & ${branding.scriptureReference}",
                        checked = branding.includeClosingCredits,
                        testTag = "${tagPrefix}_brand_closing_switch",
                        onCheckedChange = { onBrandingChange(branding.copy(includeClosingCredits = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandingToggleRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
