package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import java.security.MessageDigest
import java.security.SecureRandom

data class OAuthLaunchRequest(
    val platform: PublishPlatform,
    val authorizationUri: Uri,
    val pendingState: PendingOAuthState,
    val redirectUri: String
)

data class OAuthCallbackPayload(
    val platform: PublishPlatform?,
    val code: String?,
    val state: String?,
    val error: String?,
    val errorDescription: String?
)

/**
 * Implements RFC 7636 OAuth 2.0 Authorization Code + PKCE request building for Meta and TikTok.
 * Google YouTube authorization is handled exclusively by Google Play Services AuthorizationClient
 * (YouTubeAuthorizationManager) and does NOT use custom-scheme browser redirects.
 */
object OAuthPkceManager {

    const val REDIRECT_SCHEME = "com.aistudio.aimusic.wltv.jqr8x"
    const val REDIRECT_HOST = "oauth2redirect"

    private val secureRandom = SecureRandom()

    fun generateCodeVerifier(byteLength: Int = 64): String {
        val bytes = ByteArray(byteLength.coerceIn(32, 96))
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun computeCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateState(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun redirectUriFor(platform: PublishPlatform, config: PublishingEnvironmentConfig): String = when (platform) {
        PublishPlatform.YOUTUBE -> throw UnsupportedOperationException(
            "YouTube OAuth uses Google AuthorizationClient and does not accept custom URI schemes."
        )
        PublishPlatform.FACEBOOK -> {
            val broker = config.validBrokerBaseUrl
            if (broker.isNotBlank()) "$broker/oauth/meta/callback" else "$REDIRECT_SCHEME://$REDIRECT_HOST/facebook"
        }
        PublishPlatform.TIKTOK -> {
            val broker = config.validBrokerBaseUrl
            if (broker.isNotBlank()) "$broker/oauth/tiktok/callback" else "$REDIRECT_SCHEME://$REDIRECT_HOST/tiktok"
        }
    }

    fun buildAuthorizationRequest(
        platform: PublishPlatform,
        config: PublishingEnvironmentConfig
    ): Result<OAuthLaunchRequest> {
        if (platform == PublishPlatform.YOUTUBE) {
            return Result.failure(
                UnsupportedOperationException(
                    "YouTube uses Google Identity AuthorizationClient instead of browser custom-scheme OAuth."
                )
            )
        }
        if (!config.isPlatformOAuthReady(platform)) {
            val missing = config.missingConfigRequirements(platform).joinToString(", ")
            return Result.failure(
                IllegalStateException("${platform.displayName} OAuth requires configuration: $missing")
            )
        }

        val verifier = generateCodeVerifier()
        val challenge = computeCodeChallenge(verifier)
        val state = generateState()
        val redirectUri = redirectUriFor(platform, config)

        val uri = when (platform) {
            PublishPlatform.FACEBOOK -> Uri.parse("https://www.facebook.com/v21.0/dialog/oauth")
                .buildUpon()
                .appendQueryParameter("client_id", config.metaAppId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter(
                    "scope",
                    "pages_show_list,pages_read_engagement,pages_manage_posts"
                )
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .build()

            PublishPlatform.TIKTOK -> Uri.parse("https://www.tiktok.com/v2/auth/authorize/")
                .buildUpon()
                .appendQueryParameter("client_key", config.tiktokClientKey)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "user.info.basic,video.publish")
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .build()

            PublishPlatform.YOUTUBE -> error("Unreachable")
        }

        val pending = PendingOAuthState(
            platform = platform,
            state = state,
            codeVerifier = verifier
        )

        return Result.success(
            OAuthLaunchRequest(
                platform = platform,
                authorizationUri = uri,
                pendingState = pending,
                redirectUri = redirectUri
            )
        )
    }

    fun launchAuthorization(context: Context, uri: Uri): Result<Unit> {
        return runCatching {
            val colors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(0xFF4A148C.toInt())
                .build()
            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colors)
                .setShowTitle(true)
                .build()
            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, uri)
        }.recoverCatching {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun isOAuthRedirectUri(uri: Uri?): Boolean {
        if (uri == null) return false
        if (!uri.scheme.equals(REDIRECT_SCHEME, ignoreCase = true) ||
            !uri.host.equals(REDIRECT_HOST, ignoreCase = true)
        ) {
            return false
        }
        val segment = uri.pathSegments.firstOrNull()?.lowercase()
        return segment == PublishPlatform.FACEBOOK.key || segment == PublishPlatform.TIKTOK.key
    }

    fun parseRedirectUri(uri: Uri): OAuthCallbackPayload {
        val segment = uri.pathSegments.firstOrNull()?.lowercase()
        val platform = when (segment) {
            PublishPlatform.FACEBOOK.key -> PublishPlatform.FACEBOOK
            PublishPlatform.TIKTOK.key -> PublishPlatform.TIKTOK
            else -> null
        }
        return OAuthCallbackPayload(
            platform = platform,
            code = uri.getQueryParameter("code"),
            state = uri.getQueryParameter("state"),
            error = uri.getQueryParameter("error"),
            errorDescription = uri.getQueryParameter("error_description")
        )
    }
}
