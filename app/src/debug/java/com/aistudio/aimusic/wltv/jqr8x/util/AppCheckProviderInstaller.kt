package com.aistudio.aimusic.wltv.jqr8x.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckProviderInstaller {
    private const val TAG = "AppCheckInit"
    private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
    private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
    private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

    const val PROVIDER_NAME = "DebugAppCheckProviderFactory"

    fun install(
        context: Context,
        firebaseApp: FirebaseApp,
        intent: Intent?,
        onTokenCheckResult: (tokenObtained: Boolean, errorDetail: String?) -> Unit
    ): Boolean {
        val prefsName = "$DEBUG_STORE_PREFIX${firebaseApp.persistenceKey}"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
        val launchExtraProvided = !debugToken.isNullOrEmpty()

        if (launchExtraProvided) {
            // CRITICAL: synchronous commit() so DebugAppCheckProviderFactory reads the secret immediately.
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()
            Log.d(TAG, "App Check debug secret configured synchronously from launch Intent.")
        } else {
            val existingLocalSecret = prefs.getString(DEBUG_SECRET_KEY, null)?.trim()
            if (!existingLocalSecret.isNullOrEmpty()) {
                Log.i(
                    TAG,
                    "App Check local debug token (register in Firebase Console -> App Check -> Apps -> Manage debug tokens): $existingLocalSecret"
                )
            } else {
                Log.w(
                    TAG,
                    "No launch debug token extra; DebugAppCheckProviderFactory will generate a local debug token."
                )
            }
        }

        val appCheck = FirebaseAppCheck.getInstance(firebaseApp)
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        Log.d(TAG, "Installed $PROVIDER_NAME (launchExtraProvided=$launchExtraProvided).")

        // Request an App Check token early (non-blocking) so DebugAppCheckProvider generates/exchanges
        // its secret immediately and surfaces unregistered-token HTTP 403 diagnostics clearly.
        // Never log the returned App Check JWT token itself.
        runCatching {
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener { result ->
                    val hasToken = result.token.isNotBlank()
                    Log.d(TAG, "App Check debug token request succeeded (tokenPresent=$hasToken).")
                    onTokenCheckResult(hasToken, if (hasToken) null else "Empty App Check token returned")
                }
                .addOnFailureListener { error ->
                    val errorMsg = error.message?.take(160) ?: error::class.java.simpleName
                    Log.w(TAG, "App Check debug token exchange failed: $errorMsg")
                    if (!launchExtraProvided) {
                        val generatedSecret = prefs.getString(DEBUG_SECRET_KEY, null)?.trim()
                        if (!generatedSecret.isNullOrEmpty()) {
                            Log.i(
                                TAG,
                                "Register this Firebase App Check debug token in Firebase Console -> App Check -> Apps -> Manage debug tokens: $generatedSecret"
                            )
                        }
                    }
                    onTokenCheckResult(false, errorMsg)
                }
        }.onFailure { error ->
            val errorMsg = error.message?.take(160) ?: error::class.java.simpleName
            Log.w(TAG, "App Check debug token request could not start: $errorMsg")
            onTokenCheckResult(false, errorMsg)
        }

        return launchExtraProvided
    }
}
