package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aistudio.aimusic.wltv.jqr8x.R
import java.io.IOException

class VideoPublishWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val localVideoPath = inputData.getString(KEY_LOCAL_VIDEO_PATH).orEmpty()
        if (localVideoPath.isBlank()) {
            return Result.failure(
                Data.Builder().putString(KEY_ERROR_MESSAGE, "Missing local MP4 video file path.").build()
            )
        }

        val platformKeys = inputData.getStringArray(KEY_PLATFORMS).orEmpty()
        val platforms = platformKeys.mapNotNull { PublishPlatform.fromKey(it) }
        if (platforms.isEmpty()) {
            return Result.failure(
                Data.Builder().putString(KEY_ERROR_MESSAGE, "No target publishing platforms selected.").build()
            )
        }

        val metadata = PublishVideoMetadata(
            localVideoPath = localVideoPath,
            title = inputData.getString(KEY_TITLE).orEmpty(),
            description = inputData.getString(KEY_DESCRIPTION).orEmpty(),
            hashtags = inputData.getString(KEY_HASHTAGS).orEmpty(),
            bibleVerse = inputData.getString(KEY_BIBLE_VERSE).orEmpty(),
            privacy = PublishPrivacy.fromName(inputData.getString(KEY_PRIVACY)),
            aspectRatio = inputData.getString(KEY_ASPECT_RATIO).orEmpty().ifBlank { "16:9" },
            codecLabel = inputData.getString(KEY_CODEC_LABEL).orEmpty().ifBlank { "H.264" },
            allowComments = inputData.getBoolean(KEY_ALLOW_COMMENTS, true),
            allowDuet = inputData.getBoolean(KEY_ALLOW_DUET, true),
            allowStitch = inputData.getBoolean(KEY_ALLOW_STITCH, true)
        )

        runCatching {
            setForeground(createForegroundInfo("Preparing broadcast video upload..."))
        }

        val repository = PublishingRepository.getInstance(applicationContext)
        val failedPlatforms = mutableListOf<String>()
        val succeededPlatforms = mutableListOf<String>()
        var shouldRetryTransient = false
        var lastError = ""

        for (platform in platforms) {
            if (runAttemptCount > 0 && repository.getPersistedStatus(platform) is PlatformPublishStatus.Published) {
                succeededPlatforms.add(platform.key)
                continue
            }

            runCatching {
                setForeground(createForegroundInfo("Publishing to ${platform.displayName}..."))
            }

            when (val status = repository.executePlatformUpload(platform, metadata)) {
                is PlatformPublishStatus.Published -> {
                    succeededPlatforms.add(platform.key)
                }
                is PlatformPublishStatus.Failed -> {
                    failedPlatforms.add(platform.key)
                    lastError = "${platform.displayName}: ${status.errorMessage}"
                    if (!status.requiresReauth && isTransientErrorMessage(status.errorMessage)) {
                        shouldRetryTransient = true
                    }
                }
                else -> Unit
            }
        }

        if (shouldRetryTransient && failedPlatforms.isNotEmpty() && runAttemptCount < MAX_RETRY_ATTEMPTS) {
            failedPlatforms.forEach { key ->
                PublishPlatform.fromKey(key)?.let { p ->
                    repository.updatePlatformStatus(
                        p,
                        PlatformPublishStatus.Queued("Retrying upload (attempt ${runAttemptCount + 2})...")
                    )
                }
            }
            return Result.retry()
        }

        val outputData = Data.Builder()
            .putStringArray(KEY_SUCCEEDED_PLATFORMS, succeededPlatforms.toTypedArray())
            .putStringArray(KEY_FAILED_PLATFORMS, failedPlatforms.toTypedArray())
            .putString(KEY_ERROR_MESSAGE, lastError)
            .build()

        return if (failedPlatforms.isNotEmpty()) {
            Result.failure(outputData)
        } else {
            Result.success(outputData)
        }
    }

    internal fun createForegroundInfo(progressText: String): ForegroundInfo {
        ensureNotificationChannel()
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Word & Light TV Direct Publishing")
            .setTicker("Word & Light TV Direct Publishing")
            .setContentText(progressText)
            .setSmallIcon(R.drawable.ic_social_youtube)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Direct Video Publishing",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Progress notifications when uploading videos to connected ministry channels."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val KEY_LOCAL_VIDEO_PATH = "local_video_path"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_HASHTAGS = "hashtags"
        const val KEY_BIBLE_VERSE = "bible_verse"
        const val KEY_PRIVACY = "privacy"
        const val KEY_ASPECT_RATIO = "aspect_ratio"
        const val KEY_CODEC_LABEL = "codec_label"
        const val KEY_ALLOW_COMMENTS = "allow_comments"
        const val KEY_ALLOW_DUET = "allow_duet"
        const val KEY_ALLOW_STITCH = "allow_stitch"
        const val KEY_PLATFORMS = "platforms"
        const val KEY_SUCCEEDED_PLATFORMS = "succeeded_platforms"
        const val KEY_FAILED_PLATFORMS = "failed_platforms"
        const val KEY_ERROR_MESSAGE = "error_message"

        private const val CHANNEL_ID = "wltv_direct_publish_channel"
        private const val NOTIFICATION_ID = 4109
        private const val MAX_RETRY_ATTEMPTS = 2

        internal fun isTransientErrorMessage(message: String): Boolean {
            val lower = message.lowercase()
            return lower.contains("timeout") ||
                lower.contains("timed out") ||
                lower.contains("unable to resolve host") ||
                lower.contains("connection reset") ||
                lower.contains("http 429") ||
                lower.contains("http 500") ||
                lower.contains("http 502") ||
                lower.contains("http 503") ||
                lower.contains("http 504") ||
                lower.contains("processing_in_tiktok")
        }
    }
}
