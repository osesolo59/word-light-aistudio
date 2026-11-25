package com.aistudio.aimusic.wltv.jqr8x.data.social

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import com.aistudio.aimusic.wltv.jqr8x.R
import java.net.URI
import java.util.Locale

const val VERIFIED_YOUTUBE_CHANNEL_URL = "https://www.youtube.com/@WordAndLightTV-v6c"
const val VERIFIED_FACEBOOK_SHARE_URL = "https://www.facebook.com/share/1MA2B9gcqv/"
const val VERIFIED_TIKTOK_PROFILE_URL = "https://www.tiktok.com/@oseisolomon7"
const val VERIFIED_WHATSAPP_CONTACT_NUMBER = "+233245200758"
const val VERIFIED_WHATSAPP_URL = "https://wa.me/233245200758"

enum class SocialPlatform(
    val key: String,
    val displayName: String,
    val description: String,
    val placeholderHint: String,
    val defaultUrl: String,
    @param:DrawableRes val iconRes: Int
) {
    YOUTUBE(
        key = "youtube",
        displayName = "YouTube",
        description = "Watch official broadcasts, worship sessions & releases",
        placeholderHint = "https://www.youtube.com/@...",
        defaultUrl = VERIFIED_YOUTUBE_CHANNEL_URL,
        iconRes = R.drawable.ic_social_youtube
    ),
    FACEBOOK(
        key = "facebook",
        displayName = "Facebook",
        description = "Follow community fellowship & live announcements",
        placeholderHint = "https://www.facebook.com/...",
        defaultUrl = VERIFIED_FACEBOOK_SHARE_URL,
        iconRes = R.drawable.ic_social_facebook
    ),
    TIKTOK(
        key = "tiktok",
        displayName = "TikTok",
        description = "Short worship clips & ministry highlights",
        placeholderHint = "https://www.tiktok.com/@...",
        defaultUrl = VERIFIED_TIKTOK_PROFILE_URL,
        iconRes = R.drawable.ic_social_tiktok
    ),
    INSTAGRAM(
        key = "instagram",
        displayName = "Instagram",
        description = "Ministry reels, devotionals & event updates",
        placeholderHint = "https://www.instagram.com/...",
        defaultUrl = "",
        iconRes = R.drawable.ic_social_instagram
    ),
    WHATSAPP(
        key = "whatsapp",
        displayName = "WhatsApp",
        description = "Word & Light TV • Official Ministry Support ($VERIFIED_WHATSAPP_CONTACT_NUMBER)",
        placeholderHint = "https://wa.me/...",
        defaultUrl = VERIFIED_WHATSAPP_URL,
        iconRes = R.drawable.ic_social_whatsapp
    ),
    TELEGRAM(
        key = "telegram",
        displayName = "Telegram",
        description = "Join ministry updates & daily scripture notes",
        placeholderHint = "https://t.me/...",
        defaultUrl = "",
        iconRes = R.drawable.ic_social_telegram
    ),
    X_TWITTER(
        key = "x_twitter",
        displayName = "X (Twitter)",
        description = "Ministry updates, scriptures & live alerts",
        placeholderHint = "https://x.com/...",
        defaultUrl = "",
        iconRes = R.drawable.ic_social_x
    ),
    OFFICIAL_WEBSITE(
        key = "official_website",
        displayName = "Official Website",
        description = "Visit the official ministry website",
        placeholderHint = "https://...",
        defaultUrl = "",
        iconRes = R.drawable.ic_social_website
    )
}

data class ConfiguredSocialLink(
    val platform: SocialPlatform,
    val url: String
) {
    val displayHostAndPath: String
        get() = url.removePrefix("https://").removePrefix("www.").trimEnd('/')
}

sealed class SocialUrlValidationResult {
    data object Empty : SocialUrlValidationResult()
    data class Valid(val normalizedUrl: String) : SocialUrlValidationResult()
    data class Invalid(val errorMessage: String) : SocialUrlValidationResult()
}

sealed class ExternalLinkOpenResult {
    data class Opened(val url: String) : ExternalLinkOpenResult()
    data class InvalidUrl(val reason: String) : ExternalLinkOpenResult()
    data class NoAppAvailable(val url: String) : ExternalLinkOpenResult()
}

object SocialLinkValidator {

    private val hostPattern = Regex("^[a-z0-9.-]+$")

    fun validate(rawUrl: String): SocialUrlValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return SocialUrlValidationResult.Empty
        }

        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) {
            return SocialUrlValidationResult.Invalid("URL cannot contain spaces or control characters")
        }

        val parsed = try {
            URI(trimmed)
        } catch (_: Exception) {
            return SocialUrlValidationResult.Invalid("Enter a valid https:// web address")
        }

        val scheme = parsed.scheme?.lowercase(Locale.US)
        if (scheme != "https") {
            return if (scheme == "http") {
                SocialUrlValidationResult.Invalid("Use a secure https:// link")
            } else {
                SocialUrlValidationResult.Invalid("Only secure https:// links are allowed")
            }
        }

        if (parsed.rawUserInfo != null) {
            return SocialUrlValidationResult.Invalid("URL must not contain embedded credentials")
        }

        val host = parsed.host?.lowercase(Locale.US)
            ?: return SocialUrlValidationResult.Invalid("Enter a valid domain name after https://")

        if (!hostPattern.matches(host) ||
            !host.contains('.') ||
            host.startsWith('.') ||
            host.endsWith('.') ||
            host.contains("..") ||
            host == "localhost" ||
            host.endsWith(".local")
        ) {
            return SocialUrlValidationResult.Invalid("Enter a valid public domain (e.g., https://example.com)")
        }

        if (parsed.port != -1 && parsed.port !in 1..65535) {
            return SocialUrlValidationResult.Invalid("Invalid port in URL")
        }

        val normalized = buildString {
            append("https://")
            append(host)
            if (parsed.port != -1) {
                append(':').append(parsed.port)
            }
            parsed.rawPath?.let { append(it) }
            parsed.rawQuery?.let { append('?').append(it) }
            parsed.rawFragment?.let { append('#').append(it) }
        }

        return SocialUrlValidationResult.Valid(normalized)
    }
}

object SecureExternalLinkOpener {

    fun buildSafeExternalIntent(rawUrl: String): Intent? {
        val validation = SocialLinkValidator.validate(rawUrl)
        if (validation !is SocialUrlValidationResult.Valid) {
            return null
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(validation.normalizedUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }

    fun openExternalUrl(context: Context, rawUrl: String): ExternalLinkOpenResult {
        val validation = SocialLinkValidator.validate(rawUrl)
        if (validation !is SocialUrlValidationResult.Valid) {
            val reason = when (validation) {
                SocialUrlValidationResult.Empty -> "This social media link has not been configured yet."
                is SocialUrlValidationResult.Invalid -> validation.errorMessage
                is SocialUrlValidationResult.Valid -> ""
            }
            return ExternalLinkOpenResult.InvalidUrl(reason)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validation.normalizedUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            context.startActivity(intent)
            ExternalLinkOpenResult.Opened(validation.normalizedUrl)
        } catch (_: ActivityNotFoundException) {
            ExternalLinkOpenResult.NoAppAvailable(validation.normalizedUrl)
        } catch (_: SecurityException) {
            ExternalLinkOpenResult.NoAppAvailable(validation.normalizedUrl)
        } catch (_: Exception) {
            ExternalLinkOpenResult.NoAppAvailable(validation.normalizedUrl)
        }
    }
}
