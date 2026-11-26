package com.aistudio.aimusic.wltv.jqr8x.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object AppCheckProviderInstaller {
    private const val TAG = "AppCheckInit"

    const val PROVIDER_NAME = "PlayIntegrityAppCheckProviderFactory"

    fun install(
        context: Context,
        firebaseApp: FirebaseApp,
        intent: Intent?,
        onTokenCheckResult: (tokenObtained: Boolean, errorDetail: String?) -> Unit
    ): Boolean {
        val appCheck = FirebaseAppCheck.getInstance(firebaseApp)
        appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        Log.d(TAG, "Installed release App Check provider: $PROVIDER_NAME.")

        runCatching {
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener { result ->
                    val hasToken = result.token.isNotBlank()
                    Log.d(TAG, "Play Integrity App Check token ready (tokenPresent=$hasToken).")
                    onTokenCheckResult(hasToken, if (hasToken) null else "Empty App Check token returned")
                }
                .addOnFailureListener { error ->
                    val errorMsg = error.message?.take(160) ?: error::class.java.simpleName
                    Log.w(TAG, "Play Integrity App Check token check failed: $errorMsg")
                    onTokenCheckResult(false, errorMsg)
                }
        }.onFailure { error ->
            val errorMsg = error.message?.take(160) ?: error::class.java.simpleName
            Log.w(TAG, "Play Integrity App Check token request could not start: $errorMsg")
            onTokenCheckResult(false, errorMsg)
        }

        return false
    }
}
