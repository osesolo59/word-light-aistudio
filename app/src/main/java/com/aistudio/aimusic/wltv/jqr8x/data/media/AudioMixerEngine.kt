package com.aistudio.aimusic.wltv.jqr8x.data.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioExportFormat
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMasteringSettings
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.roundToInt

data class DecodedPcm(
    val stereo44kInterleaved: ShortArray,
    val sampleRate: Int = AudioMixerEngine.TARGET_SAMPLE_RATE,
    val channels: Int = AudioMixerEngine.TARGET_CHANNELS
) {
    val durationMs: Long
        get() = ((stereo44kInterleaved.size / channels).toLong() * 1000L) / sampleRate
}

data class MixedPcmResult(
    val stereo44kInterleaved: ShortArray,
    val durationMs: Long
)

data class RenderedAudioOutput(
    val filePath: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val formatLabel: String
)

sealed class AudioTrackImportResult {
    data class Success(val tracks: List<AudioMixTrack>, val skippedUnsupported: Int) : AudioTrackImportResult()
    data class Error(val message: String) : AudioTrackImportResult()
}

object AudioMixerEngine {

    const val TARGET_SAMPLE_RATE = 44_100
    const val TARGET_CHANNELS = 2
    private const val MAX_MIX_DURATION_MS = 600_000L // 10 minutes cap for safe in-memory mixing

    suspend fun importAudioTracksFromUris(
        context: Context,
        uris: List<Uri>
    ): AudioTrackImportResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext AudioTrackImportResult.Success(emptyList(), 0)

        val dir = File(context.filesDir, "mixer_inputs")
        if (!dir.exists()) dir.mkdirs()

        val imported = mutableListOf<AudioMixTrack>()
        var skipped = 0

        for (uri in uris) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            val displayName = AudioFileShareHelper.resolveAudioDisplayName(context, uri)
                ?: "track_${System.currentTimeMillis()}.mp3"

            if (!AudioFileShareHelper.isSupportedMixAudioFormat(mimeType, displayName)) {
                skipped++
                continue
            }

            val formatLabel = AudioFileShareHelper.detectAudioFormatLabel(mimeType, displayName)
            val ext = formatLabel.lowercase()
            val trackId = "trk_${UUID.randomUUID().toString().take(8)}"
            val destFile = File(dir, "${trackId}_${System.currentTimeMillis()}.$ext")

            try {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                } ?: run {
                    skipped++
                    continue
                }

                val durationMs = probeAudioDurationMs(destFile)
                    .coerceAtLeast(1_000L)

                imported.add(
                    AudioMixTrack(
                        id = trackId,
                        displayName = displayName,
                        formatLabel = formatLabel,
                        localUriString = Uri.fromFile(destFile).toString(),
                        durationMs = durationMs,
                        trimStartMs = 0L,
                        trimEndMs = durationMs,
                        volume = 1.0f,
                        isMuted = false,
                        offsetMs = 0L
                    )
                )
            } catch (_: Exception) {
                destFile.delete()
                skipped++
            }
        }

        if (imported.isEmpty() && skipped > 0) {
            AudioTrackImportResult.Error("Only MP3, WAV, and M4A audio files are supported in the mixer.")
        } else {
            AudioTrackImportResult.Success(imported, skipped)
        }
    }

    suspend fun importAudioFileToTrack(
        file: File,
        displayName: String,
        formatLabel: String = "WAV"
    ): AudioMixTrack = withContext(Dispatchers.IO) {
        val durationMs = probeAudioDurationMs(file).coerceAtLeast(1_000L)
        AudioMixTrack(
            id = "trk_${UUID.randomUUID().toString().take(8)}",
            displayName = displayName,
            formatLabel = formatLabel,
            localUriString = Uri.fromFile(file).toString(),
            durationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            volume = 1.0f,
            isMuted = false,
            offsetMs = 0L
        )
    }

    fun probeAudioDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        if (file.extension.equals("wav", ignoreCase = true)) {
            val wavDuration = probeWavDurationMs(file)
            if (wavDuration > 0L) return wavDuration
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    internal fun probeWavDurationMs(file: File): Long {
        return try {
            val bytes = file.readBytes()
            val header = parseWavHeader(bytes) ?: return 0L
            val bytesPerSample = (header.bitsPerSample / 8).coerceAtLeast(1)
            val totalFrames = header.dataLength / (bytesPerSample * header.channels).coerceAtLeast(1)
            (totalFrames.toLong() * 1000L) / header.sampleRate.coerceAtLeast(1)
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun renderPreviewWav(
        context: Context,
        tracks: List<AudioMixTrack>,
        title: String,
        sampleRate: Int = TARGET_SAMPLE_RATE,
        mastering: AudioMasteringSettings = AudioMasteringSettings()
    ): RenderedAudioOutput = withContext(Dispatchers.IO) {
        val mixed = mixTracksFromFiles(tracks, sampleRate, mastering)
        val outFile = AudioFileShareHelper.getMixedOutputFile(context, "${title}_preview", "wav")
        writeWavFile(mixed.stereo44kInterleaved, sampleRate, TARGET_CHANNELS, outFile)
        val rateLabel = if (sampleRate >= 48_000) "48 kHz" else "44.1 kHz"
        RenderedAudioOutput(
            filePath = outFile.absolutePath,
            fileSizeBytes = outFile.length(),
            durationMs = mixed.durationMs,
            formatLabel = "WAV Preview ($rateLabel)"
        )
    }

    suspend fun exportFinalMp3(
        context: Context,
        tracks: List<AudioMixTrack>,
        title: String,
        bitRateKbps: Int = 192,
        mastering: AudioMasteringSettings = AudioMasteringSettings()
    ): RenderedAudioOutput {
        val format = when (bitRateKbps) {
            320 -> AudioExportFormat.MP3_320
            256 -> AudioExportFormat.MP3_256
            else -> AudioExportFormat.MP3_192
        }
        return exportFinalAudio(context, tracks, title, format, mastering)
    }

    suspend fun exportFinalAudio(
        context: Context,
        tracks: List<AudioMixTrack>,
        title: String,
        format: AudioExportFormat,
        mastering: AudioMasteringSettings = AudioMasteringSettings()
    ): RenderedAudioOutput = withContext(Dispatchers.IO) {
        val sampleRate = format.sampleRateHz
        val mixed = mixTracksFromFiles(tracks, sampleRate, mastering)
        val outFile = AudioFileShareHelper.getMixedOutputFile(context, title, format.extension)

        if (format.extension == "wav") {
            writeWavFile(mixed.stereo44kInterleaved, sampleRate, TARGET_CHANNELS, outFile)
        } else {
            Mp3LameEncoder.encodePcm16ToMp3File(
                interleavedPcm = mixed.stereo44kInterleaved,
                sampleRate = sampleRate,
                channels = TARGET_CHANNELS,
                outputFile = outFile,
                bitRateKbps = format.bitRateKbps.coerceIn(128, 320)
            )
        }

        check(outFile.exists() && outFile.length() > 0L) {
            "${format.label} export produced an empty file."
        }
        RenderedAudioOutput(
            filePath = outFile.absolutePath,
            fileSizeBytes = outFile.length(),
            durationMs = mixed.durationMs,
            formatLabel = format.label
        )
    }

    private fun mixTracksFromFiles(
        tracks: List<AudioMixTrack>,
        targetSampleRate: Int,
        mastering: AudioMasteringSettings
    ): MixedPcmResult {
        val activeTracks = tracks.filter { !it.isMuted && it.volume > 0f }
        require(activeTracks.isNotEmpty()) { "Add at least one unmuted audio track before mixing." }

        val decodedById = mutableMapOf<String, DecodedPcm>()
        activeTracks.forEach { track ->
            val file = resolveTrackFile(track.localUriString)
                ?: error("Audio file not found for track \"${track.displayName}\".")
            decodedById[track.id] = decodeFileToStereoPcm(file, targetSampleRate)
        }
        return mixTracksToStereoPcm(
            tracks = activeTracks,
            decodedByTrackId = decodedById,
            targetSampleRate = targetSampleRate,
            mastering = mastering
        )
    }

    private fun resolveTrackFile(uriString: String): File? {
        if (uriString.isBlank()) return null
        val path = uriString.removePrefix("file://")
        val f = File(path)
        return if (f.exists() && f.length() > 0L) f else null
    }

    internal fun mixTracksToStereo44kPcm(
        tracks: List<AudioMixTrack>,
        decodedByTrackId: Map<String, DecodedPcm>
    ): MixedPcmResult = mixTracksToStereoPcm(
        tracks = tracks,
        decodedByTrackId = decodedByTrackId,
        targetSampleRate = TARGET_SAMPLE_RATE,
        mastering = AudioMasteringSettings(enabled = false)
    )

    internal fun mixTracksToStereoPcm(
        tracks: List<AudioMixTrack>,
        decodedByTrackId: Map<String, DecodedPcm>,
        targetSampleRate: Int = TARGET_SAMPLE_RATE,
        mastering: AudioMasteringSettings = AudioMasteringSettings(enabled = false)
    ): MixedPcmResult {
        data class TrackSlice(
            val pcm: ShortArray,
            val startFrameInSrc: Int,
            val frameCount: Int,
            val startFrameInMix: Int,
            val volume: Float
        )

        val slices = mutableListOf<TrackSlice>()
        var totalMixFrames = 0

        for (track in tracks) {
            if (track.isMuted || track.volume <= 0f) continue
            val decoded = decodedByTrackId[track.id] ?: continue
            val srcTotalFrames = decoded.stereo44kInterleaved.size / TARGET_CHANNELS
            if (srcTotalFrames <= 0) continue

            val trimStartFrames = ((track.trimStartMs.coerceAtLeast(0L) * targetSampleRate) / 1000L)
                .toInt()
                .coerceIn(0, srcTotalFrames - 1)
            val rawTrimEndMs = if (track.trimEndMs in (track.trimStartMs + 50L)..track.durationMs) {
                track.trimEndMs
            } else {
                decoded.durationMs
            }
            val trimEndFrames = ((rawTrimEndMs * targetSampleRate) / 1000L)
                .toInt()
                .coerceIn(trimStartFrames + 1, srcTotalFrames)

            val offsetFrames = ((track.offsetMs.coerceAtLeast(0L) * targetSampleRate) / 1000L).toInt()
            val frameCount = trimEndFrames - trimStartFrames
            val trackEndInMix = offsetFrames + frameCount
            totalMixFrames = maxOf(totalMixFrames, trackEndInMix)

            val roleMultiplier = AudioDspProcessor.computeRoleGainMultiplier(
                role = track.role,
                vocalBalanceOffset = mastering.vocalBalanceOffset
            )
            slices.add(
                TrackSlice(
                    pcm = decoded.stereo44kInterleaved,
                    startFrameInSrc = trimStartFrames,
                    frameCount = frameCount,
                    startFrameInMix = offsetFrames,
                    volume = (track.volume.coerceIn(0f, 1.5f) * roleMultiplier).coerceIn(0f, 2.2f)
                )
            )
        }

        require(slices.isNotEmpty() && totalMixFrames > 0) {
            "No audible frames remained after trimming/muting."
        }

        val maxFrames = ((MAX_MIX_DURATION_MS * targetSampleRate) / 1000L).toInt()
        val cappedFrames = totalMixFrames.coerceAtMost(maxFrames)
        val accum = FloatArray(cappedFrames * TARGET_CHANNELS)

        for (slice in slices) {
            val framesToWrite = minOf(slice.frameCount, cappedFrames - slice.startFrameInMix)
            if (framesToWrite <= 0) continue
            val vol = slice.volume
            for (f in 0 until framesToWrite) {
                val srcBase = (slice.startFrameInSrc + f) * TARGET_CHANNELS
                val dstBase = (slice.startFrameInMix + f) * TARGET_CHANNELS
                accum[dstBase] += (slice.pcm[srcBase].toFloat() / 32768f) * vol
                accum[dstBase + 1] += (slice.pcm[srcBase + 1].toFloat() / 32768f) * vol
            }
        }

        AudioDspProcessor.applyMasteringChainInPlace(
            interleavedStereo = accum,
            sampleRate = targetSampleRate,
            settings = mastering
        )

        val output = ShortArray(accum.size)
        for (i in accum.indices) {
            output[i] = (accum[i] * 32767f).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        val durationMs = (cappedFrames.toLong() * 1000L) / targetSampleRate
        return MixedPcmResult(output, durationMs)
    }

    internal fun decodeFileToStereo44kPcm(file: File): DecodedPcm =
        decodeFileToStereoPcm(file, TARGET_SAMPLE_RATE)

    internal fun decodeFileToStereoPcm(file: File, targetSampleRate: Int = TARGET_SAMPLE_RATE): DecodedPcm {
        val wavResult = if (file.extension.equals("wav", ignoreCase = true)) {
            decodeWavBytesToStereoPcm(file.readBytes(), targetSampleRate)
        } else {
            null
        }
        return wavResult ?: decodeWithMediaCodec(file, targetSampleRate)
    }

    private data class WavHeader(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Int,
        val dataLength: Int
    )

    private fun parseWavHeader(bytes: ByteArray): WavHeader? {
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        if (String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var audioFormat = 1
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 16
        var foundFmt = false

        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(pos + 4)
            val chunkDataStart = pos + 8
            if (chunkSize < 0 || chunkDataStart > bytes.size) break

            if (chunkId == "fmt " && chunkDataStart + 16 <= bytes.size) {
                audioFormat = buf.getShort(chunkDataStart).toInt() and 0xFFFF
                channels = buf.getShort(chunkDataStart + 2).toInt() and 0xFFFF
                sampleRate = buf.getInt(chunkDataStart + 4)
                bitsPerSample = buf.getShort(chunkDataStart + 14).toInt() and 0xFFFF
                foundFmt = true
            } else if (chunkId == "data" && foundFmt) {
                val safeLen = minOf(chunkSize, bytes.size - chunkDataStart).coerceAtLeast(0)
                return WavHeader(audioFormat, channels, sampleRate, bitsPerSample, chunkDataStart, safeLen)
            }
            val padded = chunkSize + (chunkSize and 1)
            pos = chunkDataStart + padded
        }
        return null
    }

    internal fun decodeWavBytesToStereo44kPcm(bytes: ByteArray): DecodedPcm? =
        decodeWavBytesToStereoPcm(bytes, TARGET_SAMPLE_RATE)

    internal fun decodeWavBytesToStereoPcm(
        bytes: ByteArray,
        targetSampleRate: Int = TARGET_SAMPLE_RATE
    ): DecodedPcm? {
        val h = parseWavHeader(bytes) ?: return null
        if (h.audioFormat != 1 && h.audioFormat != 3) return null
        if (h.channels <= 0 || h.sampleRate <= 0) return null

        val buf = ByteBuffer.wrap(bytes, h.dataOffset, h.dataLength).order(ByteOrder.LITTLE_ENDIAN)
        val rawShorts: ShortArray = when {
            h.audioFormat == 1 && h.bitsPerSample == 16 -> {
                val count = h.dataLength / 2
                ShortArray(count) { idx -> buf.getShort(h.dataOffset + idx * 2) }
            }
            h.audioFormat == 1 && h.bitsPerSample == 8 -> {
                ShortArray(h.dataLength) { idx ->
                    val unsigned = bytes[h.dataOffset + idx].toInt() and 0xFF
                    ((unsigned - 128) shl 8).toShort()
                }
            }
            h.audioFormat == 3 && h.bitsPerSample == 32 -> {
                val count = h.dataLength / 4
                ShortArray(count) { idx ->
                    val f = buf.getFloat(h.dataOffset + idx * 4).coerceIn(-1f, 1f)
                    (f * 32767f).roundToInt().toShort()
                }
            }
            else -> return null
        }

        val normalized = normalizeToStereoPcm(rawShorts, h.sampleRate, h.channels, targetSampleRate)
        return DecodedPcm(normalized, targetSampleRate, TARGET_CHANNELS)
    }

    private fun decodeWithMediaCodec(file: File, targetSampleRate: Int = TARGET_SAMPLE_RATE): DecodedPcm {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIdx = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    inputFormat = fmt
                    break
                }
            }
            val fmt = inputFormat ?: error("No audio track found in ${file.name}")
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio MIME type")
            extractor.selectTrack(audioTrackIdx)

            var outSampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                TARGET_SAMPLE_RATE
            }
            var outChannels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                TARGET_CHANNELS
            }
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(fmt, null, null, 0)
                start()
            }

            val pcmOut = ByteArrayOutputStream(65_536)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false

            while (!outputEos) {
                if (!inputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            outSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            outChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && newFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                        }
                    }
                }
            }

            val rawBytes = pcmOut.toByteArray()
            val bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
            val rawShorts = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                val count = rawBytes.size / 4
                ShortArray(count) { idx -> (bb.getFloat(idx * 4).coerceIn(-1f, 1f) * 32767f).roundToInt().toShort() }
            } else {
                val count = rawBytes.size / 2
                ShortArray(count) { idx -> bb.getShort(idx * 2) }
            }

            val stereoTarget = normalizeToStereoPcm(rawShorts, outSampleRate, outChannels, targetSampleRate)
            return DecodedPcm(stereoTarget, targetSampleRate, TARGET_CHANNELS)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    internal fun normalizeToStereo44k(
        input: ShortArray,
        sourceSampleRate: Int,
        sourceChannels: Int
    ): ShortArray = normalizeToStereoPcm(input, sourceSampleRate, sourceChannels, TARGET_SAMPLE_RATE)

    internal fun normalizeToStereoPcm(
        input: ShortArray,
        sourceSampleRate: Int,
        sourceChannels: Int,
        targetSampleRate: Int = TARGET_SAMPLE_RATE
    ): ShortArray {
        val safeChannels = sourceChannels.coerceAtLeast(1)
        val safeRate = sourceSampleRate.coerceIn(8_000, 192_000)
        val srcFrames = input.size / safeChannels
        if (srcFrames <= 0) return ShortArray(0)

        // Step 1: convert to stereo if mono or >2 channels
        val stereoSrc = if (safeChannels == 2) {
            input
        } else {
            ShortArray(srcFrames * 2).also { out ->
                for (f in 0 until srcFrames) {
                    val base = f * safeChannels
                    out[f * 2] = input[base]
                    out[f * 2 + 1] = if (safeChannels > 1) input[base + 1] else input[base]
                }
            }
        }

        if (safeRate == targetSampleRate) return stereoSrc

        // Step 2: linear resample to targetSampleRate Hz
        val targetFrames = ((srcFrames.toLong() * targetSampleRate) / safeRate.toLong())
            .toInt()
            .coerceAtLeast(1)
        val resampled = ShortArray(targetFrames * 2)
        val ratio = (srcFrames - 1).toDouble() / (targetFrames - 1).coerceAtLeast(1).toDouble()

        for (i in 0 until targetFrames) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, srcFrames - 1)
            val frac = (srcPos - idx0).toFloat()

            val l0 = stereoSrc[idx0 * 2].toFloat()
            val l1 = stereoSrc[idx1 * 2].toFloat()
            val r0 = stereoSrc[idx0 * 2 + 1].toFloat()
            val r1 = stereoSrc[idx1 * 2 + 1].toFloat()

            resampled[i * 2] = (l0 + (l1 - l0) * frac).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            resampled[i * 2 + 1] = (r0 + (r1 - r0) * frac).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return resampled
    }

    fun writeWavFile(
        interleavedPcm: ShortArray,
        sampleRate: Int,
        channels: Int,
        outFile: File
    ) {
        outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val dataSize = interleavedPcm.size * 2
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // Subchunk1Size for PCM
            putShort(1) // AudioFormat = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(16) // BitsPerSample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        val pcmBytes = ByteArray(dataSize)
        val pcmBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        interleavedPcm.forEach { pcmBuf.putShort(it) }

        FileOutputStream(outFile).use { fos ->
            fos.write(header)
            fos.write(pcmBytes)
            fos.flush()
        }
    }
}
