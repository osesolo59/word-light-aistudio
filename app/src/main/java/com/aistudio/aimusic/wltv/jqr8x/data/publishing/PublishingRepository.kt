package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Coordinates official authorization (Google AuthorizationClient for YouTube,
 * OAuth 2.0 + PKCE + Token Broker for Meta & TikTok), hardware-encrypted token storage,
 * TikTok creator_info/query preflight, direct interactive YouTube uploads, and
 * foreground-capable WorkManager batch uploads.
 */
class PublishingRepository(
    context: Context,
    private val envConfig: PublishingEnvironmentConfig = PublishingEnvironmentConfig.fromBuildConfig(),
    private val sessionStore: EncryptedSessionStore = EncryptedSessionStore(context),
    private val statusStore: PublishStatusStore = PublishStatusStore(context),
    private val tokenBrokerClient: TokenBrokerClient = TokenBrokerClient(),
    private val youTubeAuthAdapter: YouTubeAuthorizationClientAdapter = GoogleYouTubeAuthorizationManager,
    private val youtubeUploader: YouTubeResumableUploader = YouTubeResumableUploader(appContext = context.applicationContext),
    private val metaUploader: MetaPageVideoUploader = MetaPageVideoUploader(),
    private val tikTokUploader: TikTokDirectPostUploader = TikTokDirectPostUploader()
) {
    private val appContext = context.applicationContext
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessions = MutableStateFlow(sessionStore.getAllSessions())
    val sessionsFlow: StateFlow<Map<PublishPlatform, ConnectedChannelSession?>> = _sessions.asStateFlow()

    private val _publishStatuses = MutableStateFlow(statusStore.getAllStatuses())
    val publishStatusesFlow: StateFlow<Map<PublishPlatform, PlatformPublishStatus>> = _publishStatuses.asStateFlow()

    private val _tikTokCreatorInfo = MutableStateFlow<TikTokCreatorInfo?>(null)
    val tikTokCreatorInfo: StateFlow<TikTokCreatorInfo?> = _tikTokCreatorInfo.asStateFlow()

    private val _lastAuthFeedback = MutableStateFlow<String?>(null)
    val lastAuthFeedback: StateFlow<String?> = _lastAuthFeedback.asStateFlow()

    private val _youTubeUploadState = MutableStateFlow<YouTubeUploadUiState>(YouTubeUploadUiState.Idle)
    val youTubeUploadState: StateFlow<YouTubeUploadUiState> = _youTubeUploadState.asStateFlow()

    @Volatile
    private var youTubeUploadJob: Job? = null

    fun getEnvironmentConfig(): PublishingEnvironmentConfig = envConfig

    fun refreshLocalSessions() {
        _sessions.value = sessionStore.getAllSessions()
    }

    fun getPersistedStatus(platform: PublishPlatform): PlatformPublishStatus =
        statusStore.getStatus(platform)

    suspend fun authorizeYouTube(activity: Activity): YouTubeAuthOutcome {
        if (!sessionStore.isSecureStorageAvailable()) {
            val msg = "Device Keystore encryption is unavailable. Refusing to store YouTube tokens insecurely."
            _lastAuthFeedback.value = msg
            return YouTubeAuthOutcome.Failed(msg)
        }
        return when (val outcome = youTubeAuthAdapter.authorize(activity)) {
            is YouTubeAuthOutcome.Authorized -> {
                saveYouTubeAuthorizedSession(outcome.accessToken, outcome.grantedScopes)
                outcome
            }
            is YouTubeAuthOutcome.NeedsUserResolution -> outcome
            is YouTubeAuthOutcome.Failed -> {
                _lastAuthFeedback.value = outcome.message
                outcome
            }
        }
    }

    fun handleYouTubeResolutionResult(data: Intent?): YouTubeAuthOutcome {
        return when (val outcome = youTubeAuthAdapter.extractResultFromIntent(appContext, data)) {
            is YouTubeAuthOutcome.Authorized -> {
                saveYouTubeAuthorizedSession(outcome.accessToken, outcome.grantedScopes)
                outcome
            }
            is YouTubeAuthOutcome.NeedsUserResolution -> outcome
            is YouTubeAuthOutcome.Failed -> {
                _lastAuthFeedback.value = outcome.message
                outcome
            }
        }
    }

    private fun saveYouTubeAuthorizedSession(accessToken: String, grantedScopes: List<String>) {
        runCatching {
            val session = ConnectedChannelSession(
                platform = PublishPlatform.YOUTUBE,
                accessToken = accessToken,
                expiresAtEpochMs = System.currentTimeMillis() +
                    GoogleYouTubeAuthorizationManager.DEFAULT_ACCESS_TOKEN_TTL_MS,
                channelDisplayName = PublishPlatform.YOUTUBE.officialTargetName,
                channelHandle = "@WordAndLightTV-v6c",
                scopes = grantedScopes.ifEmpty { listOf(GoogleYouTubeAuthorizationManager.YOUTUBE_UPLOAD_SCOPE) }
            )
            sessionStore.saveSession(session)
            refreshLocalSessions()
            _lastAuthFeedback.value = "Authorized YouTube (youtube.upload): ${session.effectiveDisplayLabel()}"
        }.onFailure { err ->
            _lastAuthFeedback.value = "Secure storage error: ${err.localizedMessage}"
        }
    }

    fun startOAuthConnection(context: Context, platform: PublishPlatform): Result<OAuthLaunchRequest> {
        if (platform == PublishPlatform.YOUTUBE) {
            return Result.failure(
                UnsupportedOperationException("Use authorizeYouTube(activity) for Google YouTube AuthorizationClient.")
            )
        }
        if (!sessionStore.isSecureStorageAvailable()) {
            val err = SecurityException("Android Keystore encryption is unavailable; cannot start OAuth safely.")
            _lastAuthFeedback.value = err.message
            return Result.failure(err)
        }
        val requestResult = OAuthPkceManager.buildAuthorizationRequest(platform, envConfig)
        requestResult.onSuccess { launchReq ->
            sessionStore.savePendingOAuthState(launchReq.pendingState)
            val launched = OAuthPkceManager.launchAuthorization(context, launchReq.authorizationUri)
            if (launched.isFailure) {
                return Result.failure(
                    launched.exceptionOrNull()
                        ?: IllegalStateException("Unable to open secure browser for OAuth.")
                )
            }
        }.onFailure { err ->
            _lastAuthFeedback.value = err.localizedMessage
        }
        return requestResult
    }

    suspend fun handleOAuthRedirectUri(
        uri: Uri,
        nowMs: Long = System.currentTimeMillis()
    ): Result<ConnectedChannelSession> {
        val callback = OAuthPkceManager.parseRedirectUri(uri)
        val platform = callback.platform
            ?: return Result.failure(
                SecurityException("Unsupported or disallowed OAuth redirect target.")
            )

        if (!callback.error.isNullOrBlank()) {
            val message = callback.errorDescription ?: callback.error
            _lastAuthFeedback.value = "Authorization cancelled or denied: $message"
            return Result.failure(IllegalStateException(message))
        }

        val state = callback.state
        if (state.isNullOrBlank()) {
            val err = "Missing OAuth CSRF state for ${platform.displayName}. Connection aborted."
            _lastAuthFeedback.value = err
            return Result.failure(SecurityException(err))
        }

        val pending = runCatching {
            sessionStore.consumePendingOAuthState(
                platform = platform,
                expectedState = state,
                nowMs = nowMs
            )
        }.getOrElse { secErr ->
            _lastAuthFeedback.value = secErr.localizedMessage
            return Result.failure(secErr)
        } ?: return Result.failure(
            SecurityException("Pending OAuth state for ${platform.displayName} is missing, mismatched, or expired (>10 min). Please try again.")
        )

        val code = callback.code
        if (code.isNullOrBlank()) {
            val err = "${platform.displayName} callback did not include an authorization code."
            _lastAuthFeedback.value = err
            return Result.failure(IllegalStateException(err))
        }

        val exchanged = tokenBrokerClient.exchangeAuthorizationCode(
            platform = platform,
            code = code,
            codeVerifier = pending.codeVerifier,
            config = envConfig
        )
        exchanged.onSuccess { session ->
            runCatching {
                sessionStore.saveSession(session)
                refreshLocalSessions()
                _lastAuthFeedback.value = "Authorized ${platform.displayName}: ${session.effectiveDisplayLabel()}"
            }.onFailure { storeErr ->
                _lastAuthFeedback.value = storeErr.localizedMessage
                return Result.failure(storeErr)
            }
        }.onFailure { err ->
            _lastAuthFeedback.value = "${platform.displayName} authorization error: ${err.localizedMessage}"
        }
        return exchanged
    }

    suspend fun refreshTikTokCreatorInfo(): Result<TikTokCreatorInfo> {
        val currentSession = sessionStore.getSession(PublishPlatform.TIKTOK)
            ?: return Result.failure(IllegalStateException("TikTok is not authorized."))
        val validSession = tokenBrokerClient.refreshSessionIfNeeded(currentSession, envConfig)
            .getOrElse { return Result.failure(it) }
        if (validSession != currentSession) {
            sessionStore.saveSession(validSession)
            refreshLocalSessions()
        }
        return tikTokUploader.queryCreatorInfo(validSession.accessToken).onSuccess { info ->
            _tikTokCreatorInfo.value = info
        }
    }

    fun selectFacebookPage(page: FacebookPageTarget) {
        val updated = runCatching { sessionStore.updateFacebookTargetPage(page) }.getOrNull()
        if (updated != null) {
            refreshLocalSessions()
            _lastAuthFeedback.value = "Selected Facebook Page: ${page.pageName}"
        } else {
            _lastAuthFeedback.value = "Invalid Facebook Page selection (missing Page ID or Page token)."
        }
    }

    fun disconnectPlatform(platform: PublishPlatform) {
        if (platform == PublishPlatform.YOUTUBE) {
            youTubeUploadJob?.cancel()
            youTubeUploadJob = null
            _youTubeUploadState.value = YouTubeUploadUiState.Idle
        }
        sessionStore.clearSession(platform)
        refreshLocalSessions()
        updatePlatformStatus(platform, PlatformPublishStatus.Idle)
        if (platform == PublishPlatform.TIKTOK) {
            _tikTokCreatorInfo.value = null
        }
        _lastAuthFeedback.value = "Disconnected ${platform.displayName} session."
    }

    fun startYouTubeDirectUpload(metadata: PublishVideoMetadata) {
        if (metadata.localVideoPath.isBlank()) {
            _youTubeUploadState.value = YouTubeUploadUiState.Error(
                message = "Please select a video file from your device before uploading.",
                requiresReauth = false
            )
            return
        }
        val existingSession = sessionStore.getSession(PublishPlatform.YOUTUBE)
        if (existingSession == null) {
            _youTubeUploadState.value = YouTubeUploadUiState.Error(
                message = "YouTube is not authorized. Please authorize Word & Light TV first.",
                requiresReauth = true
            )
            return
        }

        youTubeUploadJob?.cancel()
        _youTubeUploadState.value = YouTubeUploadUiState.Uploading(
            progressPercent = 1,
            stepLabel = "Preparing upload to Word & Light TV..."
        )
        updatePlatformStatus(
            PublishPlatform.YOUTUBE,
            PlatformPublishStatus.Uploading(1, "Preparing YouTube upload...")
        )

        youTubeUploadJob = repoScope.launch {
            try {
                var activeSession = tokenBrokerClient.refreshSessionIfNeeded(
                    session = existingSession,
                    config = envConfig,
                    youtubeRefresherOverride = { youTubeAuthAdapter.refreshAccessTokenSilently(appContext) }
                ).getOrElse { err ->
                    val msg = mapFriendlyUploadError(err)
                    _youTubeUploadState.value = YouTubeUploadUiState.Error(msg, requiresReauth = true)
                    updatePlatformStatus(
                        PublishPlatform.YOUTUBE,
                        PlatformPublishStatus.Failed(msg, requiresReauth = true)
                    )
                    return@launch
                }

                if (activeSession != existingSession) {
                    runCatching { sessionStore.saveSession(activeSession) }
                    refreshLocalSessions()
                }

                val onProgress: (Int, String) -> Unit = { pct, label ->
                    _youTubeUploadState.value = YouTubeUploadUiState.Uploading(pct, label)
                    updatePlatformStatus(PublishPlatform.YOUTUBE, PlatformPublishStatus.Uploading(pct, label))
                }

                var result = youtubeUploader.uploadVideo(
                    accessToken = activeSession.accessToken,
                    metadata = metadata,
                    onProgress = onProgress
                )

                val firstErr = result.exceptionOrNull()
                if (firstErr is YouTubeResumableUploader.YouTubeApiException && firstErr.httpCode == 401) {
                    onProgress(2, "Refreshing YouTube authorization...")
                    val refreshedToken = youTubeAuthAdapter.refreshAccessTokenSilently(appContext)
                    if (refreshedToken.isSuccess) {
                        activeSession = activeSession.copy(
                            accessToken = refreshedToken.getOrThrow(),
                            expiresAtEpochMs = System.currentTimeMillis() +
                                GoogleYouTubeAuthorizationManager.DEFAULT_ACCESS_TOKEN_TTL_MS
                        )
                        runCatching { sessionStore.saveSession(activeSession) }
                        refreshLocalSessions()
                        result = youtubeUploader.uploadVideo(
                            accessToken = activeSession.accessToken,
                            metadata = metadata,
                            onProgress = onProgress
                        )
                    }
                }

                result.fold(
                    onSuccess = { published ->
                        _youTubeUploadState.value = YouTubeUploadUiState.Success(
                            videoId = published.remoteId,
                            videoUrl = published.remoteUrl,
                            visibilityNote = published.visibilityNote
                        )
                        updatePlatformStatus(PublishPlatform.YOUTUBE, published)
                    },
                    onFailure = { err ->
                        val requiresReauth = err is SecurityException ||
                            (err is YouTubeResumableUploader.YouTubeApiException && err.isAuthError) ||
                            err.localizedMessage?.contains("401") == true
                        val friendlyMsg = mapFriendlyUploadError(err)
                        _youTubeUploadState.value = YouTubeUploadUiState.Error(
                            message = friendlyMsg,
                            requiresReauth = requiresReauth
                        )
                        updatePlatformStatus(
                            PublishPlatform.YOUTUBE,
                            PlatformPublishStatus.Failed(friendlyMsg, requiresReauth = requiresReauth)
                        )
                    }
                )
            } catch (_: CancellationException) {
                _youTubeUploadState.value = YouTubeUploadUiState.Cancelled("YouTube upload was cancelled.")
                updatePlatformStatus(PublishPlatform.YOUTUBE, PlatformPublishStatus.Idle)
            }
        }
    }

    fun cancelYouTubeUpload() {
        youTubeUploadJob?.cancel()
        youTubeUploadJob = null
        _youTubeUploadState.value = YouTubeUploadUiState.Cancelled("YouTube upload cancelled by user.")
        updatePlatformStatus(PublishPlatform.YOUTUBE, PlatformPublishStatus.Idle)
    }

    fun resetYouTubeUploadState() {
        if (_youTubeUploadState.value !is YouTubeUploadUiState.Uploading) {
            _youTubeUploadState.value = YouTubeUploadUiState.Idle
        }
    }

    private fun mapFriendlyUploadError(err: Throwable): String {
        return when (err) {
            is UnknownHostException -> "No internet connection. Please check your network and try again."
            is SocketTimeoutException -> "Upload timed out. Check your internet stability and retry."
            is YouTubeResumableUploader.YouTubeApiException -> when {
                err.httpCode == 401 -> "YouTube authorization expired. Tap Re-authorize YouTube and try again."
                err.httpCode == 403 && err.apiReason.equals("quotaExceeded", ignoreCase = true) ->
                    "YouTube API daily upload quota reached for this project. Try again later."
                err.httpCode == 403 -> "YouTube denied upload permission (${err.message}). Verify your account and channel status."
                else -> err.message ?: "YouTube API error (${err.httpCode})."
            }
            is SecurityException -> err.localizedMessage ?: "Authorization or Keystore error."
            is IOException -> err.localizedMessage ?: "File or network error during YouTube upload."
            else -> err.localizedMessage ?: "YouTube upload failed."
        }
    }

    fun clearAuthFeedback() {
        _lastAuthFeedback.value = null
    }

    fun resetPublishStatuses() {
        statusStore.clearAll()
        _publishStatuses.value = PublishPlatform.entries.associateWith { PlatformPublishStatus.Idle }
    }

    internal fun updatePlatformStatus(platform: PublishPlatform, status: PlatformPublishStatus) {
        statusStore.saveStatus(platform, status)
        _publishStatuses.update { map ->
            map.toMutableMap().apply { put(platform, status) }
        }
    }

    fun enqueueDirectPublish(
        platforms: Set<PublishPlatform>,
        metadata: PublishVideoMetadata
    ): List<PublishPlatform> {
        val activeSessions = sessionStore.getAllSessions()
        val authorizedTargets = platforms.filter { activeSessions[it] != null }
        if (authorizedTargets.isEmpty()) return emptyList()

        authorizedTargets.forEach { platform ->
            updatePlatformStatus(
                platform,
                PlatformPublishStatus.Queued("Scheduled upload for ${platform.officialTargetName}...")
            )
        }

        val inputData = Data.Builder()
            .putString(VideoPublishWorker.KEY_LOCAL_VIDEO_PATH, metadata.localVideoPath)
            .putString(VideoPublishWorker.KEY_TITLE, metadata.title)
            .putString(VideoPublishWorker.KEY_DESCRIPTION, metadata.description)
            .putString(VideoPublishWorker.KEY_HASHTAGS, metadata.hashtags)
            .putString(VideoPublishWorker.KEY_BIBLE_VERSE, metadata.bibleVerse)
            .putString(VideoPublishWorker.KEY_PRIVACY, metadata.privacy.name)
            .putString(VideoPublishWorker.KEY_ASPECT_RATIO, metadata.aspectRatio)
            .putString(VideoPublishWorker.KEY_CODEC_LABEL, metadata.codecLabel)
            .putBoolean(VideoPublishWorker.KEY_ALLOW_COMMENTS, metadata.allowComments)
            .putBoolean(VideoPublishWorker.KEY_ALLOW_DUET, metadata.allowDuet)
            .putBoolean(VideoPublishWorker.KEY_ALLOW_STITCH, metadata.allowStitch)
            .putStringArray(
                VideoPublishWorker.KEY_PLATFORMS,
                authorizedTargets.map { it.key }.toTypedArray()
            )
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<VideoPublishWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()

        val workManager = WorkManager.getInstance(appContext)
        workManager.enqueueUniqueWork(
            "wltv_direct_publish_batch",
            ExistingWorkPolicy.REPLACE,
            request
        )

        repoScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { workInfo ->
                if (workInfo != null) {
                    _publishStatuses.value = statusStore.getAllStatuses()
                    if (workInfo.state == WorkInfo.State.FAILED) {
                        val failureMessage = workInfo.outputData.getString(VideoPublishWorker.KEY_ERROR_MESSAGE)
                            ?: "Upload worker failed."
                        authorizedTargets.forEach { p ->
                            val existing = statusStore.getStatus(p)
                            if (existing is PlatformPublishStatus.Queued || existing is PlatformPublishStatus.Uploading) {
                                updatePlatformStatus(p, PlatformPublishStatus.Failed(failureMessage))
                            }
                        }
                    }
                }
            }
        }

        return authorizedTargets
    }

    internal suspend fun executePlatformUpload(
        platform: PublishPlatform,
        metadata: PublishVideoMetadata
    ): PlatformPublishStatus {
        val currentSession = sessionStore.getSession(platform)
            ?: return PlatformPublishStatus.Failed(
                errorMessage = "${platform.displayName} is not authorized.",
                requiresReauth = true
            ).also { updatePlatformStatus(platform, it) }

        val validSession = tokenBrokerClient.refreshSessionIfNeeded(
            session = currentSession,
            config = envConfig,
            youtubeRefresherOverride = { youTubeAuthAdapter.refreshAccessTokenSilently(appContext) }
        ).getOrElse { err ->
            val failed = PlatformPublishStatus.Failed(
                errorMessage = err.localizedMessage ?: "Authorization expired.",
                requiresReauth = true
            )
            updatePlatformStatus(platform, failed)
            return failed
        }

        if (validSession != currentSession) {
            sessionStore.saveSession(validSession)
            refreshLocalSessions()
        }

        val onProgress: (Int, String) -> Unit = { pct, label ->
            updatePlatformStatus(platform, PlatformPublishStatus.Uploading(pct, label))
        }

        val uploadResult = when (platform) {
            PublishPlatform.YOUTUBE -> {
                val firstTry = youtubeUploader.uploadVideo(
                    accessToken = validSession.accessToken,
                    metadata = metadata,
                    onProgress = onProgress
                )
                val firstErr = firstTry.exceptionOrNull()
                if (firstErr is YouTubeResumableUploader.YouTubeApiException && firstErr.httpCode == 401) {
                    val refreshed = youTubeAuthAdapter.refreshAccessTokenSilently(appContext)
                    if (refreshed.isSuccess) {
                        val updatedSession = validSession.copy(
                            accessToken = refreshed.getOrThrow(),
                            expiresAtEpochMs = System.currentTimeMillis() +
                                GoogleYouTubeAuthorizationManager.DEFAULT_ACCESS_TOKEN_TTL_MS
                        )
                        runCatching { sessionStore.saveSession(updatedSession) }
                        refreshLocalSessions()
                        youtubeUploader.uploadVideo(
                            accessToken = updatedSession.accessToken,
                            metadata = metadata,
                            onProgress = onProgress
                        )
                    } else {
                        firstTry
                    }
                } else {
                    firstTry
                }
            }
            PublishPlatform.FACEBOOK -> {
                val selectedPage = validSession.availablePages.firstOrNull {
                    it.pageId == validSession.targetPageId && it.hasValidPublishingCredentials()
                }
                if (selectedPage == null) {
                    Result.failure(
                        SecurityException("Select a valid Word & Light TV Facebook Page with a Page token before publishing.")
                    )
                } else {
                    metaUploader.uploadToPage(
                        pageId = selectedPage.pageId,
                        pageAccessToken = selectedPage.pageAccessToken,
                        metadata = metadata,
                        onProgress = onProgress
                    )
                }
            }
            PublishPlatform.TIKTOK -> tikTokUploader.uploadVideo(
                accessToken = validSession.accessToken,
                metadata = metadata,
                creatorInfoOverride = _tikTokCreatorInfo.value,
                channelHandleHint = validSession.channelHandle,
                onProgress = onProgress
            )
        }

        val finalStatus = uploadResult.fold(
            onSuccess = { it },
            onFailure = { err ->
                PlatformPublishStatus.Failed(
                    errorMessage = if (platform == PublishPlatform.YOUTUBE) {
                        mapFriendlyUploadError(err)
                    } else {
                        err.localizedMessage ?: "${platform.displayName} upload failed."
                    },
                    requiresReauth = err is SecurityException ||
                        (err is YouTubeResumableUploader.YouTubeApiException && err.isAuthError) ||
                        err.localizedMessage?.contains("401") == true ||
                        err.localizedMessage?.contains("403") == true
                )
            }
        )
        updatePlatformStatus(platform, finalStatus)
        return finalStatus
    }

    companion object {
        @Volatile
        private var sharedInstance: PublishingRepository? = null

        fun getInstance(context: Context): PublishingRepository {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: PublishingRepository(context.applicationContext).also {
                    sharedInstance = it
                }
            }
        }
    }
}
