package com.aistudio.aimusic.wltv.jqr8x.ui.mixer

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioExportFormat
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMasteringSettings
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioTrackRole
import com.aistudio.aimusic.wltv.jqr8x.data.model.MasteringPreset
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProAudioMasteringCard(
    tracks: List<AudioMixTrack>,
    settings: AudioMasteringSettings,
    selectedFormat: AudioExportFormat,
    onSelectPreset: (MasteringPreset) -> Unit,
    onUpdateSettings: (AudioMasteringSettings) -> Unit,
    onSelectFormat: (AudioExportFormat) -> Unit
) {
    val vocalCount = tracks.count { it.role == AudioTrackRole.VOCAL }
    val instrumentalCount = tracks.count { it.role == AudioTrackRole.INSTRUMENTAL }
    val balanceLabel = when {
        settings.vocalBalanceOffset > 0.15f -> "+${(settings.vocalBalanceOffset * 100).roundToInt()}% Vocal Clarity"
        settings.vocalBalanceOffset < -0.15f -> "+${(-settings.vocalBalanceOffset * 100).roundToInt()}% Instrumental Focus"
        else -> "Balanced Blend"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pro_audio_mastering_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pro Mastering & Vocal/Instrumental Balance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "On-device PCM DSP: Biquad EQ, Compressor, Noise Gate & Reverb • $vocalCount Vocal / $instrumentalCount Instrumental",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Vocal vs Instrumental Balance
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Vocal ↔ Instrumental Balance",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = balanceLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = settings.vocalBalanceOffset,
                    onValueChange = { onUpdateSettings(settings.copy(vocalBalanceOffset = it)) },
                    valueRange = -1f..1f,
                    modifier = Modifier.testTag("mixer_vocal_balance_slider")
                )
            }

            Text(
                text = "Mastering Presets",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    MasteringPreset.WARM_GOSPEL,
                    MasteringPreset.BROADCAST_CRISP,
                    MasteringPreset.LIVE_SANCTUARY,
                    MasteringPreset.FLAT_NATURAL
                ).forEach { preset ->
                    FilterChip(
                        selected = settings.preset == preset,
                        onClick = { onSelectPreset(preset) },
                        modifier = Modifier.testTag("mastering_preset_${preset.name.lowercase()}"),
                        label = { Text(preset.label) }
                    )
                }
            }

            // Effect Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Noise Reduction (75Hz HPF + Gate)", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = settings.noiseReductionEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings(
                            settings.copy(
                                enabled = true,
                                preset = MasteringPreset.CUSTOM,
                                noiseReductionEnabled = enabled
                            )
                        )
                    },
                    modifier = Modifier.testTag("mixer_noise_reduction_switch")
                )
            }
            if (settings.noiseReductionEnabled) {
                Slider(
                    value = settings.noiseReductionAmount,
                    onValueChange = {
                        onUpdateSettings(
                            settings.copy(
                                enabled = true,
                                preset = MasteringPreset.CUSTOM,
                                noiseReductionAmount = it
                            )
                        )
                    },
                    valueRange = 0f..1f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dynamic Compressor & Limiter", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = settings.compressorEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings(
                            settings.copy(
                                enabled = true,
                                preset = MasteringPreset.CUSTOM,
                                compressorEnabled = enabled
                            )
                        )
                    },
                    modifier = Modifier.testTag("mixer_compressor_switch")
                )
            }
            if (settings.compressorEnabled) {
                Slider(
                    value = settings.compressorAmount,
                    onValueChange = {
                        onUpdateSettings(
                            settings.copy(
                                enabled = true,
                                preset = MasteringPreset.CUSTOM,
                                compressorAmount = it
                            )
                        )
                    },
                    valueRange = 0f..1f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sanctuary Room Reverb", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = settings.reverbEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings(
                            settings.copy(
                                enabled = true,
                                preset = MasteringPreset.CUSTOM,
                                reverbEnabled = enabled
                            )
                        )
                    },
                    modifier = Modifier.testTag("mixer_reverb_switch")
                )
            }
            if (settings.reverbEnabled) {
                Slider(
                    value = settings.reverbMix,
                    onValueChange = {
                        onUpdateSettings(
                            settings.copy(
                                enabled = true,
                                preset = MasteringPreset.CUSTOM,
                                reverbMix = it
                            )
                        )
                    },
                    valueRange = 0.05f..0.55f
                )
            }

            // 3-Band EQ
            Text(
                text = "3-Band EQ: Bass ${formatDb(settings.eqLowGainDb)} • Mid ${formatDb(settings.eqMidGainDb)} • Air ${formatDb(settings.eqHighGainDb)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            EqSliderRow(
                label = "Bass 120Hz",
                value = settings.eqLowGainDb,
                testTag = "mixer_eq_low_slider",
                onChange = {
                    onUpdateSettings(settings.copy(enabled = true, preset = MasteringPreset.CUSTOM, eqLowGainDb = it))
                }
            )
            EqSliderRow(
                label = "Mid 1.8kHz",
                value = settings.eqMidGainDb,
                testTag = "mixer_eq_mid_slider",
                onChange = {
                    onUpdateSettings(settings.copy(enabled = true, preset = MasteringPreset.CUSTOM, eqMidGainDb = it))
                }
            )
            EqSliderRow(
                label = "Air 7.5kHz",
                value = settings.eqHighGainDb,
                testTag = "mixer_eq_high_slider",
                onChange = {
                    onUpdateSettings(settings.copy(enabled = true, preset = MasteringPreset.CUSTOM, eqHighGainDb = it))
                }
            )

            // Export Format Picker (WAV 44.1 / 48 kHz + MP3 up to 320 kbps)
            Text(
                text = "Master Export Quality",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AudioExportFormat.entries.forEach { format ->
                    FilterChip(
                        selected = selectedFormat == format,
                        onClick = { onSelectFormat(format) },
                        modifier = Modifier.testTag("mixer_export_format_${format.name.lowercase()}"),
                        label = { Text(format.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EqSliderRow(
    label: String,
    value: Float,
    testTag: String,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(84.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -9f..9f,
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
        )
    }
}

private fun formatDb(db: Float): String {
    val rounded = (db * 10f).roundToInt() / 10f
    return if (rounded >= 0f) "+${rounded}dB" else "${rounded}dB"
}
