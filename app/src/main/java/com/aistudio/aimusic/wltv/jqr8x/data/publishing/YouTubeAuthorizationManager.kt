package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

sealed class YouTubeAuthOutcome {
    data class Authorized(
        val accessToken: String,
        val grantedScopes: List<String>
    ) : YouTubeAuthOutcome()

    data class NeedsUserResolution(
        val pendingIntent: PendingIntent
    ) : YouTubeAuthOutcome()

    data class Failed(
        val message: String,
        val requiresReauth: Boolean = true
    ) : YouTubeAuthOutcome()
}

interface YouTubeAuthorizationClientAdapter {
    fun buildUploadAuthorizationRequest(): AuthorizationRequest
    suspend fun authorize(activity: Activity): YouTubeAuthOutcome
    fun extractResultFromIntent(context: Context, data: Intent?): YouTubeAuthOutcome
    suspend fun refreshAccessTokenSilently(context: Context): Result<String>
}

/**
 * Implements Google's officially supported Android Identity AuthorizationClient flow for YouTube.
 * - Uses ONLY https://www.googleapis.com/auth/youtube.upload (no unnecessary sensitive scopes).
 * - Uses the existing Android OAuth client bound by package name + SHA-1 in Google Cloud Console.
 * - Eliminates insecure custom URI scheme redirects for Google OAuth.
 */
object GoogleYouTubeAuthorizationManager : YouTubeAuthorizationClientAdapter {

    const val YOUTUBE_UPLOAD_SCOPE = "https://www.googleapis.com/auth/youtube.upload"
    // Conservative 50-minute local validity hint; AuthorizationClient returns fresh tokens silently while granted.
    const val DEFAULT_ACCESS_TOKEN_TTL_MS = 50L * 60L * 1000L

    override fun buildUploadAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_UPLOAD_SCOPE)))
            .build()
    }

    override suspend fun authorize(activity: Activity): YouTubeAuthOutcome {
        return runCatching {
            val request = buildUploadAuthorizationRequest()
            val result = Identity.getAuthorizationClient(activity).authorize(request).await()
            mapAuthorizationResult(result)
        }.getOrElse { err ->
            YouTubeAuthOutcome.Failed(
                message = err.localizedMessage ?: "Google YouTube authorization failed.",
                requiresReauth = true
            )
        }
    }

    override fun extractResultFromIntent(context: Context, data: Intent?): YouTubeAuthOutcome {
        if (data == null) {
            return YouTubeAuthOutcome.Failed("Google YouTube authorization was cancelled.")
        }
        return runCatching {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            mapAuthorizationResult(result)
        }.getOrElse { err ->
            YouTubeAuthOutcome.Failed(
                message = err.localizedMessage ?: "Failed to read Google YouTube authorization result."
            )
        }
    }

    override suspend fun refreshAccessTokenSilently(context: Context): Result<String> {
        return runCatching {
            val request = buildUploadAuthorizationRequest()
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            if (result.hasResolution()) {
                throw IllegalStateException("YouTube permission needs user confirmation. Please re-authorize YouTube.")
            }
            val token = result.accessToken?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Google AuthorizationClient did not return a YouTube access token.")
            if (!hasUploadScope(result.grantedScopes)) {
                throw IllegalStateException("YouTube upload permission ($YOUTUBE_UPLOAD_SCOPE) was not granted.")
            }
            token
        }
    }

    internal fun mapAuthorizationResult(result: AuthorizationResult): YouTubeAuthOutcome {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
            return if (pendingIntent != null) {
                YouTubeAuthOutcome.NeedsUserResolution(pendingIntent)
            } else {
                YouTubeAuthOutcome.Failed("Google YouTube consent intent was unavailable.")
            }
        }
        val token = result.accessToken.orEmpty()
        if (token.isBlank()) {
            return YouTubeAuthOutcome.Failed("No YouTube access token returned.")
        }
        val scopes = result.grantedScopes.toList()
        if (!hasUploadScope(scopes)) {
            return YouTubeAuthOutcome.Failed("Required scope $YOUTUBE_UPLOAD_SCOPE was not granted.")
        }
        return YouTubeAuthOutcome.Authorized(
            accessToken = token,
            grantedScopes = scopes
        )
    }

    internal fun hasUploadScope(grantedScopes: Collection<String>?): Boolean {
        if (grantedScopes.isNullOrEmpty()) return false
        return grantedScopes.any { it.equals(YOUTUBE_UPLOAD_SCOPE, ignoreCase = true) }
    }
}
