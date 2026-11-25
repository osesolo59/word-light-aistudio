package com.aistudio.aimusic.wltv.jqr8x.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioMixerEngineTest {

    private fun generateSineStereoPcm(
        freqHz: Double,
        durationMs: Long,
        amplitude: Double = 0.5
    ): ShortArray {
        val sampleRate = AudioMixerEngine.TARGET_SAMPLE_RATE
        val frames = ((durationMs * sampleRate) / 1000L).toInt()
        val out = ShortArray(frames * 2)
        for (f in 0 until frames) {
            val s = (sin(2.0 * PI * freqHz * (f.toDouble() / sampleRate)) * amplitude * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            out[f * 2] = s
            out[f * 2 + 1] = s
        }
        return out
    }

    @Test
    fun formatValidationAndDetection_supportsMp3WavAndM4aOnly() {
        assertTrue(AudioFileShareHelper.isSupportedMixAudioFormat("audio/mpeg", "choir.mp3"))
        assertTrue(AudioFileShareHelper.isSupportedMixAudioFormat("audio/wav", "pad.WAV"))
        assertTrue(AudioFileShareHelper.isSupportedMixAudioFormat("audio/mp4", "vocals.m4a"))
        assertFalse(AudioFileShareHelper.isSupportedMixAudioFormat("video/mp4", "clip.mp4"))
        assertFalse(AudioFileShareHelper.isSupportedMixAudioFormat("image/png", "cover.png"))

        assertEquals("WAV", AudioFileShareHelper.detectAudioFormatLabel("audio/x-wav", "pad.wav"))
        assertEquals("M4A", AudioFileShareHelper.detectAudioFormatLabel("audio/m4a", "voice.m4a"))
        assertEquals("MP3", AudioFileShareHelper.detectAudioFormatLabel("audio/mpeg", "beat.mp3"))
    }

    @Test
    fun writeAndDecodeWav_roundTripsPcmAndDurationAccurately() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempWav = File(context.cacheDir, "roundtrip_test.wav")
        val pcm = generateSineStereoPcm(freqHz = 440.0, durationMs = 1500L, amplitude = 0.4)
        AudioMixerEngine.writeWavFile(pcm, 44_100, 2, tempWav)

        assertTrue(tempWav.exists() && tempWav.length() > 44)
        val probedMs = AudioMixerEngine.probeWavDurationMs(tempWav)
        assertTrue("Expected ~1500ms, got $probedMs", probedMs in 1480L..1520L)

        val decoded = AudioMixerEngine.decodeWavBytesToStereo44kPcm(tempWav.readBytes())
        assertNotNull(decoded)
        assertEquals(pcm.size, decoded!!.stereo44kInterleaved.size)
        tempWav.delete()
    }

    @Test
    fun mixTracksToStereo44kPcm_appliesVolumeMuteTrimAndOffset() {
        val loudPcm = generateSineStereoPcm(freqHz = 330.0, durationMs = 2000L, amplitude = 0.4)
        val mutedPcm = generateSineStereoPcm(freqHz = 880.0, durationMs = 2000L, amplitude = 0.8)

        val tracks = listOf(
            AudioMixTrack(
                id = "t1",
                displayName = "Lead",
                formatLabel = "WAV",
                durationMs = 2000L,
                trimStartMs = 200L,
                trimEndMs = 1200L,
                volume = 0.5f,
                isMuted = false,
                offsetMs = 500L
            ),
            AudioMixTrack(
                id = "t2",
                displayName = "Muted Backing",
                formatLabel = "WAV",
                durationMs = 2000L,
                trimStartMs = 0L,
                trimEndMs = 2000L,
                volume = 1.0f,
                isMuted = true,
                offsetMs = 0L
            )
        )

        val mixed = AudioMixerEngine.mixTracksToStereo44kPcm(
            tracks = tracks,
            decodedByTrackId = mapOf(
                "t1" to DecodedPcm(loudPcm),
                "t2" to DecodedPcm(mutedPcm)
            )
        )

        // 500ms offset + 1000ms trimmed slice = ~1500ms total
        assertTrue("Expected ~1500ms, got ${mixed.durationMs}", mixed.durationMs in 1480L..1520L)

        // First 400ms (before 500ms offset) should be completely silent because t2 is muted
        val checkSilenceSamples = ((400L * AudioMixerEngine.TARGET_SAMPLE_RATE) / 1000L).toInt() * 2
        val maxPreOffset = (0 until checkSilenceSamples).maxOf { abs(mixed.stereo44kInterleaved[it].toInt()) }
        assertEquals(0, maxPreOffset)

        // Samples inside [600ms..1300ms] should have audio scaled to ~0.2 peak
        val activeStart = ((650L * AudioMixerEngine.TARGET_SAMPLE_RATE) / 1000L).toInt() * 2
        val activeEnd = ((1200L * AudioMixerEngine.TARGET_SAMPLE_RATE) / 1000L).toInt() * 2
        val maxActive = (activeStart until activeEnd).maxOf { abs(mixed.stereo44kInterleaved[it].toInt()) }
        assertTrue("Expected audible signal after offset, got $maxActive", maxActive > 2000)
    }

    @Test
    fun mp3LameEncoderAndExportFinalMp3_produceValidMp3Frames() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val padFile = AudioFileShareHelper.createBuiltInWarmPadSampleWav(
                context = context,
                sampleTitle = "Test Pad",
                durationMs = 2000L
            )
            val harmonyFile = AudioFileShareHelper.createBuiltInWarmPadSampleWav(
                context = context,
                sampleTitle = "Harmony Pad",
                baseFreqHz = 329.63,
                durationMs = 2000L
            )
            val t1 = AudioMixerEngine.importAudioFileToTrack(padFile, "Lead Pad.wav", "WAV")
            val t2 = AudioMixerEngine.importAudioFileToTrack(harmonyFile, "Harmony Pad.wav", "WAV")
                .copy(volume = 0.7f, offsetMs = 250L)

            val preview = AudioMixerEngine.renderPreviewWav(context, listOf(t1, t2), "Aseda_Preview")
            assertTrue(File(preview.filePath).exists() && preview.fileSizeBytes > 1000L)

            val mp3Out = AudioMixerEngine.exportFinalMp3(context, listOf(t1, t2), "Aseda_Master")
            val mp3File = File(mp3Out.filePath)
            assertTrue(mp3File.exists() && mp3Out.fileSizeBytes > 1000L)

            val bytes = mp3File.readBytes()
            // Verify MPEG audio frame sync header: 0xFF followed by top 3 bits 0xE0
            val hasSyncHeader = (0 until minOf(bytes.size - 1, 512)).any { idx ->
                (bytes[idx].toInt() and 0xFF) == 0xFF && (bytes[idx + 1].toInt() and 0xE0) == 0xE0
            }
            assertTrue("Expected valid MPEG MP3 frame sync header in exported file", hasSyncHeader)

            padFile.delete()
            harmonyFile.delete()
            File(preview.filePath).delete()
            mp3File.delete()
        }
    }
}
