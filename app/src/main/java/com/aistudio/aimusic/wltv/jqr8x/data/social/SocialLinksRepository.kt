package com.aistudio.aimusic.wltv.jqr8x.data.social

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SocialLinksRepository(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    init {
        migrateStaleDefaultsIfNeeded()
    }

    private val _allLinks = MutableStateFlow(loadAllLinks())
    val allLinksFlow: StateFlow<Map<SocialPlatform, String>> = _allLinks.asStateFlow()

    private val _configuredLinks = MutableStateFlow(extractConfiguredLinks(_allLinks.value))
    val configuredLinksFlow: StateFlow<List<ConfiguredSocialLink>> = _configuredLinks.asStateFlow()

    fun getAllLinks(): Map<SocialPlatform, String> = _allLinks.value

    fun getConfiguredLinks(): List<ConfiguredSocialLink> = _configuredLinks.value

    fun getLink(platform: SocialPlatform): String = _allLinks.value[platform].orEmpty()

    /**
     * Validates and persists a map of all/subset platform URLs from Admin/Settings configuration.
     * Unconfigured optional platforms become blank (hidden); WhatsApp stays active with the
     * verified official Word & Light TV contact link if left blank.
     */
    fun updateLinks(updates: Map<SocialPlatform, String>): Result<Map<SocialPlatform, String>> {
        val normalizedUpdates = linkedMapOf<SocialPlatform, String>()
        for (platform in SocialPlatform.entries) {
            val candidate = updates[platform] ?: _allLinks.value[platform].orEmpty()
            when (val validation = SocialLinkValidator.validate(candidate)) {
                SocialUrlValidationResult.Empty -> {
                    normalizedUpdates[platform] = if (platform == SocialPlatform.WHATSAPP) {
                        VERIFIED_WHATSAPP_URL
                    } else {
                        ""
                    }
                }
                is SocialUrlValidationResult.Valid -> {
                    normalizedUpdates[platform] = validation.normalizedUrl
                }
                is SocialUrlValidationResult.Invalid -> {
                    return Result.failure(
                        IllegalArgumentException("${platform.displayName}: ${validation.errorMessage}")
                    )
                }
            }
        }

        prefs.edit().apply {
            normalizedUpdates.forEach { (platform, url) ->
                putString(prefKey(platform), url)
            }
            putInt(KEY_VERIFIED_DEFAULTS_VERSION, CURRENT_VERIFIED_DEFAULTS_VERSION)
        }.apply()

        _allLinks.value = normalizedUpdates.toMap()
        _configuredLinks.value = extractConfiguredLinks(_allLinks.value)
        return Result.success(_allLinks.value)
    }

    fun updateLink(platform: SocialPlatform, rawUrl: String): Result<Map<SocialPlatform, String>> {
        val next = _allLinks.value.toMutableMap()
        next[platform] = rawUrl
        return updateLinks(next)
    }

    fun resetToVerifiedDefaults(): Map<SocialPlatform, String> {
        prefs.edit()
            .clear()
            .putInt(KEY_VERIFIED_DEFAULTS_VERSION, CURRENT_VERIFIED_DEFAULTS_VERSION)
            .apply()
        val defaults = defaultLinksMap()
        _allLinks.value = defaults
        _configuredLinks.value = extractConfiguredLinks(defaults)
        return defaults
    }

    private fun migrateStaleDefaultsIfNeeded() {
        val storedVersion = prefs.getInt(KEY_VERIFIED_DEFAULTS_VERSION, 0)
        if (storedVersion >= CURRENT_VERIFIED_DEFAULTS_VERSION) return

        prefs.edit().apply {
            SocialPlatform.entries.forEach { platform ->
                if (platform.defaultUrl.isNotBlank()) {
                    val key = prefKey(platform)
                    val existing = prefs.getString(key, null)
                    if (existing.isNullOrBlank()) {
                        putString(key, platform.defaultUrl)
                    }
                }
            }
            putInt(KEY_VERIFIED_DEFAULTS_VERSION, CURRENT_VERIFIED_DEFAULTS_VERSION)
        }.apply()
    }

    private fun loadAllLinks(): Map<SocialPlatform, String> {
        val result = linkedMapOf<SocialPlatform, String>()
        for (platform in SocialPlatform.entries) {
            val key = prefKey(platform)
            val rawValue = if (prefs.contains(key)) {
                prefs.getString(key, "").orEmpty()
            } else {
                platform.defaultUrl
            }
            val validated = when (val validation = SocialLinkValidator.validate(rawValue)) {
                is SocialUrlValidationResult.Valid -> validation.normalizedUrl
                SocialUrlValidationResult.Empty,
                is SocialUrlValidationResult.Invalid -> ""
            }
            result[platform] = if (platform == SocialPlatform.WHATSAPP && validated.isBlank()) {
                VERIFIED_WHATSAPP_URL
            } else {
                validated
            }
        }
        return result
    }

    companion object {
        private const val DEFAULT_PREFS_NAME = "word_and_light_social_links"
        private const val KEY_PREFIX = "social_url_"
        private const val KEY_VERIFIED_DEFAULTS_VERSION = "verified_defaults_version"
        private const val CURRENT_VERIFIED_DEFAULTS_VERSION = 3

        @Volatile
        private var sharedInstance: SocialLinksRepository? = null

        fun getInstance(context: Context): SocialLinksRepository {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: SocialLinksRepository(context.applicationContext).also {
                    sharedInstance = it
                }
            }
        }

        internal fun resetSharedInstanceForTests() {
            synchronized(this) {
                sharedInstance = null
            }
        }

        fun defaultLinksMap(): Map<SocialPlatform, String> {
            return SocialPlatform.entries.associateWith { it.defaultUrl }
        }

        fun extractConfiguredLinks(links: Map<SocialPlatform, String>): List<ConfiguredSocialLink> {
            return SocialPlatform.entries.mapNotNull { platform ->
                val raw = links[platform].orEmpty()
                when (val validation = SocialLinkValidator.validate(raw)) {
                    is SocialUrlValidationResult.Valid -> ConfiguredSocialLink(
                        platform = platform,
                        url = validation.normalizedUrl
                    )
                    SocialUrlValidationResult.Empty,
                    is SocialUrlValidationResult.Invalid -> {
                        if (platform == SocialPlatform.WHATSAPP) {
                            ConfiguredSocialLink(
                                platform = SocialPlatform.WHATSAPP,
                                url = VERIFIED_WHATSAPP_URL
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        }

        private fun prefKey(platform: SocialPlatform): String = "$KEY_PREFIX${platform.key}"
    }
}
