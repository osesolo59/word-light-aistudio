package com.aistudio.aimusic.wltv.jqr8x.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioExportFormat
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMasteringSettings
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioTrackRole
import com.aistudio.aimusic.wltv.jqr8x.data.model.MasteringPreset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProAudioDspAndExportTest {

    private fun generateFloatStereoSine(
        freqHz: Double,
        sampleRate: Int,
        durationMs: Long,
        amplitude: Float = 0.5f
    ): FloatArray {
        val frames = ((durationMs * sampleRate) / 1000L).toInt()
        val buf = FloatArray(frames * 2)
        for (f in 0 until frames) {
            val s = (sin(2.0 * PI * freqHz * f.toDouble() / sampleRate) * amplitude).toFloat()
            buf[f * 2] = s
            buf[f * 2 + 1] = s
        }
        return buf
    }

    private fun rms(samples: FloatArray, startFrame: Int = 0, endFrame: Int = samples.size / 2): Float {
        var sumSq = 0.0
        var count = 0
        for (f in startFrame until endFrame) {
            val l = samples[f * 2].toDouble()
            sumSq += l * l
            count++
        }
        return if (count > 0) sqrt(sumSq / count).toFloat() else 0f
    }

    @Test
    fun exportFinalAudio_supportsWav44100And48000Headers(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceWav = AudioFileShareHelper.createBuiltInWarmPadSampleWav(
            context = context,
            sampleTitle = "Pro_Master_Pad",
            durationMs = 1500L
        )
        val track = AudioMixerEngine.importAudioFileToTrack(sourceWav, "Sanctuary_Pad.wav", "WAV")
            .copy(role = AudioTrackRole.INSTRUMENTAL)

        val out44 = AudioMixerEngine.exportFinalAudio(
            context = context,
            tracks = listOf(track),
            title = "Export_44100",
            format = AudioExportFormat.WAV_44100,
            mastering = AudioMasteringSettings.fromPreset(MasteringPreset.WARM_GOSPEL)
        )
        val file44 = File(out44.filePath)
        assertTrue(file44.exists() && file44.length() > 44)
        val bytes44 = file44.readBytes()
        val rate44 = ByteBuffer.wrap(bytes44, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(44_100, rate44)

        val out48 = AudioMixerEngine.exportFinalAudio(
            context = context,
            tracks = listOf(track),
            title = "Export_48000",
            format = AudioExportFormat.WAV_48000,
            mastering = AudioMasteringSettings.fromPreset(MasteringPreset.BROADCAST_CRISP)
        )
        val file48 = File(out48.filePath)
        assertTrue(file48.exists() && file48.length() > 44)
        val bytes48 = file48.readBytes()
        val rate48 = ByteBuffer.wrap(bytes48, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(48_000, rate48)
        assertTrue("48kHz WAV should be larger than 44.1kHz WAV", file48.length() > file44.length())

        sourceWav.delete()
        file44.delete()
        file48.delete()
    }

    @Test
    fun exportFinalAudio_supportsMp3UpTo320Kbps(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vocalWav = AudioFileShareHelper.createBuiltInWarmPadSampleWav(
            context = context,
            sampleTitle = "Lead_Vocal",
            baseFreqHz = 293.66,
            durationMs = 2000L
        )
        val backingWav = AudioFileShareHelper.createBuiltInWarmPadSampleWav(
            context = context,
            sampleTitle = "Organ_Backing",
            baseFreqHz = 220.0,
            durationMs = 2000L
        )
        val vocalTrack = AudioMixerEngine.importAudioFileToTrack(vocalWav, "Vocal.wav", "WAV")
            .copy(role = AudioTrackRole.VOCAL)
        val instTrack = AudioMixerEngine.importAudioFileToTrack(backingWav, "Organ.wav", "WAV")
            .copy(role = AudioTrackRole.INSTRUMENTAL, volume = 0.7f)

        val out192 = AudioMixerEngine.exportFinalAudio(
            context = context,
            tracks = listOf(vocalTrack, instTrack),
            title = "Grace_192",
            format = AudioExportFormat.MP3_192,
            mastering = AudioMasteringSettings.fromPreset(MasteringPreset.WARM_GOSPEL, vocalBalanceOffset = 0.5f)
        )
        val out320 = AudioMixerEngine.exportFinalAudio(
            context = context,
            tracks = listOf(vocalTrack, instTrack),
            title = "Grace_320",
            format = AudioExportFormat.MP3_320,
            mastering = AudioMasteringSettings.fromPreset(MasteringPreset.WARM_GOSPEL, vocalBalanceOffset = 0.5f)
        )

        val file192 = File(out192.filePath)
        val file320 = File(out320.filePath)
        assertTrue(file192.exists() && out192.fileSizeBytes > 1_000L)
        assertTrue(file320.exists() && out320.fileSizeBytes > out192.fileSizeBytes)

        val bytes320 = file320.readBytes()
        val hasSyncHeader = (0 until minOf(bytes320.size - 1, 512)).any { idx ->
            (bytes320[idx].toInt() and 0xFF) == 0xFF && (bytes320[idx + 1].toInt() and 0xE0) == 0xE0
        }
        assertTrue("Expected valid MPEG sync frames in 320 kbps MP3", hasSyncHeader)

        vocalWav.delete()
        backingWav.delete()
        file192.delete()
        file320.delete()
    }

    @Test
    fun dspProcessor_biquadEqCompressorNoiseGateReverbAndVocalBalance_behaveMeasurably() {
        val sampleRate = 44_100

        // 1. 3-band EQ boost increases 100 Hz RMS when bass boosted
        val lowSine = generateFloatStereoSine(freqHz = 100.0, sampleRate = sampleRate, durationMs = 400L, amplitude = 0.25f)
        val dryRms = rms(lowSine, 2000, lowSine.size / 2)
        val boosted = lowSine.copyOf()
        AudioDspProcessor.applyMasteringChainInPlace(
            interleavedStereo = boosted,
            sampleRate = sampleRate,
            settings = AudioMasteringSettings(
                enabled = true,
                preset = MasteringPreset.CUSTOM,
                eqLowGainDb = 6.0f,
                eqMidGainDb = 0f,
                eqHighGainDb = 0f,
                noiseReductionEnabled = false,
                compressorEnabled = false,
                reverbEnabled = false
            )
        )
        val wetRms = rms(boosted, 2000, boosted.size / 2)
        assertTrue("Bass EQ +6dB should raise 100Hz RMS ($wetRms > $dryRms)", wetRms > dryRms * 1.4f)

        // 2. Adaptive Noise Gate attenuates very quiet floor (~ -52 dBFS)
        val quietFloor = generateFloatStereoSine(freqHz = 1000.0, sampleRate = sampleRate, durationMs = 300L, amplitude = 0.0025f)
        val quietBefore = rms(quietFloor, 4000, quietFloor.size / 2)
        val gated = quietFloor.copyOf()
        AudioDspProcessor.applyMasteringChainInPlace(
            interleavedStereo = gated,
            sampleRate = sampleRate,
            settings = AudioMasteringSettings(
                enabled = true,
                preset = MasteringPreset.CUSTOM,
                eqLowGainDb = 0f,
                eqMidGainDb = 0f,
                eqHighGainDb = 0f,
                noiseReductionEnabled = true,
                noiseReductionAmount = 0.85f,
                compressorEnabled = false,
                reverbEnabled = false
            )
        )
        val gatedAfter = rms(gated, 4000, gated.size / 2)
        assertTrue("Noise gate should attenuate quiet hiss ($gatedAfter < $quietBefore)", gatedAfter < quietBefore * 0.6f)

        // 3. Peak limiter prevents clipping above 1.0f even with hot drive + compression
        val hotSignal = generateFloatStereoSine(freqHz = 440.0, sampleRate = sampleRate, durationMs = 250L, amplitude = 1.6f)
        AudioDspProcessor.applyMasteringChainInPlace(
            interleavedStereo = hotSignal,
            sampleRate = sampleRate,
            settings = AudioMasteringSettings(
                enabled = true,
                compressorEnabled = true,
                compressorAmount = 0.7f,
                noiseReductionEnabled = false,
                reverbEnabled = false,
                masterGainDb = 3.0f
            )
        )
        val maxPeak = hotSignal.maxOf { abs(it) }
        assertTrue("Peak limiter must constrain signal <= 1.0f, got $maxPeak", maxPeak <= 1.0f)

        // 4. Stereo Sanctuary Reverb creates an audible tail in trailing silence
        val impulseDuration = 500L
        val reverbBuf = FloatArray(((impulseDuration * sampleRate) / 1000L).toInt() * 2)
        val burst = generateFloatStereoSine(freqHz = 523.25, sampleRate = sampleRate, durationMs = 80L, amplitude = 0.6f)
        burst.copyInto(reverbBuf, destinationOffset = 0)
        AudioDspProcessor.applyMasteringChainInPlace(
            interleavedStereo = reverbBuf,
            sampleRate = sampleRate,
            settings = AudioMasteringSettings(
                enabled = true,
                eqLowGainDb = 0f,
                eqMidGainDb = 0f,
                eqHighGainDb = 0f,
                noiseReductionEnabled = false,
                compressorEnabled = false,
                reverbEnabled = true,
                reverbMix = 0.4f
            )
        )
        val tailRms = rms(reverbBuf, startFrame = 8_000, endFrame = reverbBuf.size / 2)
        assertTrue("Reverb should produce non-zero tail energy in silent region ($tailRms)", tailRms > 0.005f)

        // 5. Vocal vs Instrumental balance shifts role gain multipliers cleanly
        val vocalBoost = AudioDspProcessor.computeRoleGainMultiplier(AudioTrackRole.VOCAL, vocalBalanceOffset = 0.8f)
        val instCut = AudioDspProcessor.computeRoleGainMultiplier(AudioTrackRole.INSTRUMENTAL, vocalBalanceOffset = 0.8f)
        assertTrue(vocalBoost > 1.2f)
        assertTrue(instCut < 0.7f)
        assertEquals(1.0f, AudioDspProcessor.computeRoleGainMultiplier(AudioTrackRole.GENERAL, 0.8f), 0.001f)
    }

    @Test
    fun mixTracks_appliesVocalInstrumentalBalanceInPcmOutput() {
        val sampleRate = 44_100
        val vocalPcm = ShortArray(sampleRate * 2) { 6_000 }
        val instPcm = ShortArray(sampleRate * 2) { 6_000 }
        val vocalTrack = AudioMixTrack(id = "v1", displayName = "Lead", role = AudioTrackRole.VOCAL, durationMs = 1000L, trimEndMs = 1000L)
        val instTrack = AudioMixTrack(id = "i1", displayName = "Keys", role = AudioTrackRole.INSTRUMENTAL, durationMs = 1000L, trimEndMs = 1000L)

        val vocalOnlyMaster = AudioMixerEngine.mixTracksToStereoPcm(
            tracks = listOf(vocalTrack, instTrack),
            decodedByTrackId = mapOf(
                "v1" to DecodedPcm(vocalPcm),
                "i1" to DecodedPcm(ShortArray(sampleRate * 2) { 0 })
            ),
            targetSampleRate = sampleRate,
            mastering = AudioMasteringSettings(enabled = false, vocalBalanceOffset = 1.0f)
        )
        val instOnlyMaster = AudioMixerEngine.mixTracksToStereoPcm(
            tracks = listOf(vocalTrack, instTrack),
            decodedByTrackId = mapOf(
                "v1" to DecodedPcm(ShortArray(sampleRate * 2) { 0 }),
                "i1" to DecodedPcm(instPcm)
            ),
            targetSampleRate = sampleRate,
            mastering = AudioMasteringSettings(enabled = false, vocalBalanceOffset = 1.0f)
        )

        assertTrue(
            "Positive vocalBalanceOffset must make VOCAL louder than INSTRUMENTAL",
            vocalOnlyMaster.stereo44kInterleaved[100] > instOnlyMaster.stereo44kInterleaved[100] * 1.5
        )
    }
}
