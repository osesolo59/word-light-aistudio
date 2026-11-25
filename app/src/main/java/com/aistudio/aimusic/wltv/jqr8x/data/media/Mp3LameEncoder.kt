package com.aistudio.aimusic.wltv.jqr8x.data.media

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Pure Java/Kotlin Android-safe MP3 encoder built directly on jump3r's `de.sciss.jump3r.mp3.Lame`
 * engine (avoiding `de.sciss.jump3r.lowlevel.LameEncoder` which depends on desktop `javax.sound`).
 */
object Mp3LameEncoder {

    private const val CHUNK_FRAMES = 4096

    fun encodePcm16ToMp3File(
        interleavedPcm: ShortArray,
        sampleRate: Int = 44_100,
        channels: Int = 2,
        outputFile: File,
        bitRateKbps: Int = 192
    ) {
        outputFile.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        FileOutputStream(outputFile).use { fos ->
            encodePcm16ToStream(
                interleavedPcm = interleavedPcm,
                sampleRate = sampleRate,
                channels = channels,
                outputStream = fos,
                bitRateKbps = bitRateKbps
            )
            fos.flush()
        }
    }

    fun encodePcm16ToMp3Bytes(
        interleavedPcm: ShortArray,
        sampleRate: Int = 44_100,
        channels: Int = 2,
        bitRateKbps: Int = 192
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        encodePcm16ToStream(
            interleavedPcm = interleavedPcm,
            sampleRate = sampleRate,
            channels = channels,
            outputStream = baos,
            bitRateKbps = bitRateKbps
        )
        return baos.toByteArray()
    }

    fun encodePcm16ToStream(
        interleavedPcm: ShortArray,
        sampleRate: Int = 44_100,
        channels: Int = 2,
        outputStream: OutputStream,
        bitRateKbps: Int = 192
    ) {
        val safeChannels = channels.coerceIn(1, 2)
        val safeSampleRate = sampleRate.coerceIn(8_000, 48_000)

        val lame = Lame()
        val ga = GainAnalysis()
        val bs = BitStream()
        val presets = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()

        lame.setModules(ga, bs, presets, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        presets.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        val gfp = lame.lame_init()
        gfp.num_channels = safeChannels
        gfp.in_samplerate = safeSampleRate
        gfp.out_samplerate = safeSampleRate
        gfp.brate = bitRateKbps.coerceIn(64, 320)
        gfp.quality = 5
        gfp.mode = if (safeChannels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        gfp.write_id3tag_automatic = false
        id3.id3tag_init(gfp)

        try {
            val initCode = lame.lame_init_params(gfp)
            check(initCode >= 0) { "MP3 encoder initialization failed (code=$initCode)" }

            val totalFrames = interleavedPcm.size / safeChannels
            val mp3BufSize = (1.25 * CHUNK_FRAMES + 8192).toInt()
            val mp3Buffer = ByteArray(mp3BufSize)
            val left = IntArray(CHUNK_FRAMES)
            val right = IntArray(CHUNK_FRAMES)

            if (totalFrames == 0) {
                // Encode 1152 samples of silence so even an empty test produces a valid MP3 frame
                val emptyCount = 1152
                val emptyLeft = IntArray(emptyCount)
                val emptyRight = IntArray(emptyCount)
                val encoded = lame.lame_encode_buffer_int(
                    gfp, emptyLeft, emptyRight, emptyCount, mp3Buffer, 0, mp3Buffer.size
                )
                if (encoded > 0) outputStream.write(mp3Buffer, 0, encoded)
            } else {
                var frameOffset = 0
                while (frameOffset < totalFrames) {
                    val framesThisChunk = minOf(CHUNK_FRAMES, totalFrames - frameOffset)
                    if (safeChannels == 2) {
                        var sampleIdx = frameOffset * 2
                        for (i in 0 until framesThisChunk) {
                            left[i] = interleavedPcm[sampleIdx].toInt() shl 16
                            right[i] = interleavedPcm[sampleIdx + 1].toInt() shl 16
                            sampleIdx += 2
                        }
                    } else {
                        for (i in 0 until framesThisChunk) {
                            val v = interleavedPcm[frameOffset + i].toInt() shl 16
                            left[i] = v
                            right[i] = v
                        }
                    }
                    val encoded = lame.lame_encode_buffer_int(
                        gfp, left, right, framesThisChunk, mp3Buffer, 0, mp3Buffer.size
                    )
                    check(encoded >= 0) { "MP3 encoding error (code=$encoded)" }
                    if (encoded > 0) {
                        outputStream.write(mp3Buffer, 0, encoded)
                    }
                    frameOffset += framesThisChunk
                }
            }

            val flushed = lame.lame_encode_flush(gfp, mp3Buffer, 0, mp3Buffer.size)
            if (flushed > 0) {
                outputStream.write(mp3Buffer, 0, flushed)
            }
        } finally {
            lame.lame_close(gfp)
        }
    }
}
