package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * Implements Meta Graph API 3-Phase Resumable Page Video Upload:
 * 1. upload_phase=start -> upload_session_id, video_id, start_offset, end_offset
 * 2. upload_phase=transfer (loop per chunk offset)
 * 3. upload_phase=finish -> title, description, published flag
 *
 * Enforces that pageId is a valid numeric Facebook Page ID and uses a non-blank Page access token.
 */
class MetaPageVideoUploader(
    private val httpClient: OkHttpClient = TokenBrokerClient.defaultHttpClient(),
    private val graphVideoBaseUrl: String = "https://graph-video.facebook.com/v21.0"
) {

    suspend fun uploadToPage(
        pageId: String,
        pageAccessToken: String,
        metadata: PublishVideoMetadata,
        onProgress: (percent: Int, stepLabel: String) -> Unit
    ): Result<PlatformPublishStatus.Published> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanPageId = pageId.trim()
            if (cleanPageId.isBlank() || !NUMERIC_PAGE_ID_REGEX.matches(cleanPageId)) {
                throw IllegalStateException("Select a valid Word & Light TV Facebook Page ID before publishing.")
            }
            if (pageAccessToken.isBlank()) {
                throw SecurityException("Missing Facebook Page access token. Re-authorize Facebook Page access.")
            }
            val file = File(metadata.localVideoPath.removePrefix("file://").trim())
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                throw IOException("Local exported MP4 file not found or empty.")
            }
            val totalBytes = file.length()
            val endpoint = "${graphVideoBaseUrl.trimEnd('/')}/$cleanPageId/videos"

            onProgress(3, "Starting Facebook Page upload session...")
            val startForm = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_token", pageAccessToken)
                .addFormDataPart("upload_phase", "start")
                .addFormDataPart("file_size", totalBytes.toString())
                .build()

            val startJson = executePost(endpoint, startForm)
            val sessionId = startJson.optString("upload_session_id")
            val videoId = startJson.optString("video_id")
            if (sessionId.isBlank() || videoId.isBlank()) {
                throw IOException("Facebook Graph API did not return upload_session_id / video_id.")
            }

            var startOffset = startJson.optString("start_offset").toLongOrNull() ?: 0L
            var endOffset = startJson.optString("end_offset").toLongOrNull() ?: totalBytes

            RandomAccessFile(file, "r").use { raf ->
                while (startOffset < totalBytes && startOffset < endOffset) {
                    val length = min(endOffset - startOffset, MAX_LOCAL_CHUNK_BYTES).toInt()
                    val chunkBytes = ByteArray(length)
                    raf.seek(startOffset)
                    raf.readFully(chunkBytes, 0, length)

                    val transferBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("access_token", pageAccessToken)
                        .addFormDataPart("upload_phase", "transfer")
                        .addFormDataPart("upload_session_id", sessionId)
                        .addFormDataPart("start_offset", startOffset.toString())
                        .addFormDataPart(
                            "video_file_chunk",
                            file.name.ifBlank { "word_and_light.mp4" },
                            chunkBytes.toRequestBody(OCTET_STREAM)
                        )
                        .build()

                    val transferJson = executePost(endpoint, transferBody)
                    val nextStart = transferJson.optString("start_offset").toLongOrNull() ?: totalBytes
                    val nextEnd = transferJson.optString("end_offset").toLongOrNull() ?: totalBytes
                    startOffset = nextStart
                    endOffset = nextEnd

                    val pct = ((startOffset.coerceAtMost(totalBytes) * 90L) / totalBytes).toInt().coerceIn(5, 92)
                    onProgress(pct, "Uploading video chunk to Facebook Page ($pct%)...")
                }
            }

            onProgress(95, "Finalizing Facebook Page post...")
            val publishNow = metadata.privacy == PublishPrivacy.PUBLIC
            val finishForm = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_token", pageAccessToken)
                .addFormDataPart("upload_phase", "finish")
                .addFormDataPart("upload_session_id", sessionId)
                .addFormDataPart("title", metadata.title.ifBlank { "Word & Light TV" })
                .addFormDataPart("description", metadata.buildFormattedDescription())
                .addFormDataPart("published", publishNow.toString())
                .build()

            val finishJson = executePost(endpoint, finishForm)
            if (!finishJson.optBoolean("success", true)) {
                throw IOException("Facebook Page finish phase returned success=false.")
            }

            onProgress(100, "Published to Word & Light TV Facebook Page")
            PlatformPublishStatus.Published(
                remoteId = videoId,
                remoteUrl = "https://www.facebook.com/watch/?v=$videoId",
                visibilityNote = if (publishNow) {
                    "Published live to Facebook Page"
                } else {
                    "Uploaded as unpublished draft on Facebook Page"
                }
            )
        }
    }

    private fun executePost(url: String, body: MultipartBody): JSONObject {
        val request = Request.Builder().url(url).post(body).build()
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
            if (!response.isSuccessful) {
                val msg = json.optJSONObject("error")?.optString("message").orEmpty()
                    .ifBlank { "Meta Graph API HTTP ${response.code}" }
                throw IOException(msg)
            }
            return json
        }
    }

    companion object {
        private val NUMERIC_PAGE_ID_REGEX = Regex("^\\d+$")
        private const val MAX_LOCAL_CHUNK_BYTES = 4L * 1024L * 1024L
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
