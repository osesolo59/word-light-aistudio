package com.aistudio.aimusic.wltv.jqr8x

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.aimusic.wltv.jqr8x.data.model.UserProfile
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.OAuthPkceManager
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.auth.AuthScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.navigation.AppNavigation
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import com.aistudio.aimusic.wltv.jqr8x.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val _pendingOAuthUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            configureAppCheck(this, intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "App Check configuration failed", e)
        }

        captureOAuthIntentIfPresent(intent)

        enableEdgeToEdge()

        setContent {
            AppContent(
                pendingOAuthUriFlow = _pendingOAuthUri.asStateFlow(),
                onConsumeOAuthUri = { _pendingOAuthUri.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")) {
            configureAppCheck(this, intent)
        }
        captureOAuthIntentIfPresent(intent)
    }

    private fun captureOAuthIntentIfPresent(intent: Intent?) {
        val data = intent?.data ?: return
        if (OAuthPkceManager.isOAuthRedirectUri(data)) {
            _pendingOAuthUri.value = data
        }
    }
}

@Composable
fun AppContent(
    pendingOAuthUriFlow: StateFlow<Uri?>? = null,
    onConsumeOAuthUri: () -> Unit = {},
    firebaseReadyOverride: Boolean? = null
) {
    val context = LocalContext.current
    var initAttempt by remember { mutableIntStateOf(0) }
    val isFirebaseReady = remember(context, initAttempt, firebaseReadyOverride) {
        firebaseReadyOverride ?: runCatching {
            FirebaseApp.getInstance()
            true
        }.getOrElse {
            runCatching {
                FirebaseApp.initializeApp(context) != null
            }.getOrDefault(false)
        }
    }

    var currentUser by remember(isFirebaseReady) {
        mutableStateOf(
            if (isFirebaseReady) runCatching { Firebase.auth.currentUser }.getOrNull() else null
        )
    }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var globalError by remember { mutableStateOf<String?>(null) }

    if (isFirebaseReady) {
        DisposableEffect(Unit) {
            val firebaseAuth = runCatching { Firebase.auth }.getOrNull()
            val listener = FirebaseAuth.AuthStateListener { auth ->
                currentUser = auth.currentUser
            }
            firebaseAuth?.addAuthStateListener(listener)
            onDispose {
                firebaseAuth?.removeAuthStateListener(listener)
            }
        }
    }

    LaunchedEffect(isFirebaseReady, currentUser, initAttempt) {
        if (isFirebaseReady && currentUser != null) {
            try {
                globalError = null
                val repository = MusicRepository(context)
                repository.getUserProfile().collectLatest {
                    userProfile = it
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AppContent", "Failed to load user profile", e)
                globalError = "Profile Load Error: ${e.localizedMessage ?: "Unable to connect to cloud profile."}"
            }
        } else {
            userProfile = null
        }
    }

    val isDarkTheme = when (userProfile?.appearance ?: "System Default") {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    WordAndLightTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!isFirebaseReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 480.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "WORD & LIGHT AI STUDIO",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Firebase configuration (app/google-services.json) is missing in this APK build, so sign-in and cloud studio services cannot start yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add app/google-services.json to your GitHub build (or install the APK built directly in AI Studio) and reopen the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { initAttempt++ },
                            modifier = Modifier.testTag("retry_firebase_init_button")
                        ) {
                            Text("Retry Initialization")
                        }
                    }
                }
            } else if (globalError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 480.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = globalError!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                globalError = null
                                initAttempt++
                            },
                            modifier = Modifier.testTag("retry_profile_load_button")
                        ) {
                            Text("Retry Connection")
                        }
                    }
                }
            } else if (currentUser == null) {
                AuthScreen(
                    onAuthSuccess = {
                        currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
                    }
                )
            } else {
                val uid = currentUser!!.uid
                key(uid) {
                    AppNavigation(
                        uid = uid,
                        pendingOAuthUriFlow = pendingOAuthUriFlow,
                        onConsumeOAuthUri = onConsumeOAuthUri
                    )
                }
            }
        }
    }
}
