package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * Implements Google YouTube Data API v3 Resumable Upload protocol (videos.insert)
 * and optional custom thumbnail upload (thumbnails.set), using strictly the
 * `https://www.googleapis.com/auth/youtube.upload` scope.
 *
 * Supports both local filesystem MP4 paths (studio exports) and Android SAF/Photo Picker
 * `content://` URIs without requiring broad storage permissions.
 */
class YouTubeResumableUploader(
    private val httpClient: OkHttpClient = TokenBrokerClient.defaultHttpClient(),
    private val resumableInitUrl: String = DEFAULT_RESUMABLE_INIT_URL,
    private val thumbnailsSetUrl: String = DEFAULT_THUMBNAILS_SET_URL,
    private val chunkSizeBytes: Int = DEFAULT_CHUNK_SIZE_BYTES,
    private val appContext: Context? = null
) {

    internal class YouTubeApiException(
        val httpCode: Int,
        val apiReason: String,
        message: String
    ) : IOException(message) {
        val isAuthError: Boolean get() = httpCode == 401 || httpCode == 403
    }

    private data class ResolvedVideoSource(
        val lengthBytes: Long,
        val mimeType: String,
        val tempFileToCleanup: File?,
        val openStream: () -> InputStream
    )

    suspend fun uploadVideo(
        accessToken: String,
        metadata: PublishVideoMetadata,
        onProgress: (percent: Int, stepLabel: String) -> Unit
    ): Result<PlatformPublishStatus.Published> = withContext(Dispatchers.IO) {
        var resolvedSource: ResolvedVideoSource? = null
        try {
            coroutineContext.ensureActive()
            onProgress(1, "Preparing selected video...")
            resolvedSource = resolveVideoSource(metadata.localVideoPath)
            val totalBytes = resolvedSource.lengthBytes
            if (totalBytes <= 0L) {
                throw IOException("Selected video file is empty or unreadable.")
            }

            coroutineContext.ensureActive()
            onProgress(3, "Initializing YouTube upload session...")
            val uploadSessionUrl = startResumableSession(
                accessToken = accessToken,
                metadata = metadata,
                totalBytes = totalBytes,
                mimeType = resolvedSource.mimeType
            )

            val videoId = streamChunks(
                uploadSessionUrl = uploadSessionUrl,
                accessToken = accessToken,
                source = resolvedSource,
                totalBytes = totalBytes,
                onProgress = onProgress
            )

            var thumbnailNote = ""
            if (metadata.localThumbnailPath.isNotBlank()) {
                coroutineContext.ensureActive()
                onProgress(98, "Uploading custom thumbnail...")
                thumbnailNote = uploadThumbnailBestEffort(
                    accessToken = accessToken,
                    videoId = videoId,
                    thumbnailPathOrUri = metadata.localThumbnailPath
                )
            }

            onProgress(100, "Uploaded to Word & Light TV on YouTube")
            val visibilityNote = buildString {
                append("Uploaded as ${metadata.privacy.label} on YouTube")
                if (thumbnailNote.isNotBlank()) {
                    append(" • ")
                    append(thumbnailNote)
                }
            }
            Result.success(
                PlatformPublishStatus.Published(
                    remoteId = videoId,
                    remoteUrl = "https://youtu.be/$videoId",
                    visibilityNote = visibilityNote
                )
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (err: Throwable) {
            Result.failure(err)
        } finally {
            resolvedSource?.tempFileToCleanup?.delete()
        }
    }

    private fun resolveVideoSource(pathOrUri: String): ResolvedVideoSource {
        val cleaned = pathOrUri.trim()
        if (cleaned.startsWith("content://", ignoreCase = true)) {
            val ctx = appContext
                ?: throw IOException("Android context is required to read selected system picker video URI.")
            val uri = Uri.parse(cleaned)
            val resolver = ctx.contentResolver
            val detectedMime = resolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
            val queriedSize = queryContentSizeBytes(ctx, uri)
            if (queriedSize > 0L) {
                return ResolvedVideoSource(
                    lengthBytes = queriedSize,
                    mimeType = detectedMime,
                    tempFileToCleanup = null,
                    openStream = {
                        resolver.openInputStream(uri)
                            ?: throw IOException("Unable to open selected video stream.")
                    }
                )
            }

            // Fallback when a DocumentProvider returns unknown size (-1): stage once in app cache
            val stagedFile = File.createTempFile("wltv_yt_stage_", ".mp4", ctx.cacheDir)
            resolver.openInputStream(uri)?.use { input ->
                stagedFile.outputStream().use { out ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            } ?: run {
                stagedFile.delete()
                throw IOException("Unable to open selected video stream.")
            }
            if (!stagedFile.exists() || stagedFile.length() <= 0L) {
                stagedFile.delete()
                throw IOException("Selected video file is empty or unreadable.")
            }
            return ResolvedVideoSource(
                lengthBytes = stagedFile.length(),
                mimeType = detectedMime,
                tempFileToCleanup = stagedFile,
                openStream = { FileInputStream(stagedFile) }
            )
        }

        val file = File(cleaned.removePrefix("file://").trim())
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            throw IOException("Selected video file not found or empty.")
        }
        return ResolvedVideoSource(
            lengthBytes = file.length(),
            mimeType = "video/mp4",
            tempFileToCleanup = null,
            openStream = { FileInputStream(file) }
        )
    }

    private fun queryContentSizeBytes(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeCol >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeCol)) {
                    val value = cursor.getLong(sizeCol)
                    if (value > 0L) return value
                }
            }
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private suspend fun startResumableSession(
        accessToken: String,
        metadata: PublishVideoMetadata,
        totalBytes: Long,
        mimeType: String
    ): String {
        val tags = metadata.parsedYouTubeTags()
        val payload = JSONObject().apply {
            put("snippet", JSONObject().apply {
                put("title", metadata.title.ifBlank { "Word & Light TV Video" }.take(100))
                put("description", metadata.buildFormattedDescription().take(5000))
                put("categoryId", "10") // Music
                if (tags.isNotEmpty()) {
                    put("tags", JSONArray(tags))
                }
            })
            put("status", JSONObject().apply {
                put("privacyStatus", metadata.privacy.youtubeStatus)
                put("selfDeclaredMadeForKids", false)
            })
        }

        val initRequest = Request.Builder()
            .url(resumableInitUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("X-Upload-Content-Length", totalBytes.toString())
            .header("X-Upload-Content-Type", mimeType)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeCancellable(initRequest).use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                val (reason, msg) = extractApiError(errBody)
                throw YouTubeApiException(
                    httpCode = response.code,
                    apiReason = reason,
                    message = "YouTube init failed (${response.code}): $msg"
                )
            }
            return response.header("Location")
                ?: response.header("location")
                ?: throw IOException("YouTube did not return a resumable Location header.")
        }
    }

    private suspend fun streamChunks(
        uploadSessionUrl: String,
        accessToken: String,
        source: ResolvedVideoSource,
        totalBytes: Long,
        onProgress: (percent: Int, stepLabel: String) -> Unit
    ): String {
        val effectiveChunkSize = chunkSizeBytes.coerceAtLeast(256 * 1024)
        val buffer = ByteArray(effectiveChunkSize)
        var offset = 0L

        source.openStream().use { inputStream ->
            while (offset < totalBytes) {
                coroutineContext.ensureActive()
                val remaining = totalBytes - offset
                val targetChunkLen = min(effectiveChunkSize.toLong(), remaining).toInt()
                val bytesRead = readExactChunk(inputStream, buffer, targetChunkLen)
                if (bytesRead <= 0) {
                    throw IOException("Unexpected end of video stream before upload finished.")
                }
                val endByte = offset + bytesRead - 1L
                val chunkMediaType = source.mimeType.toMediaType()
                val chunkBody = buffer.toRequestBody(chunkMediaType, 0, bytesRead)

                val putRequest = Request.Builder()
                    .url(uploadSessionUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Content-Range", "bytes $offset-$endByte/$totalBytes")
                    .put(chunkBody)
                    .build()

                executeCancellable(putRequest).use { response ->
                    val code = response.code
                    val bodyText = response.body?.string().orEmpty()
                    if (code == 200 || code == 201) {
                        val json = runCatching { JSONObject(bodyText) }.getOrNull()
                        val videoId = json?.optString("id").orEmpty()
                        if (videoId.isBlank()) {
                            throw IOException("YouTube upload finished without returning a video ID.")
                        }
                        onProgress(97, "Video uploaded. Finalizing...")
                        return videoId
                    } else if (code == 308) {
                        offset += bytesRead
                        val pct = ((offset * 92L) / totalBytes).toInt().coerceIn(5, 96)
                        onProgress(pct, "Uploading video to YouTube ($pct%)...")
                    } else {
                        val (reason, msg) = extractApiError(bodyText)
                        throw YouTubeApiException(
                            httpCode = code,
                            apiReason = reason,
                            message = "YouTube upload error ($code): $msg"
                        )
                    }
                }
            }
        }
        throw IOException("YouTube upload ended before confirmation.")
    }

    private suspend fun uploadThumbnailBestEffort(
        accessToken: String,
        videoId: String,
        thumbnailPathOrUri: String
    ): String {
        return runCatching {
            val (bytes, mimeType) = loadThumbnailPayload(thumbnailPathOrUri)
            val urlWithQuery = if (thumbnailsSetUrl.contains("?")) {
                "$thumbnailsSetUrl&videoId=$videoId"
            } else {
                "$thumbnailsSetUrl?videoId=$videoId"
            }
            val req = Request.Builder()
                .url(urlWithQuery)
                .header("Authorization", "Bearer $accessToken")
                .post(bytes.toRequestBody(mimeType.toMediaType()))
                .build()

            executeCancellable(req).use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val (_, msg) = extractApiError(raw)
                    "Custom thumbnail not applied (${response.code}: $msg)"
                } else {
                    "Custom thumbnail applied"
                }
            }
        }.getOrElse { err ->
            if (err is CancellationException) throw err
            "Custom thumbnail skipped (${err.localizedMessage ?: "unreadable image"})"
        }
    }

    private fun loadThumbnailPayload(pathOrUri: String): Pair<ByteArray, String> {
        val rawBytes: ByteArray
        val rawMime: String
        if (pathOrUri.trim().startsWith("content://", ignoreCase = true)) {
            val ctx = appContext ?: throw IOException("Context unavailable for thumbnail URI.")
            val uri = Uri.parse(pathOrUri.trim())
            rawMime = ctx.contentResolver.getType(uri)?.takeIf {
                it == "image/jpeg" || it == "image/png"
            } ?: "image/jpeg"
            rawBytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Unable to read selected thumbnail.")
        } else {
            val file = File(pathOrUri.removePrefix("file://").trim())
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                throw IOException("Selected thumbnail file not found.")
            }
            rawMime = if (file.name.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
            rawBytes = file.readBytes()
        }

        if (rawBytes.size <= MAX_THUMBNAIL_BYTES) {
            return rawBytes to rawMime
        }

        // Compress oversized gallery photos into a <= 2 MB JPEG for YouTube thumbnails.set
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: throw IOException("Thumbnail exceeds 2 MB limit.")
        try {
            var quality = 88
            while (quality >= 40) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val candidate = out.toByteArray()
                if (candidate.size <= MAX_THUMBNAIL_BYTES) {
                    return candidate to "image/jpeg"
                }
                quality -= 12
            }
            throw IOException("Thumbnail exceeds YouTube's 2 MB image limit.")
        } finally {
            bitmap.recycle()
        }
    }

    private fun readExactChunk(inputStream: InputStream, buffer: ByteArray, targetBytes: Int): Int {
        var totalRead = 0
        while (totalRead < targetBytes) {
            val count = inputStream.read(buffer, totalRead, targetBytes - totalRead)
            if (count == -1) break
            totalRead += count
        }
        return totalRead
    }

    private suspend fun executeCancellable(request: Request): Response {
        coroutineContext.ensureActive()
        return suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled || call.isCanceled()) {
                        cont.cancel(CancellationException("Upload cancelled by user."))
                    } else if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isCancelled || call.isCanceled()) {
                        response.close()
                        cont.cancel(CancellationException("Upload cancelled by user."))
                    } else if (cont.isActive) {
                        cont.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }
    }

    private fun extractApiError(raw: String): Pair<String, String> {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return "" to raw.take(180)
        val errObj = obj.optJSONObject("error")
        val reason = errObj?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        val msg = errObj?.optString("message")?.takeIf { it.isNotBlank() }
            ?: obj.optString("error_description").ifBlank { raw.take(180) }
        return reason to msg
    }

    companion object {
        const val DEFAULT_RESUMABLE_INIT_URL =
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"
        const val DEFAULT_THUMBNAILS_SET_URL =
            "https://www.googleapis.com/upload/youtube/v3/thumbnails/set"
        private const val DEFAULT_CHUNK_SIZE_BYTES = 4 * 1024 * 1024 // 4 MB (multiple of 256 KB)
        private const val MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
