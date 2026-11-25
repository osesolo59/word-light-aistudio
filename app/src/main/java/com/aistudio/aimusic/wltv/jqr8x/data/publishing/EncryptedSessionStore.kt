package com.aistudio.aimusic.wltv.jqr8x.data.publishing

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherPayload: String): String?
}

/**
 * Hardware-backed Android Keystore AES-256-GCM cipher.
 * Encrypts OAuth session JSON blobs at rest so tokens never appear in plaintext preferences.
 */
class AndroidKeyStoreAesGcmCipher(
    private val keyAlias: String = KEY_ALIAS
) : SessionCipher {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(4 + iv.size + cipherBytes.size)
            .putInt(iv.size)
            .put(iv)
            .put(cipherBytes)
            .array()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    override fun decrypt(cipherPayload: String): String? {
        return runCatching {
            val raw = Base64.decode(cipherPayload, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(raw)
            val ivSize = buffer.int
            if (ivSize !in 12..32 || ivSize > buffer.remaining()) return null
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val cipherBytes = ByteArray(buffer.remaining())
            buffer.get(cipherBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "wltv_social_oauth_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        fun isAvailable(): Boolean = runCatching {
            if ("robolectric".equals(Build.FINGERPRINT, ignoreCase = true)) {
                return@runCatching false
            }
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore != null
        }.getOrDefault(false)
    }
}

/**
 * Ephemeral AES-256-GCM JCE cipher restricted strictly to Robolectric JVM unit tests
 * where Android's hardware Keystore binder does not exist. Never permitted on real devices.
 */
internal class EphemeralRobolectricAesGcmCipher : SessionCipher {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply {
        init(256)
    }.generateKey()

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(4 + iv.size + cipherBytes.size)
            .putInt(iv.size)
            .put(iv)
            .put(cipherBytes)
            .array()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    override fun decrypt(cipherPayload: String): String? {
        return runCatching {
            val raw = Base64.decode(cipherPayload, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(raw)
            val ivSize = buffer.int
            if (ivSize !in 12..32 || ivSize > buffer.remaining()) return null
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val cipherBytes = ByteArray(buffer.remaining())
            buffer.get(cipherBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrNull()
    }
}

/**
 * Local encrypted storage for authorized OAuth 2.0 sessions and per-platform PKCE states.
 * - Fails securely with SecurityException if Android Keystore AES-GCM is unavailable on real devices.
 * - Never stores plaintext passwords, client secrets, or unencrypted Base64 tokens.
 * - Enforces a 10-minute expiration window and per-platform isolation for pending OAuth states.
 */
class EncryptedSessionStore(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME,
    cipherOverride: SessionCipher? = null,
    private val keystoreAvailabilityCheck: () -> Boolean = { AndroidKeyStoreAesGcmCipher.isAvailable() },
    private val allowRobolectricTestCipher: Boolean = isRobolectricRuntime()
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val cipherProvider: SessionCipher? by lazy {
        cipherOverride ?: when {
            keystoreAvailabilityCheck() -> AndroidKeyStoreAesGcmCipher()
            allowRobolectricTestCipher && isRobolectricRuntime() -> EphemeralRobolectricAesGcmCipher()
            else -> null
        }
    }

    private fun requireCipher(): SessionCipher {
        return cipherProvider ?: throw SecurityException(
            "Android Keystore AES-256-GCM encryption is unavailable on this device. OAuth tokens cannot be stored safely."
        )
    }

    fun isSecureStorageAvailable(): Boolean = runCatching { requireCipher() }.isSuccess

    fun saveSession(session: ConnectedChannelSession) {
        if (session.platform == PublishPlatform.FACEBOOK) {
            val selectedValid = session.targetPageId.isNotBlank() &&
                session.availablePages.any {
                    it.pageId == session.targetPageId && it.hasValidPublishingCredentials()
                }
            if (!selectedValid) {
                throw SecurityException("Facebook session must target a valid Facebook Page with a Page access token.")
            }
        }
        val cipher = requireCipher()
        val json = serializeSession(session).toString()
        val encrypted = cipher.encrypt(json)
        prefs.edit()
            .putString(sessionKey(session.platform), encrypted)
            .apply()
    }

    fun getSession(platform: PublishPlatform): ConnectedChannelSession? {
        val encrypted = prefs.getString(sessionKey(platform), null) ?: return null
        val cipher = runCatching { requireCipher() }.getOrNull() ?: return null
        val plain = cipher.decrypt(encrypted) ?: return null
        return runCatching { deserializeSession(JSONObject(plain)) }.getOrNull()
    }

    fun getAllSessions(): Map<PublishPlatform, ConnectedChannelSession?> {
        if (!isSecureStorageAvailable()) {
            return PublishPlatform.entries.associateWith { null }
        }
        return PublishPlatform.entries.associateWith { getSession(it) }
    }

    fun updateFacebookTargetPage(page: FacebookPageTarget): ConnectedChannelSession? {
        if (!page.hasValidPublishingCredentials()) {
            return null
        }
        val current = getSession(PublishPlatform.FACEBOOK) ?: return null
        val matchingPage = current.availablePages.firstOrNull {
            it.pageId == page.pageId && it.hasValidPublishingCredentials()
        } ?: return null
        val updated = current.copy(
            accessToken = matchingPage.pageAccessToken,
            targetPageId = matchingPage.pageId,
            targetPageName = matchingPage.pageName
        )
        saveSession(updated)
        return updated
    }

    fun clearSession(platform: PublishPlatform) {
        prefs.edit().remove(sessionKey(platform)).apply()
    }

    fun clearAllSessions() {
        val editor = prefs.edit()
        PublishPlatform.entries.forEach { editor.remove(sessionKey(it)) }
        editor.remove(KEY_PENDING_OAUTH_MAP)
        editor.remove(KEY_LEGACY_PENDING_OAUTH)
        editor.apply()
    }

    fun savePendingOAuthState(
        pending: PendingOAuthState,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val cipher = requireCipher()
        val map = loadPendingStateMap(cipher, nowMs).toMutableMap()
        map[pending.platform] = pending
        persistPendingStateMap(cipher, map)
    }

    fun consumePendingOAuthState(
        platform: PublishPlatform,
        expectedState: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PendingOAuthState? {
        val cipher = requireCipher()
        val map = loadPendingStateMap(cipher, nowMs).toMutableMap()
        val entry = map[platform]
        if (entry == null) {
            persistPendingStateMap(cipher, map)
            return null
        }
        if (entry.isExpired(nowMs)) {
            map.remove(platform)
            persistPendingStateMap(cipher, map)
            return null
        }
        if (expectedState != null && entry.state != expectedState) {
            // Do not delete valid in-flight state on unsolicited mismatched state; return null for abort.
            return null
        }
        map.remove(platform)
        persistPendingStateMap(cipher, map)
        return entry
    }

    internal fun activePendingPlatforms(nowMs: Long = System.currentTimeMillis()): Set<PublishPlatform> {
        val cipher = runCatching { requireCipher() }.getOrNull() ?: return emptySet()
        return loadPendingStateMap(cipher, nowMs).keys
    }

    private fun loadPendingStateMap(
        cipher: SessionCipher,
        nowMs: Long
    ): Map<PublishPlatform, PendingOAuthState> {
        val encrypted = prefs.getString(KEY_PENDING_OAUTH_MAP, null) ?: return emptyMap()
        val plain = cipher.decrypt(encrypted) ?: return emptyMap()
        val root = runCatching { JSONObject(plain) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<PublishPlatform, PendingOAuthState>()
        for (platform in PublishPlatform.entries) {
            val obj = root.optJSONObject(platform.key) ?: continue
            val state = obj.optString("state")
            val verifier = obj.optString("codeVerifier")
            val createdAt = obj.optLong("createdAtEpochMs", 0L)
            if (state.isNotBlank() && verifier.isNotBlank()) {
                val pending = PendingOAuthState(
                    platform = platform,
                    state = state,
                    codeVerifier = verifier,
                    createdAtEpochMs = createdAt
                )
                if (!pending.isExpired(nowMs)) {
                    result[platform] = pending
                }
            }
        }
        return result
    }

    private fun persistPendingStateMap(
        cipher: SessionCipher,
        map: Map<PublishPlatform, PendingOAuthState>
    ) {
        if (map.isEmpty()) {
            prefs.edit()
                .remove(KEY_PENDING_OAUTH_MAP)
                .remove(KEY_LEGACY_PENDING_OAUTH)
                .apply()
            return
        }
        val root = JSONObject()
        map.forEach { (platform, pending) ->
            root.put(
                platform.key,
                JSONObject().apply {
                    put("platform", platform.key)
                    put("state", pending.state)
                    put("codeVerifier", pending.codeVerifier)
                    put("createdAtEpochMs", pending.createdAtEpochMs)
                }
            )
        }
        prefs.edit()
            .putString(KEY_PENDING_OAUTH_MAP, cipher.encrypt(root.toString()))
            .remove(KEY_LEGACY_PENDING_OAUTH)
            .apply()
    }

    private fun serializeSession(session: ConnectedChannelSession): JSONObject = JSONObject().apply {
        put("platform", session.platform.key)
        put("accessToken", session.accessToken)
        put("refreshToken", session.refreshToken)
        put("expiresAtEpochMs", session.expiresAtEpochMs)
        put("accountId", session.accountId)
        put("channelDisplayName", session.channelDisplayName)
        put("channelHandle", session.channelHandle)
        put("targetPageId", session.targetPageId)
        put("targetPageName", session.targetPageName)
        put("connectedAtEpochMs", session.connectedAtEpochMs)
        put("scopes", JSONArray(session.scopes))
        val pagesArray = JSONArray()
        session.availablePages.forEach { page ->
            pagesArray.put(JSONObject().apply {
                put("pageId", page.pageId)
                put("pageName", page.pageName)
                put("category", page.category)
                put("pageAccessToken", page.pageAccessToken)
            })
        }
        put("availablePages", pagesArray)
    }

    private fun deserializeSession(obj: JSONObject): ConnectedChannelSession? {
        val platform = PublishPlatform.fromKey(obj.optString("platform")) ?: return null
        val scopesArray = obj.optJSONArray("scopes") ?: JSONArray()
        val scopes = buildList {
            for (i in 0 until scopesArray.length()) {
                scopesArray.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        val pagesArray = obj.optJSONArray("availablePages") ?: JSONArray()
        val pages = buildList {
            for (i in 0 until pagesArray.length()) {
                val item = pagesArray.optJSONObject(i) ?: continue
                val pid = item.optString("pageId")
                if (pid.isNotBlank()) {
                    add(
                        FacebookPageTarget(
                            pageId = pid,
                            pageName = item.optString("pageName"),
                            category = item.optString("category"),
                            pageAccessToken = item.optString("pageAccessToken")
                        )
                    )
                }
            }
        }
        return ConnectedChannelSession(
            platform = platform,
            accessToken = obj.optString("accessToken"),
            refreshToken = obj.optString("refreshToken"),
            expiresAtEpochMs = obj.optLong("expiresAtEpochMs", 0L),
            accountId = obj.optString("accountId"),
            channelDisplayName = obj.optString("channelDisplayName"),
            channelHandle = obj.optString("channelHandle"),
            targetPageId = obj.optString("targetPageId"),
            targetPageName = obj.optString("targetPageName"),
            availablePages = pages,
            scopes = scopes,
            connectedAtEpochMs = obj.optLong("connectedAtEpochMs", 0L)
        )
    }

    companion object {
        const val DEFAULT_PREFS_NAME = "wltv_encrypted_social_sessions"
        private const val KEY_PREFIX = "oauth_session_"
        private const val KEY_PENDING_OAUTH_MAP = "pending_oauth_pkce_states_v2"
        private const val KEY_LEGACY_PENDING_OAUTH = "pending_oauth_pkce_state"

        private fun sessionKey(platform: PublishPlatform): String = "$KEY_PREFIX${platform.key}"

        internal fun isRobolectricRuntime(): Boolean =
            "robolectric".equals(Build.FINGERPRINT, ignoreCase = true)
    }
}

/**
 * Non-secret persistent store for background WorkManager publishing results.
 * Persists status indicators, remote IDs/URLs, and error messages across worker retries and app restarts
 * without storing any OAuth tokens or secrets.
 */
class PublishStatusStore(
    context: Context,
    prefsName: String = DEFAULT_STATUS_PREFS_NAME
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun saveStatus(platform: PublishPlatform, status: PlatformPublishStatus) {
        val json = JSONObject().apply {
            when (status) {
                PlatformPublishStatus.Idle -> put("type", "IDLE")
                is PlatformPublishStatus.Queued -> {
                    put("type", "QUEUED")
                    put("stepLabel", status.stepLabel)
                }
                is PlatformPublishStatus.Uploading -> {
                    put("type", "UPLOADING")
                    put("progressPercent", status.progressPercent)
                    put("stepLabel", status.stepLabel)
                }
                is PlatformPublishStatus.Processing -> {
                    put("type", "PROCESSING")
                    put("stepLabel", status.stepLabel)
                }
                is PlatformPublishStatus.Published -> {
                    put("type", "PUBLISHED")
                    put("remoteId", status.remoteId)
                    put("remoteUrl", status.remoteUrl)
                    put("visibilityNote", status.visibilityNote)
                }
                is PlatformPublishStatus.Failed -> {
                    put("type", "FAILED")
                    put("errorMessage", status.errorMessage)
                    put("requiresReauth", status.requiresReauth)
                }
            }
        }
        prefs.edit().putString(keyFor(platform), json.toString()).commit()
    }

    fun getStatus(platform: PublishPlatform): PlatformPublishStatus {
        val raw = prefs.getString(keyFor(platform), null) ?: return PlatformPublishStatus.Idle
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return PlatformPublishStatus.Idle
        return when (json.optString("type")) {
            "QUEUED" -> PlatformPublishStatus.Queued(json.optString("stepLabel", "Queued..."))
            "UPLOADING" -> PlatformPublishStatus.Uploading(
                progressPercent = json.optInt("progressPercent", 0),
                stepLabel = json.optString("stepLabel", "Uploading...")
            )
            "PROCESSING" -> PlatformPublishStatus.Processing(json.optString("stepLabel", "Processing..."))
            "PUBLISHED" -> PlatformPublishStatus.Published(
                remoteId = json.optString("remoteId"),
                remoteUrl = json.optString("remoteUrl"),
                visibilityNote = json.optString("visibilityNote")
            )
            "FAILED" -> PlatformPublishStatus.Failed(
                errorMessage = json.optString("errorMessage", "Upload failed."),
                requiresReauth = json.optBoolean("requiresReauth", false)
            )
            else -> PlatformPublishStatus.Idle
        }
    }

    fun getAllStatuses(): Map<PublishPlatform, PlatformPublishStatus> =
        PublishPlatform.entries.associateWith { getStatus(it) }

    fun clearAll() {
        val editor = prefs.edit()
        PublishPlatform.entries.forEach { editor.remove(keyFor(it)) }
        editor.commit()
    }

    companion object {
        const val DEFAULT_STATUS_PREFS_NAME = "wltv_publish_status_results"
        private fun keyFor(platform: PublishPlatform): String = "status_${platform.key}"
    }
}
