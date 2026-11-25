package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import androidx.annotation.DrawableRes
import com.aistudio.aimusic.wltv.jqr8x.BuildConfig
import com.aistudio.aimusic.wltv.jqr8x.R
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialLinkValidator
import com.aistudio.aimusic.wltv.jqr8x.data.social.SocialUrlValidationResult
import com.aistudio.aimusic.wltv.jqr8x.data.social.VERIFIED_FACEBOOK_SHARE_URL
import com.aistudio.aimusic.wltv.jqr8x.data.social.VERIFIED_TIKTOK_PROFILE_URL
import com.aistudio.aimusic.wltv.jqr8x.data.social.VERIFIED_YOUTUBE_CHANNEL_URL

enum class PublishPlatform(
    val key: String,
    val displayName: String,
    val officialTargetName: String,
    val officialHandleOrLabel: String,
    val verifiedUrl: String,
    val requiresBrokerBackend: Boolean,
    @param:DrawableRes val iconRes: Int
) {
    YOUTUBE(
        key = "youtube",
        displayName = "YouTube",
        officialTargetName = "Word & Light TV",
        officialHandleOrLabel = "@WordAndLightTV-v6c • Google AuthorizationClient",
        verifiedUrl = VERIFIED_YOUTUBE_CHANNEL_URL,
        requiresBrokerBackend = false,
        iconRes = R.drawable.ic_social_youtube
    ),
    FACEBOOK(
        key = "facebook",
        displayName = "Facebook Page",
        officialTargetName = "Word & Light TV Page",
        officialHandleOrLabel = "Meta Graph Page Publish",
        verifiedUrl = VERIFIED_FACEBOOK_SHARE_URL,
        requiresBrokerBackend = true,
        iconRes = R.drawable.ic_social_facebook
    ),
    TIKTOK(
        key = "tiktok",
        displayName = "TikTok",
        officialTargetName = "@oseisolomon7",
        officialHandleOrLabel = "TikTok Content Posting API",
        verifiedUrl = VERIFIED_TIKTOK_PROFILE_URL,
        requiresBrokerBackend = true,
        iconRes = R.drawable.ic_social_tiktok
    );

    companion object {
        fun fromKey(key: String?): PublishPlatform? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

enum class PublishPrivacy(
    val label: String,
    val youtubeStatus: String,
    val tiktokPrivacyLevel: String
) {
    PRIVATE("Private / Draft", "private", "SELF_ONLY"),
    UNLISTED("Unlisted / Followers", "unlisted", "FOLLOWER_OF_CREATOR"),
    PUBLIC("Public Broadcast", "public", "PUBLIC_TO_EVERYONE");

    companion object {
        fun fromName(name: String?): PublishPrivacy =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PRIVATE

        fun fromTikTokOption(option: String): PublishPrivacy? = when (option.uppercase()) {
            "SELF_ONLY" -> PRIVATE
            "FOLLOWER_OF_CREATOR", "MUTUAL_FOLLOW_FRIENDS" -> UNLISTED
            "PUBLIC_TO_EVERYONE" -> PUBLIC
            else -> null
        }
    }
}

data class TikTokCreatorInfo(
    val creatorNickname: String = "",
    val creatorUsername: String = "",
    val privacyLevelOptions: List<String> = emptyList(),
    val commentDisabled: Boolean = false,
    val duetDisabled: Boolean = false,
    val stitchDisabled: Boolean = false,
    val maxVideoPostDurationSec: Int = 600
) {
    fun allowedPrivacies(): List<PublishPrivacy> {
        val mapped = privacyLevelOptions.mapNotNull { PublishPrivacy.fromTikTokOption(it) }.distinct()
        return mapped.ifEmpty { listOf(PublishPrivacy.PRIVATE) }
    }

    fun isSandboxRestrictedOnly(): Boolean =
        privacyLevelOptions.isEmpty() || privacyLevelOptions.all { it.equals("SELF_ONLY", ignoreCase = true) }

    fun effectiveTikTokPrivacyLevel(requested: PublishPrivacy): String {
        val desired = requested.tiktokPrivacyLevel
        if (privacyLevelOptions.any { it.equals(desired, ignoreCase = true) }) {
            return desired
        }
        return if (privacyLevelOptions.any { it.equals("SELF_ONLY", ignoreCase = true) }) {
            "SELF_ONLY"
        } else {
            privacyLevelOptions.firstOrNull() ?: "SELF_ONLY"
        }
    }
}

data class PublishingEnvironmentConfig(
    val youtubeClientId: String = "",
    val metaAppId: String = "",
    val tiktokClientKey: String = "",
    val socialBrokerBaseUrl: String = "",
    val privacyPolicyUrl: String = "",
    val termsOfServiceUrl: String = ""
) {
    val validBrokerBaseUrl: String
        get() = validateHttpsUrl(socialBrokerBaseUrl)

    val validPrivacyPolicyUrl: String
        get() = validateHttpsUrl(privacyPolicyUrl)

    val validTermsOfServiceUrl: String
        get() = validateHttpsUrl(termsOfServiceUrl)

    private fun validateHttpsUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return when (val check = SocialLinkValidator.validate(trimmed)) {
            is SocialUrlValidationResult.Valid -> check.normalizedUrl.trimEnd('/')
            else -> ""
        }
    }

    fun isPlatformOAuthReady(platform: PublishPlatform): Boolean = when (platform) {
        // Google AuthorizationClient uses the Android OAuth client bound by package name + SHA-1 in google-services.json.
        PublishPlatform.YOUTUBE -> true
        PublishPlatform.FACEBOOK -> metaAppId.isNotBlank() && validBrokerBaseUrl.isNotBlank()
        PublishPlatform.TIKTOK -> tiktokClientKey.isNotBlank() && validBrokerBaseUrl.isNotBlank()
    }

    fun missingConfigRequirements(platform: PublishPlatform): List<String> = when (platform) {
        PublishPlatform.YOUTUBE -> emptyList()
        PublishPlatform.FACEBOOK -> buildList {
            if (metaAppId.isBlank()) add("WLTV_META_APP_ID (Meta Developer App ID)")
            if (validBrokerBaseUrl.isBlank()) add("WLTV_SOCIAL_BROKER_URL (Free-tier Cloudflare Worker HTTPS URL)")
        }
        PublishPlatform.TIKTOK -> buildList {
            if (tiktokClientKey.isBlank()) add("WLTV_TIKTOK_CLIENT_KEY (TikTok Developer Client Key)")
            if (validBrokerBaseUrl.isBlank()) add("WLTV_SOCIAL_BROKER_URL (Free-tier Cloudflare Worker HTTPS URL)")
        }
    }

    companion object {
        fun fromBuildConfig(): PublishingEnvironmentConfig = PublishingEnvironmentConfig(
            youtubeClientId = BuildConfig.YOUTUBE_OAUTH_CLIENT_ID.trim(),
            metaAppId = BuildConfig.META_APP_ID.trim(),
            tiktokClientKey = BuildConfig.TIKTOK_CLIENT_KEY.trim(),
            socialBrokerBaseUrl = BuildConfig.SOCIAL_BROKER_BASE_URL.trim(),
            privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL.trim(),
            termsOfServiceUrl = BuildConfig.TERMS_OF_SERVICE_URL.trim()
        )
    }
}

data class FacebookPageTarget(
    val pageId: String,
    val pageName: String,
    val category: String = "",
    val pageAccessToken: String = ""
) {
    fun hasValidPublishingCredentials(): Boolean =
        pageId.isNotBlank() && NUMERIC_ID_REGEX.matches(pageId.trim()) && pageAccessToken.isNotBlank()

    companion object {
        private val NUMERIC_ID_REGEX = Regex("^\\d+$")
    }
}

data class ConnectedChannelSession(
    val platform: PublishPlatform,
    val accessToken: String,
    val refreshToken: String = "",
    val expiresAtEpochMs: Long = 0L,
    val accountId: String = "",
    val channelDisplayName: String = "",
    val channelHandle: String = "",
    val targetPageId: String = "",
    val targetPageName: String = "",
    val availablePages: List<FacebookPageTarget> = emptyList(),
    val scopes: List<String> = emptyList(),
    val connectedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun isExpiredOrExpiringSoon(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (expiresAtEpochMs <= 0L) return false
        return nowMs >= (expiresAtEpochMs - 120_000L)
    }

    fun effectiveDisplayLabel(): String = when (platform) {
        PublishPlatform.YOUTUBE -> channelDisplayName.ifBlank { platform.officialTargetName }
        PublishPlatform.FACEBOOK -> targetPageName.ifBlank {
            channelDisplayName.ifBlank { platform.officialTargetName }
        }
        PublishPlatform.TIKTOK -> channelHandle.ifBlank {
            channelDisplayName.ifBlank { platform.officialTargetName }
        }
    }
}

data class PendingOAuthState(
    val platform: PublishPlatform,
    val state: String,
    val codeVerifier: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis(), ttlMs: Long = PENDING_OAUTH_TTL_MS): Boolean {
        if (createdAtEpochMs <= 0L) return true
        val elapsed = nowMs - createdAtEpochMs
        return elapsed < 0L || elapsed > ttlMs
    }

    companion object {
        const val PENDING_OAUTH_TTL_MS = 10L * 60L * 1000L // 10 minutes
    }
}

data class PublishVideoMetadata(
    val localVideoPath: String,
    val title: String,
    val description: String,
    val hashtags: String = "#WordAndLightTV #GospelMusic #Psalm119105",
    val bibleVerse: String = "",
    val privacy: PublishPrivacy = PublishPrivacy.PRIVATE,
    val aspectRatio: String = "16:9",
    val codecLabel: String = "H.264",
    val allowComments: Boolean = true,
    val allowDuet: Boolean = true,
    val allowStitch: Boolean = true,
    val localThumbnailPath: String = ""
) {
    fun parsedYouTubeTags(): List<String> = hashtags
        .split(",", "\n", " ")
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotBlank() }
        .distinct()
        .take(30)

    fun buildFormattedDescription(): String = buildString {
        if (description.isNotBlank()) {
            appendLine(description.trim())
        } else {
            appendLine(title.ifBlank { "Word & Light TV Broadcast" })
        }
        if (bibleVerse.isNotBlank()) {
            appendLine()
            appendLine("Scripture: ${bibleVerse.trim()}")
        }
        if (hashtags.isNotBlank()) {
            appendLine()
            appendLine(hashtags.trim())
        }
        appendLine()
        append("Published via Word & Light AI STUDIO • Christians Strong Foundation Ministry")
    }.trim()
}

sealed class PlatformPublishStatus {
    data object Idle : PlatformPublishStatus()
    data class Queued(val stepLabel: String = "Queued for upload...") : PlatformPublishStatus()
    data class Uploading(
        val progressPercent: Int,
        val stepLabel: String
    ) : PlatformPublishStatus()

    data class Processing(val stepLabel: String) : PlatformPublishStatus()
    data class Published(
        val remoteId: String,
        val remoteUrl: String,
        val visibilityNote: String
    ) : PlatformPublishStatus()

    data class Failed(
        val errorMessage: String,
        val requiresReauth: Boolean = false
    ) : PlatformPublishStatus()
}

sealed class YouTubeUploadUiState {
    data object Idle : YouTubeUploadUiState()
    data class Uploading(
        val progressPercent: Int,
        val stepLabel: String
    ) : YouTubeUploadUiState()

    data class Success(
        val videoId: String,
        val videoUrl: String,
        val visibilityNote: String
    ) : YouTubeUploadUiState()

    data class Cancelled(
        val message: String = "YouTube upload cancelled."
    ) : YouTubeUploadUiState()

    data class Error(
        val message: String,
        val requiresReauth: Boolean = false
    ) : YouTubeUploadUiState()
}
