package com.aistudio.aimusic.wltv.jqr8x.data.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.aistudio.aimusic.wltv.jqr8x.data.model.BilingualCaptionMode
import com.aistudio.aimusic.wltv.jqr8x.data.model.PhotoSlideshowDraft
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowAspectRatio
import com.aistudio.aimusic.wltv.jqr8x.data.model.SlideshowTransition
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.model.UploadedPhotoItem
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoCodecOption
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoFrameRate
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoResolutionPreset
import com.aistudio.aimusic.wltv.jqr8x.data.model.WordLightBrandingOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class PhotoImportResult {
    data class Success(val imported: List<UploadedPhotoItem>, val skippedUnsupported: Int) : PhotoImportResult()
    data class Error(val message: String) : PhotoImportResult()
}

sealed class SaveToDeviceResult {
    data class SavedToMediaStore(val uri: Uri, val displayPath: String) : SaveToDeviceResult()
    data class RequiresDocumentPicker(val suggestedFileName: String) : SaveToDeviceResult()
    data class Error(val message: String) : SaveToDeviceResult()
}

object StudioMediaHelper {

    val SUPPORTED_IMAGE_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    )

    val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    private const val MAX_IMPORTED_IMAGE_DIMENSION = 3840

    fun isSupportedImageFormat(mimeType: String?, fileNameOrPath: String?): Boolean {
        val normalizedMime = mimeType?.trim()?.lowercase().orEmpty()
        if (normalizedMime in SUPPORTED_IMAGE_MIME_TYPES) return true
        val ext = fileNameOrPath
            ?.substringAfterLast('.', "")
            ?.substringBefore('?')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        return ext in SUPPORTED_IMAGE_EXTENSIONS
    }

    suspend fun importPhotosFromUris(
        context: Context,
        uris: List<Uri>,
        studioType: String
    ): PhotoImportResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext PhotoImportResult.Success(emptyList(), 0)
        }
        val photosDir = File(context.filesDir, "studio_photos/${sanitizeSegment(studioType)}")
        if (!photosDir.exists()) photosDir.mkdirs()

        val imported = mutableListOf<UploadedPhotoItem>()
        var skipped = 0

        for (uri in uris) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
                ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "image/${it.lowercase()}" }
            val displayName = resolveDisplayName(context, uri) ?: "photo_${System.currentTimeMillis()}"

            if (!isSupportedImageFormat(mimeType, displayName)) {
                skipped++
                continue
            }

            try {
                val safeExt = when {
                    mimeType?.contains("png", ignoreCase = true) == true ||
                        displayName.endsWith(".png", ignoreCase = true) -> "png"
                    mimeType?.contains("webp", ignoreCase = true) == true ||
                        displayName.endsWith(".webp", ignoreCase = true) -> "webp"
                    else -> "jpg"
                }
                val id = UUID.randomUUID().toString().take(12)
                val destFile = File(photosDir, "photo_${id}.$safeExt")
                val normalizedBitmap = decodeOrientedScaledBitmap(context, uri, MAX_IMPORTED_IMAGE_DIMENSION)
                if (normalizedBitmap != null) {
                    val format = when (safeExt) {
                        "png" -> Bitmap.CompressFormat.PNG
                        "webp" -> @Suppress("DEPRECATION") if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            Bitmap.CompressFormat.WEBP
                        }
                        else -> Bitmap.CompressFormat.JPEG
                    }
                    FileOutputStream(destFile).use { out ->
                        normalizedBitmap.compress(format, 95, out)
                    }
                    normalizedBitmap.recycle()
                } else {
                    resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: run {
                        skipped++
                        continue
                    }
                }

                imported.add(
                    UploadedPhotoItem(
                        id = id,
                        uriString = Uri.fromFile(destFile).toString(),
                        displayName = displayName,
                        mimeType = mimeType ?: "image/$safeExt",
                        isCover = false
                    )
                )
            } catch (_: Exception) {
                skipped++
            }
        }

        if (imported.isEmpty() && skipped > 0) {
            PhotoImportResult.Error("Only JPG, JPEG, PNG, and WebP images are supported.")
        } else {
            PhotoImportResult.Success(imported, skipped)
        }
    }

    suspend fun importAudioFromUri(
        context: Context,
        uri: Uri,
        studioType: String
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val displayName = resolveDisplayName(context, uri) ?: "audio_${System.currentTimeMillis()}.mp3"
            val ext = displayName.substringAfterLast('.', "mp3").lowercase().take(8)
            val audioDir = File(context.filesDir, "studio_audio/${sanitizeSegment(studioType)}")
            if (!audioDir.exists()) audioDir.mkdirs()
            val destFile = File(audioDir, "audio_${UUID.randomUUID().toString().take(10)}.$ext")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            displayName to Uri.fromFile(destFile).toString()
        } catch (_: Exception) {
            null
        }
    }

    fun movePhotoLeft(photos: List<UploadedPhotoItem>, photoId: String): List<UploadedPhotoItem> {
        val idx = photos.indexOfFirst { it.id == photoId }
        if (idx <= 0) return photos
        val mutable = photos.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(idx - 1, item)
        return ensureCoverExists(mutable)
    }

    fun movePhotoRight(photos: List<UploadedPhotoItem>, photoId: String): List<UploadedPhotoItem> {
        val idx = photos.indexOfFirst { it.id == photoId }
        if (idx < 0 || idx >= photos.lastIndex) return photos
        val mutable = photos.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(idx + 1, item)
        return ensureCoverExists(mutable)
    }

    fun removePhoto(photos: List<UploadedPhotoItem>, photoId: String): List<UploadedPhotoItem> {
        val filtered = photos.filterNot { it.id == photoId }
        return ensureCoverExists(filtered)
    }

    fun setCoverPhoto(photos: List<UploadedPhotoItem>, photoId: String): List<UploadedPhotoItem> {
        if (photos.none { it.id == photoId }) return ensureCoverExists(photos)
        return photos.map { it.copy(isCover = it.id == photoId) }
    }

    fun ensureCoverExists(photos: List<UploadedPhotoItem>): List<UploadedPhotoItem> {
        if (photos.isEmpty()) return emptyList()
        if (photos.any { it.isCover }) return photos
        return photos.mapIndexed { index, item -> item.copy(isCover = index == 0) }
    }

    fun coverPhotoUri(photos: List<UploadedPhotoItem>): String =
        photos.firstOrNull { it.isCover }?.uriString ?: photos.firstOrNull()?.uriString.orEmpty()

    fun saveDraft(context: Context, draft: PhotoSlideshowDraft) {
        try {
            val dir = File(context.filesDir, "studio_projects")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${sanitizeSegment(draft.studioType)}_draft.json")
            val json = JSONObject().apply {
                put("studioType", draft.studioType)
                put("title", draft.title)
                put("prompt", draft.prompt)
                put("bibleVerse", draft.bibleVerse)
                put("captions", draft.captions)
                put("templateName", draft.templateName)
                put("aspectRatio", draft.aspectRatio.ratioLabel)
                put("resolutionPreset", draft.resolutionPreset.name)
                put("frameRate", draft.frameRate.fps)
                put("videoCodec", draft.videoCodec.name)
                put("includeLogoWatermark", draft.branding.includeLogoWatermark)
                put("includeOpeningTitle", draft.branding.includeOpeningTitle)
                put("includeClosingCredits", draft.branding.includeClosingCredits)
                put("transition", draft.transition.label)
                put("durationSecondsPerPhoto", draft.durationSecondsPerPhoto)
                put("audioSourceLabel", draft.audioSourceLabel)
                put("audioUriString", draft.audioUriString)
                put("lastExportedVideoPath", draft.lastExportedVideoPath)
                put("lastExportedMediaStoreUri", draft.lastExportedMediaStoreUri)
                put("captionDisplayMode", draft.captionDisplayMode.name)
                val cueArray = JSONArray()
                draft.captionCues.forEach { cue ->
                    cueArray.put(
                        JSONObject().apply {
                            put("id", cue.id)
                            put("startSec", cue.startSec)
                            put("endSec", cue.endSec)
                            put("sectionLabel", cue.sectionLabel)
                            put("englishText", cue.englishText)
                            put("twiText", cue.twiText)
                        }
                    )
                }
                put("captionCues", cueArray)
                val photoArray = JSONArray()
                draft.photos.forEach { photo ->
                    photoArray.put(
                        JSONObject().apply {
                            put("id", photo.id)
                            put("uriString", photo.uriString)
                            put("displayName", photo.displayName)
                            put("mimeType", photo.mimeType)
                            put("isCover", photo.isCover)
                        }
                    )
                }
                put("photos", photoArray)
            }
            file.writeText(json.toString())
        } catch (_: Exception) {
            // Best-effort local draft persistence
        }
    }

    fun loadDraft(context: Context, studioType: String): PhotoSlideshowDraft? {
        return try {
            val file = File(context.filesDir, "studio_projects/${sanitizeSegment(studioType)}_draft.json")
            if (!file.exists()) return null
            val json = JSONObject(file.readText())
            val photosArr = json.optJSONArray("photos") ?: JSONArray()
            val validPhotos = buildList {
                for (i in 0 until photosArr.length()) {
                    val item = photosArr.optJSONObject(i) ?: continue
                    val uriString = item.optString("uriString")
                    if (uriString.isBlank()) continue
                    val parsedUri = Uri.parse(uriString)
                    val fileExists = parsedUri.scheme != "file" || parsedUri.path?.let { File(it).exists() } == true
                    if (fileExists) {
                        add(
                            UploadedPhotoItem(
                                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                                uriString = uriString,
                                displayName = item.optString("displayName", "Photo"),
                                mimeType = item.optString("mimeType", "image/jpeg"),
                                isCover = item.optBoolean("isCover", false)
                            )
                        )
                    }
                }
            }
            val cuesArr = json.optJSONArray("captionCues") ?: JSONArray()
            val parsedCues = buildList {
                for (i in 0 until cuesArr.length()) {
                    val obj = cuesArr.optJSONObject(i) ?: continue
                    add(
                        TimedBilingualCue(
                            id = obj.optString("id").ifBlank { "cue_${i + 1}" },
                            startSec = obj.optInt("startSec", 0),
                            endSec = obj.optInt("endSec", 5),
                            sectionLabel = obj.optString("sectionLabel"),
                            englishText = obj.optString("englishText"),
                            twiText = obj.optString("twiText")
                        )
                    )
                }
            }
            val exportedPath = json.optString("lastExportedVideoPath")
                .takeIf { it.isNotBlank() && File(it).exists() }
                .orEmpty()

            PhotoSlideshowDraft(
                studioType = json.optString("studioType", studioType),
                title = json.optString("title"),
                prompt = json.optString("prompt"),
                bibleVerse = json.optString("bibleVerse"),
                captions = json.optString("captions"),
                templateName = json.optString("templateName"),
                aspectRatio = SlideshowAspectRatio.fromRatioLabel(json.optString("aspectRatio")),
                resolutionPreset = VideoResolutionPreset.fromNameOrLabel(json.optString("resolutionPreset")),
                frameRate = VideoFrameRate.fromFps(json.optInt("frameRate", 30)),
                videoCodec = VideoCodecOption.fromName(json.optString("videoCodec")),
                branding = WordLightBrandingOptions(
                    includeLogoWatermark = json.optBoolean("includeLogoWatermark", true),
                    includeOpeningTitle = json.optBoolean("includeOpeningTitle", true),
                    includeClosingCredits = json.optBoolean("includeClosingCredits", true)
                ),
                transition = SlideshowTransition.fromLabel(json.optString("transition")),
                durationSecondsPerPhoto = json.optInt("durationSecondsPerPhoto", 3).coerceIn(2, 10),
                audioSourceLabel = json.optString("audioSourceLabel"),
                audioUriString = json.optString("audioUriString"),
                photos = ensureCoverExists(validPhotos),
                captionCues = parsedCues,
                captionDisplayMode = BilingualCaptionMode.fromLabel(json.optString("captionDisplayMode")),
                lastExportedVideoPath = exportedPath,
                lastExportedMediaStoreUri = json.optString("lastExportedMediaStoreUri")
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveExportedVideoToDevice(
        context: Context,
        sourceFilePath: String,
        title: String
    ): SaveToDeviceResult = withContext(Dispatchers.IO) {
        val cleanPath = sourceFilePath.removePrefix("file://")
        val sourceFile = File(cleanPath)
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext SaveToDeviceResult.Error("Exported video file could not be found.")
        }

        val safeFileName = "${sanitizeFileName(title.ifBlank { "WordLight_Video" })}_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext SaveToDeviceResult.RequiresDocumentPicker(safeFileName)
        }

        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, safeFileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/WordLightAIStudio"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext SaveToDeviceResult.Error("Unable to create video entry in device gallery.")

            resolver.openOutputStream(itemUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: run {
                resolver.delete(itemUri, null, null)
                return@withContext SaveToDeviceResult.Error("Unable to write MP4 bytes to gallery.")
            }

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            SaveToDeviceResult.SavedToMediaStore(
                uri = itemUri,
                displayPath = "Movies/WordLightAIStudio/$safeFileName"
            )
        } catch (e: Exception) {
            SaveToDeviceResult.Error(e.localizedMessage ?: "Failed to save video to device.")
        }
    }

    suspend fun copyVideoToUri(
        context: Context,
        sourceFilePath: String,
        destinationUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceFilePath.removePrefix("file://"))
            if (!sourceFile.exists()) return@withContext false
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            } != null
        } catch (_: Exception) {
            false
        }
    }

    fun createShareVideoIntent(
        context: Context,
        videoFilePath: String,
        title: String
    ): Intent? {
        val cleanPath = videoFilePath.removePrefix("file://")
        val file = File(cleanPath)
        if (!file.exists()) return null
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Word & Light AI STUDIO Video" })
            putExtra(Intent.EXTRA_TEXT, "Created in Word & Light AI STUDIO: ${title.ifBlank { "Gospel Photo Video" }}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getExportedOutputFile(context: Context, studioType: String, title: String): File {
        val outDir = File(context.filesDir, "exported_videos")
        if (!outDir.exists()) outDir.mkdirs()
        val prefix = sanitizeSegment(studioType).lowercase()
        val cleanTitle = sanitizeFileName(title.ifBlank { "video" }).take(28)
        return File(outDir, "${prefix}_${cleanTitle}_${System.currentTimeMillis()}.mp4")
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
            } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun decodeOrientedScaledBitmap(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxEdge * 2 || bounds.outHeight / sampleSize > maxEdge * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            } ?: return null

            val rotationDegrees = try {
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) {
                0f
            }

            val currentMax = maxOf(rawBitmap.width, rawBitmap.height)
            val scale = if (currentMax > maxEdge) maxEdge.toFloat() / currentMax.toFloat() else 1f
            if (rotationDegrees == 0f && scale >= 0.99f) {
                rawBitmap
            } else {
                val matrix = Matrix().apply {
                    if (scale < 1f) postScale(scale, scale)
                    if (rotationDegrees != 0f) postRotate(rotationDegrees)
                }
                val transformed = Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    rawBitmap.width,
                    rawBitmap.height,
                    matrix,
                    true
                )
                if (transformed !== rawBitmap) rawBitmap.recycle()
                transformed
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeSegment(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "default" }

    private fun sanitizeFileName(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "WordLight_Video" }
}
