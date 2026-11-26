package com.aistudio.aimusic.wltv.jqr8x.util

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppCheckHelperTest {

    private lateinit var context: Context
    private lateinit var firebaseApp: FirebaseApp

    @Before
    fun setUp() {
        AppCheckDiagnostics.resetForTest()
        context = ApplicationProvider.getApplicationContext()
        firebaseApp = runCatching { FirebaseApp.getInstance() }.getOrElse {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:1035836705469:android:cd043a7cec9572aba505d4")
                    .setProjectId("gen-lang-client-0518223416")
                    .setApiKey("fake-api-key")
                    .build()
            )
        }
    }

    @After
    fun tearDown() {
        AppCheckDiagnostics.resetForTest()
    }

    @Test
    fun configureAppCheck_installsDebugProviderAndCommitsIntentTokenSynchronously() {
        val intent = Intent().putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "test-debug-token-uuid-1234")

        configureAppCheck(context, intent)

        val status = AppCheckDiagnostics.status
        assertTrue(status.initialized)
        assertTrue(status.initializedBeforeAi)
        assertTrue(status.isDebugBuild)
        assertTrue(status.launchExtraProvided)
        assertEquals("DebugAppCheckProviderFactory", status.providerName)

        val prefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.${firebaseApp.persistenceKey}",
            Context.MODE_PRIVATE
        )
        assertEquals(
            "test-debug-token-uuid-1234",
            prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
        )
    }

    @Test
    fun markAiRequestedBeforeInit_flagsLateInitialization() {
        AppCheckDiagnostics.markAiRequested()
        assertFalse(AppCheckDiagnostics.status.initializedBeforeAi)

        configureAppCheck(context, null)
        assertTrue(AppCheckDiagnostics.status.initialized)
        assertFalse(AppCheckDiagnostics.status.initializedBeforeAi)
    }
}
