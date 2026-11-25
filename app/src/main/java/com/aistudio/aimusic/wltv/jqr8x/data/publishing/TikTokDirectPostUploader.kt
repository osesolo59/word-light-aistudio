package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.ceil
import kotlin.math.min

/**
 * Implements TikTok Content Posting API v2 Direct Post via FILE_UPLOAD:
 * 1. POST /v2/post/publish/creator_info/query/ -> validates creator privacy options, flags & max duration
 * 2. POST /v2/post/publish/video/init/ -> publish_id, upload_url (respecting TikTok privacy & sandbox SELF_ONLY)
 * 3. PUT chunks to upload_url with Content-Range: bytes start-end/total
 * 4. Poll POST /v2/post/publish/status/fetch/ until PUBLISH_COMPLETE
 */
class TikTokDirectPostUploader(
    private val httpClient: OkHttpClient = TokenBrokerClient.defaultHttpClient(),
    private val creatorInfoUrl: String = "https://open.tiktokapis.com/v2/post/publish/creator_info/query/",
    private val initUrl: String = "https://open.tiktokapis.com/v2/post/publish/video/init/",
    private val statusFetchUrl: String = "https://open.tiktokapis.com/v2/post/publish/status/fetch/",
    private val maxChunkBytes: Int = DEFAULT_CHUNK_BYTES,
    private val pollDelayMs: Long = 2000L
) {

    suspend fun queryCreatorInfo(accessToken: String): Result<TikTokCreatorInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(creatorInfoUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json; charset=UTF-8")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                val errObj = json.optJSONObject("error")
                val errCode = errObj?.optString("code").orEmpty()
                if (!response.isSuccessful || (errCode.isNotBlank() && errCode != "ok")) {
                    val errMsg = errObj?.optString("message").orEmpty().ifBlank { "HTTP ${response.code}" }
                    throw IOException("TikTok creator_info/query failed ($errCode): $errMsg")
                }
                val data = json.optJSONObject("data") ?: JSONObject()
                val privacyArr = data.optJSONArray("privacy_level_options")
                val privacyOptions = buildList {
                    if (privacyArr != null) {
                        for (i in 0 until privacyArr.length()) {
                            privacyArr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }
                TikTokCreatorInfo(
                    creatorNickname = data.optString("creator_nickname"),
                    creatorUsername = data.optString("creator_username"),
                    privacyLevelOptions = privacyOptions,
                    commentDisabled = data.optBoolean("comment_disabled", false),
                    duetDisabled = data.optBoolean("duet_disabled", false),
                    stitchDisabled = data.optBoolean("stitch_disabled", false),
                    maxVideoPostDurationSec = data.optInt("max_video_post_duration_sec", 600)
                )
            }
        }
    }

    suspend fun uploadVideo(
        accessToken: String,
        metadata: PublishVideoMetadata,
        creatorInfoOverride: TikTokCreatorInfo? = null,
        channelHandleHint: String = "",
        onProgress: (percent: Int, stepLabel: String) -> Unit
    ): Result<PlatformPublishStatus.Published> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(metadata.localVideoPath.removePrefix("file://").trim())
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                throw IOException("Local exported MP4 file not found or empty.")
            }
            val totalBytes = file.length()

            onProgress(2, "Querying TikTok creator posting options...")
            val creatorInfo = creatorInfoOverride ?: queryCreatorInfo(accessToken).getOrThrow()
            val enforcedPrivacyLevel = creatorInfo.effectiveTikTokPrivacyLevel(metadata.privacy)
            val (chunkSize, chunkCount) = computeChunkPlan(totalBytes, maxChunkBytes)

            onProgress(4, "Initializing TikTok Direct Post ($enforcedPrivacyLevel)...")
            val (publishId, uploadUrl) = initDirectPost(
                accessToken = accessToken,
                metadata = metadata,
                creatorInfo = creatorInfo,
                enforcedPrivacyLevel = enforcedPrivacyLevel,
                totalBytes = totalBytes,
                chunkSize = chunkSize,
                chunkCount = chunkCount
            )

            uploadChunks(
                uploadUrl = uploadUrl,
                file = file,
                totalBytes = totalBytes,
                chunkSize = chunkSize,
                chunkCount = chunkCount,
                onProgress = onProgress
            )

            onProgress(92, "Waiting for TikTok processing...")
            val (finalState, publicPostId) = pollStatus(accessToken, publishId)

            val handle = creatorInfo.creatorUsername.ifBlank { channelHandleHint }.trim()
                .removePrefix("@")
            val resolvedRemoteUrl = when {
                publicPostId.isNotBlank() && handle.isNotBlank() ->
                    "https://www.tiktok.com/@$handle/video/$publicPostId"
                handle.isNotBlank() ->
                    "https://www.tiktok.com/@$handle"
                else -> ""
            }

            onProgress(100, "TikTok upload complete")
            PlatformPublishStatus.Published(
                remoteId = publishId,
                remoteUrl = resolvedRemoteUrl,
                visibilityNote = "TikTok status: $finalState ($enforcedPrivacyLevel)"
            )
        }
    }

    internal fun computeChunkPlan(totalBytes: Long, preferredMaxChunk: Int): Pair<Long, Int> {
        if (totalBytes <= preferredMaxChunk.toLong()) {
            return totalBytes to 1
        }
        val chunk = preferredMaxChunk.toLong().coerceIn(MIN_CHUNK_BYTES, MAX_ALLOWED_CHUNK_BYTES)
        val count = ceil(totalBytes.toDouble() / chunk.toDouble()).toInt().coerceAtLeast(1)
        return chunk to count
    }

    private fun initDirectPost(
        accessToken: String,
        metadata: PublishVideoMetadata,
        creatorInfo: TikTokCreatorInfo,
        enforcedPrivacyLevel: String,
        totalBytes: Long,
        chunkSize: Long,
        chunkCount: Int
    ): Pair<String, String> {
        val captionTitle = buildString {
            append(metadata.title.ifBlank { "Word & Light TV" })
            if (metadata.hashtags.isNotBlank()) {
                append(" ")
                append(metadata.hashtags)
            }
        }.trim().take(2200)

        val payload = JSONObject().apply {
            put("post_info", JSONObject().apply {
                put("title", captionTitle)
                put("privacy_level", enforcedPrivacyLevel)
                put("disable_duet", !metadata.allowDuet || creatorInfo.duetDisabled)
                put("disable_comment", !metadata.allowComments || creatorInfo.commentDisabled)
                put("disable_stitch", !metadata.allowStitch || creatorInfo.stitchDisabled)
            })
            put("source_info", JSONObject().apply {
                put("source", "FILE_UPLOAD")
                put("video_size", totalBytes)
                put("chunk_size", chunkSize)
                put("total_chunk_count", chunkCount)
            })
        }

        val request = Request.Builder()
            .url(initUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=UTF-8")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
            val errObj = json.optJSONObject("error")
            val errCode = errObj?.optString("code").orEmpty()
            if (!response.isSuccessful || (errCode.isNotBlank() && errCode != "ok")) {
                val errMsg = errObj?.optString("message").orEmpty().ifBlank { "HTTP ${response.code}" }
                throw IOException("TikTok init failed ($errCode): $errMsg")
            }
            val data = json.optJSONObject("data") ?: throw IOException("TikTok init missing data object.")
            val publishId = data.optString("publish_id")
            val uploadUrl = data.optString("upload_url")
            if (publishId.isBlank() || uploadUrl.isBlank()) {
                throw IOException("TikTok init did not return publish_id / upload_url.")
            }
            return publishId to uploadUrl
        }
    }

    private fun uploadChunks(
        uploadUrl: String,
        file: File,
        totalBytes: Long,
        chunkSize: Long,
        chunkCount: Int,
        onProgress: (percent: Int, stepLabel: String) -> Unit
    ) {
        RandomAccessFile(file, "r").use { raf ->
            var offset = 0L
            for (index in 0 until chunkCount) {
                val remaining = totalBytes - offset
                if (remaining <= 0L) break
                val isLast = index == chunkCount - 1
                val readLen = if (isLast) remaining.toInt() else min(chunkSize, remaining).toInt()
                val bytes = ByteArray(readLen)
                raf.seek(offset)
                raf.readFully(bytes, 0, readLen)
                val endByte = offset + readLen - 1L

                val putRequest = Request.Builder()
                    .url(uploadUrl)
                    .header("Content-Type", "video/mp4")
                    .header("Content-Range", "bytes $offset-$endByte/$totalBytes")
                    .put(bytes.toRequestBody(MP4_MEDIA_TYPE, 0, readLen))
                    .build()

                httpClient.newCall(putRequest).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        val err = response.body?.string().orEmpty()
                        throw IOException("TikTok chunk PUT failed (${response.code}): ${err.take(150)}")
                    }
                }
                offset += readLen
                val pct = ((offset * 85L) / totalBytes).toInt().coerceIn(5, 90)
                onProgress(pct, "Uploading chunk ${index + 1}/$chunkCount to TikTok ($pct%)...")
            }
        }
    }

    private suspend fun pollStatus(accessToken: String, publishId: String): Pair<String, String> {
        val bodyJson = JSONObject().put("publish_id", publishId).toString()
        repeat(15) { attempt ->
            val request = Request.Builder()
                .url(statusFetchUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json; charset=UTF-8")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                if (!response.isSuccessful) {
                    throw IOException("TikTok status poll failed (${response.code})")
                }
                val data = json.optJSONObject("data")
                val status = data?.optString("status").orEmpty()
                val postIds = data?.optJSONArray("publicaly_available_post_id")
                val postId = postIds?.optString(0).orEmpty()
                when (status) {
                    "PUBLISH_COMPLETE", "SEND_TO_USER_INBOX" -> return status to postId
                    "FAILED" -> {
                        val reason = data?.optString("fail_reason").orEmpty()
                        throw IOException("TikTok rejected publish: ${reason.ifBlank { "FAILED" }}")
                    }
                }
            }
            if (attempt < 14) delay(pollDelayMs)
        }
        return "PROCESSING_IN_TIKTOK" to ""
    }

    companion object {
        private const val MIN_CHUNK_BYTES = 5L * 1024L * 1024L
        private const val DEFAULT_CHUNK_BYTES = 10 * 1024 * 1024
        private const val MAX_ALLOWED_CHUNK_BYTES = 64L * 1024L * 1024L
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        private val MP4_MEDIA_TYPE = "video/mp4".toMediaType()
    }
}
