package com.aistudio.aimusic.wltv.jqr8x.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.aistudio.aimusic.wltv.jqr8x.BuildConfig
import com.google.firebase.FirebaseApp

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"

data class AppCheckStatus(
    val initialized: Boolean = false,
    val initializedBeforeAi: Boolean = true,
    val providerName: String = "Uninitialized",
    val isDebugBuild: Boolean = BuildConfig.DEBUG,
    val launchExtraProvided: Boolean = false,
    val tokenCheckAttempted: Boolean = false,
    val tokenObtained: Boolean = false,
    val lastInitError: String? = null,
    val lastTokenError: String? = null
)

object AppCheckDiagnostics {
    @Volatile
    var status: AppCheckStatus = AppCheckStatus()
        private set

    @Volatile
    private var installedAppName: String? = null

    internal fun shouldInstall(firebaseAppName: String, hasDebugExtra: Boolean): Boolean =
        !status.initialized || installedAppName != firebaseAppName || (BuildConfig.DEBUG && hasDebugExtra)

    internal fun recordInstalled(
        firebaseAppName: String,
        providerName: String,
        launchExtraProvided: Boolean
    ) {
        installedAppName = firebaseAppName
        val prior = status
        status = prior.copy(
            initialized = true,
            providerName = providerName,
            isDebugBuild = BuildConfig.DEBUG,
            launchExtraProvided = launchExtraProvided,
            lastInitError = null
        )
    }

    internal fun recordTokenResult(tokenObtained: Boolean, errorDetail: String?) {
        status = status.copy(
            tokenCheckAttempted = true,
            tokenObtained = tokenObtained,
            lastTokenError = errorDetail
        )
    }

    internal fun recordInitError(errorDetail: String) {
        status = status.copy(
            initialized = false,
            lastInitError = errorDetail
        )
    }

    fun markAiRequested() {
        if (!status.initialized) {
            status = status.copy(initializedBeforeAi = false)
        }
    }

    @VisibleForTesting
    internal fun resetForTest() {
        installedAppName = null
        status = AppCheckStatus()
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val firebaseApp = runCatching { FirebaseApp.getInstance() }.getOrElse {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            } else {
                FirebaseApp.getApps(context).firstOrNull()
            }
        }
        if (firebaseApp == null) {
            val detail = "FirebaseApp is not configured (google-services.json missing)"
            Log.e(TAG, detail)
            AppCheckDiagnostics.recordInitError(detail)
            return
        }

        val hasDebugExtra = !intent?.getStringExtra(DEBUG_TOKEN_EXTRA).isNullOrBlank()
        if (!AppCheckDiagnostics.shouldInstall(firebaseApp.name, hasDebugExtra)) {
            Log.d(TAG, "App Check already initialized (${AppCheckDiagnostics.status.providerName}); skipping duplicate setup.")
            return
        }

        val launchExtraProvided = AppCheckProviderInstaller.install(
            context = context,
            firebaseApp = firebaseApp,
            intent = intent,
            onTokenCheckResult = { tokenObtained, errorDetail ->
                AppCheckDiagnostics.recordTokenResult(tokenObtained, errorDetail)
            }
        )
        AppCheckDiagnostics.recordInstalled(
            firebaseAppName = firebaseApp.name,
            providerName = AppCheckProviderInstaller.PROVIDER_NAME,
            launchExtraProvided = launchExtraProvided
        )
    } catch (e: Exception) {
        val safeMsg = e.message?.take(160) ?: e::class.java.simpleName
        Log.e(TAG, "Failed to initialize Firebase App Check: ${e::class.java.simpleName}", e)
        AppCheckDiagnostics.recordInitError(safeMsg)
    }
}
