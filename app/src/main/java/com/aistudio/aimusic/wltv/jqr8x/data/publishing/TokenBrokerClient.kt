package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Exchanges OAuth authorization codes and refreshes scoped upload tokens.
 * - YouTube refreshes short-lived access tokens silently through Google Identity AuthorizationClient.
 * - Meta & TikTok delegate code/refresh exchanges to the free-tier Cloudflare Worker Token Broker,
 *   authenticated via the signed-in user's Firebase ID token so client_secrets remain strictly server-side.
 * - Meta Page publishing never falls back to a personal user token; a valid numeric Page ID and Page token
 *   are always required.
 */
class TokenBrokerClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val firebaseIdTokenProvider: suspend (forceRefresh: Boolean) -> String? = { forceRefresh ->
        runCatching { Firebase.auth.currentUser?.getIdToken(forceRefresh)?.await()?.token }.getOrNull()
    },
    private val youtubeTokenRefresher: suspend () -> Result<String> = {
        Result.failure(
            IllegalStateException("YouTube silent token refresh requires AuthorizationClient context.")
        )
    }
) {

    suspend fun exchangeAuthorizationCode(
        platform: PublishPlatform,
        code: String,
        codeVerifier: String,
        config: PublishingEnvironmentConfig
    ): Result<ConnectedChannelSession> = withContext(Dispatchers.IO) {
        when (platform) {
            PublishPlatform.YOUTUBE -> Result.failure(
                UnsupportedOperationException(
                    "YouTube authorization must use Google Identity AuthorizationClient."
                )
            )
            PublishPlatform.FACEBOOK -> exchangeMetaCodeViaBroker(code, codeVerifier, config)
            PublishPlatform.TIKTOK -> exchangeTikTokCodeViaBroker(code, codeVerifier, config)
        }
    }

    suspend fun refreshSessionIfNeeded(
        session: ConnectedChannelSession,
        config: PublishingEnvironmentConfig,
        youtubeRefresherOverride: (suspend () -> Result<String>)? = null
    ): Result<ConnectedChannelSession> = withContext(Dispatchers.IO) {
        if (!session.isExpiredOrExpiringSoon()) {
            return@withContext Result.success(session)
        }
        when (session.platform) {
            PublishPlatform.YOUTUBE -> {
                val refresher = youtubeRefresherOverride ?: youtubeTokenRefresher
                refresher().map { freshToken ->
                    session.copy(
                        accessToken = freshToken,
                        expiresAtEpochMs = System.currentTimeMillis() +
                            GoogleYouTubeAuthorizationManager.DEFAULT_ACCESS_TOKEN_TTL_MS
                    )
                }
            }
            PublishPlatform.TIKTOK -> refreshTikTokViaBroker(session, config)
            PublishPlatform.FACEBOOK -> Result.failure(
                IllegalStateException("Facebook Page authorization expired. Reconnect via official OAuth.")
            )
        }
    }

    private suspend fun exchangeMetaCodeViaBroker(
        code: String,
        codeVerifier: String,
        config: PublishingEnvironmentConfig
    ): Result<ConnectedChannelSession> = runCatching {
        val broker = config.validBrokerBaseUrl
        if (broker.isBlank()) {
            throw IllegalStateException("Social Token Broker HTTPS URL is not configured.")
        }

        val payloadJson = JSONObject().apply {
            put("code", code)
            put("codeVerifier", codeVerifier)
            put("redirectUri", OAuthPkceManager.redirectUriFor(PublishPlatform.FACEBOOK, config))
        }.toString()

        val json = executeAuthenticatedBrokerPost("$broker/oauth/meta/exchange", payloadJson)
        val pagesArray = json.optJSONArray("pages")
        val pages = buildList {
            if (pagesArray != null) {
                for (i in 0 until pagesArray.length()) {
                    val item = pagesArray.optJSONObject(i) ?: continue
                    val target = FacebookPageTarget(
                        pageId = item.optString("id").trim(),
                        pageName = item.optString("name", "Word & Light TV Page").trim(),
                        category = item.optString("category").trim(),
                        pageAccessToken = item.optString("accessToken").trim()
                    )
                    if (target.hasValidPublishingCredentials()) {
                        add(target)
                    }
                }
            }
        }
        val preferredPage = pages.firstOrNull {
            it.pageName.contains("Word", ignoreCase = true) && it.pageName.contains("Light", ignoreCase = true)
        } ?: pages.firstOrNull()
            ?: throw IllegalStateException(
                "No Word & Light TV Facebook Page with a valid Page access token was returned. Personal user tokens are not permitted for Page publishing."
            )

        ConnectedChannelSession(
            platform = PublishPlatform.FACEBOOK,
            accessToken = preferredPage.pageAccessToken,
            expiresAtEpochMs = json.optLong("expiresAtEpochMs", 0L),
            accountId = json.optString("userId"),
            channelDisplayName = json.optString("userName", "Facebook Page Admin"),
            targetPageId = preferredPage.pageId,
            targetPageName = preferredPage.pageName,
            availablePages = pages,
            scopes = listOf("pages_show_list", "pages_read_engagement", "pages_manage_posts")
        )
    }

    private suspend fun exchangeTikTokCodeViaBroker(
        code: String,
        codeVerifier: String,
        config: PublishingEnvironmentConfig
    ): Result<ConnectedChannelSession> = runCatching {
        val broker = config.validBrokerBaseUrl
        if (broker.isBlank()) {
            throw IllegalStateException("Social Token Broker HTTPS URL is not configured.")
        }

        val payloadJson = JSONObject().apply {
            put("code", code)
            put("codeVerifier", codeVerifier)
            put("redirectUri", OAuthPkceManager.redirectUriFor(PublishPlatform.TIKTOK, config))
        }.toString()

        val json = executeAuthenticatedBrokerPost("$broker/oauth/tiktok/exchange", payloadJson)
        val accessToken = json.optString("accessToken")
        if (accessToken.isBlank()) {
            throw IOException(json.optString("error", "TikTok broker exchange failed."))
        }
        val expiresInSec = json.optLong("expiresIn", 86400L)
        ConnectedChannelSession(
            platform = PublishPlatform.TIKTOK,
            accessToken = accessToken,
            refreshToken = json.optString("refreshToken"),
            expiresAtEpochMs = System.currentTimeMillis() + (expiresInSec * 1000L),
            accountId = json.optString("openId"),
            channelDisplayName = json.optString("displayName").ifBlank { "TikTok Creator" },
            channelHandle = json.optString("username"),
            scopes = json.optString("scope").split(",").filter { it.isNotBlank() }
        )
    }

    private suspend fun refreshTikTokViaBroker(
        session: ConnectedChannelSession,
        config: PublishingEnvironmentConfig
    ): Result<ConnectedChannelSession> = runCatching {
        val broker = config.validBrokerBaseUrl
        if (broker.isBlank() || session.refreshToken.isBlank()) {
            throw IllegalStateException("TikTok authorization expired. Reconnect via official OAuth.")
        }

        val payloadJson = JSONObject().apply {
            put("refreshToken", session.refreshToken)
        }.toString()

        val json = executeAuthenticatedBrokerPost("$broker/oauth/tiktok/refresh", payloadJson)
        val newToken = json.optString("accessToken")
        if (newToken.isBlank()) {
            throw IOException(json.optString("error", "TikTok token refresh failed."))
        }
        val expiresInSec = json.optLong("expiresIn", 86400L)
        session.copy(
            accessToken = newToken,
            refreshToken = json.optString("refreshToken").ifBlank { session.refreshToken },
            expiresAtEpochMs = System.currentTimeMillis() + (expiresInSec * 1000L)
        )
    }

    private suspend fun executeAuthenticatedBrokerPost(url: String, jsonBody: String): JSONObject {
        val firstToken = firebaseIdTokenProvider(false)
            ?: throw IllegalStateException("Sign in to Word & Light AI STUDIO before linking social channels.")

        try {
            return executeBrokerCall(url, jsonBody, firstToken)
        } catch (err: HttpStatusException) {
            if (err.code == 401) {
                val refreshedToken = firebaseIdTokenProvider(true)
                    ?: throw err
                if (refreshedToken.isNotBlank() && refreshedToken != firstToken) {
                    return executeBrokerCall(url, jsonBody, refreshedToken)
                }
            }
            throw err
        }
    }

    private fun executeBrokerCall(url: String, jsonBody: String, idToken: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $idToken")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject {
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
            if (!response.isSuccessful) {
                val err = parsed.optString("error_description")
                    .ifBlank { parsed.optString("error") }
                    .ifBlank { parsed.optJSONObject("error")?.optString("message").orEmpty() }
                    .ifBlank { "HTTP ${response.code}" }
                throw HttpStatusException(response.code, err)
            }
            return parsed
        }
    }

    internal class HttpStatusException(val code: Int, message: String) : IOException(message)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
