package com.aistudio.aimusic.wltv.jqr8x.data.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

object AudioFileShareHelper {

    val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a")

    val SUPPORTED_AUDIO_MIME_TYPES = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/vnd.wave",
        "audio/mp4",
        "audio/m4a",
        "audio/x-m4a",
        "audio/aac"
    )

    val PICKER_AUDIO_MIME_TYPES = arrayOf(
        "audio/mpeg",
        "audio/wav",
        "audio/x-wav",
        "audio/mp4",
        "audio/m4a",
        "audio/x-m4a",
        "audio/*"
    )

    fun isSupportedMixAudioFormat(mimeType: String?, fileNameOrPath: String?): Boolean {
        val ext = fileNameOrPath
            ?.substringAfterLast('.', "")
            ?.substringBefore('?')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (ext in SUPPORTED_AUDIO_EXTENSIONS) return true
        val mime = mimeType?.trim()?.lowercase().orEmpty()
        return mime in SUPPORTED_AUDIO_MIME_TYPES
    }

    fun detectAudioFormatLabel(mimeType: String?, displayName: String?): String {
        val ext = displayName
            ?.substringAfterLast('.', "")
            ?.substringBefore('?')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        val mime = mimeType?.trim()?.lowercase().orEmpty()
        return when {
            ext == "wav" || mime.contains("wav") -> "WAV"
            ext == "m4a" || mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "M4A"
            else -> "MP3"
        }
    }

    fun resolveAudioDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
            } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    fun getMixedOutputFile(context: Context, title: String, extension: String): File {
        val dir = File(context.filesDir, "mixed_audio")
        if (!dir.exists()) dir.mkdirs()
        val cleanTitle = sanitizeFileName(title.ifBlank { "Gospel_Mix" }).take(32)
        val suffix = UUID.randomUUID().toString().take(6)
        return File(dir, "mix_${cleanTitle}_${System.currentTimeMillis()}_$suffix.$extension")
    }

    suspend fun saveMixedMp3ToDevice(
        context: Context,
        sourceFilePath: String,
        title: String
    ): SaveToDeviceResult = saveMixedAudioToDevice(context, sourceFilePath, title)

    suspend fun saveMixedAudioToDevice(
        context: Context,
        sourceFilePath: String,
        title: String
    ): SaveToDeviceResult = withContext(Dispatchers.IO) {
        val sourceFile = File(sourceFilePath.removePrefix("file://"))
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext SaveToDeviceResult.Error("Exported audio file could not be found.")
        }

        val isWav = sourceFile.extension.equals("wav", ignoreCase = true)
        val ext = if (isWav) "wav" else "mp3"
        val mime = if (isWav) "audio/wav" else "audio/mpeg"
        val safeFileName = "${sanitizeFileName(title.ifBlank { "WordLight_Mix" })}_${System.currentTimeMillis()}.$ext"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext SaveToDeviceResult.RequiresDocumentPicker(safeFileName)
        }

        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, safeFileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/WordLightAIStudio")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext SaveToDeviceResult.Error("Unable to create audio entry in device storage.")

            resolver.openOutputStream(itemUri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: run {
                resolver.delete(itemUri, null, null)
                return@withContext SaveToDeviceResult.Error("Unable to write audio bytes to device storage.")
            }

            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            SaveToDeviceResult.SavedToMediaStore(
                uri = itemUri,
                displayPath = "Music/WordLightAIStudio/$safeFileName"
            )
        } catch (e: Exception) {
            SaveToDeviceResult.Error(e.localizedMessage ?: "Failed to save audio to device.")
        }
    }

    suspend fun copyAudioToUri(
        context: Context,
        sourceFilePath: String,
        destinationUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceFilePath.removePrefix("file://"))
            if (!sourceFile.exists()) return@withContext false
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } != null
        } catch (_: Exception) {
            false
        }
    }

    fun createShareAudioIntent(
        context: Context,
        audioFilePath: String,
        title: String
    ): Intent? {
        val file = File(audioFilePath.removePrefix("file://"))
        if (!file.exists()) return null
        val isWav = file.extension.equals("wav", ignoreCase = true)
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = if (isWav) "audio/wav" else "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Word & Light AI STUDIO Gospel Mix" })
            putExtra(
                Intent.EXTRA_TEXT,
                "Mastered on-device in Word & Light AI STUDIO (${if (isWav) "WAV" else "MP3"}): ${title.ifBlank { "English & Twi Gospel Mix" }}"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun deleteOwnedMixedAudio(context: Context, localMp3Path: String) {
        val cleanPath = localMp3Path.removePrefix("file://").trim()
        if (cleanPath.isEmpty()) return
        runCatching {
            val dir = File(context.filesDir, "mixed_audio").canonicalFile
            val target = File(cleanPath).canonicalFile
            if (target.path.startsWith(dir.path + File.separator) && target.exists()) {
                target.delete()
            }
        }
    }

    /**
     * Generates a clean, royalty-free warm worship chord pad WAV locally so users can
     * immediately test multi-track mixing, trimming, and MP3 export even before picking device recordings.
     */
    fun createBuiltInWarmPadSampleWav(
        context: Context,
        sampleTitle: String = "Worship Warm Pad (Sample WAV)",
        baseFreqHz: Double = 261.63,
        durationMs: Long = 6_000L
    ): File {
        val dir = File(context.filesDir, "mixer_inputs")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "sample_pad_${UUID.randomUUID().toString().take(8)}.wav")
        val sampleRate = 44_100
        val channels = 2
        val totalFrames = ((durationMs.coerceIn(1_000L, 15_000L) * sampleRate) / 1000L).toInt()
        val pcm = ShortArray(totalFrames * channels)
        val fifthHz = baseFreqHz * 1.4983
        val thirdHz = baseFreqHz * 1.2599
        for (frame in 0 until totalFrames) {
            val t = frame.toDouble() / sampleRate
            val env = when {
                frame < sampleRate / 10 -> frame.toDouble() / (sampleRate / 10)
                frame > totalFrames - sampleRate / 5 -> (totalFrames - frame).toDouble() / (sampleRate / 5)
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            val s = (
                0.45 * sin(2.0 * PI * baseFreqHz * t) +
                    0.30 * sin(2.0 * PI * thirdHz * t) +
                    0.25 * sin(2.0 * PI * fifthHz * t)
                ) * env * 0.40
            val q = (s * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            pcm[frame * 2] = q
            pcm[frame * 2 + 1] = q
        }
        AudioMixerEngine.writeWavFile(pcm, sampleRate, channels, file)
        return file
    }

    private fun sanitizeFileName(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "WordLight_Mix" }
}
