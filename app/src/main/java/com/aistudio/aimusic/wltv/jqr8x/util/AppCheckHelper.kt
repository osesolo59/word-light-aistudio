package com.aistudio.aimusic.wltv.jqr8x.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.aistudio.aimusic.wltv.jqr8x.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            val launchExtraProvided = !debugToken.isNullOrEmpty()

            if (launchExtraProvided) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured from launch Intent.")
            } else {
                // On a physical debug phone launched normally, DebugAppCheckProviderFactory creates/uses
                // a local debug secret UUID printed once in Logcat ("Enter this debug secret...").
                Log.w(
                    TAG,
                    "No launch debug token extra; DebugAppCheckProviderFactory will use local device debug secret."
                )
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed (launchExtraProvided=$launchExtraProvided).")
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            val siteKey = if (resId != 0) context.getString(resId).trim() else ""
            if (siteKey.isNotBlank()) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                )
                Log.d(TAG, "Release App Check provider installed: Recaptcha.")
            } else {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Release App Check provider installed: PlayIntegrity.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check: ${e::class.java.simpleName}")
    }
}
