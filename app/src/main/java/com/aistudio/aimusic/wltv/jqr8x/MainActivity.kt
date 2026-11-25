package com.aistudio.aimusic.wltv.jqr8x

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.aistudio.aimusic.wltv.jqr8x.data.model.UserProfile
import com.aistudio.aimusic.wltv.jqr8x.data.publishing.OAuthPkceManager
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.auth.AuthScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.navigation.AppNavigation
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import com.aistudio.aimusic.wltv.jqr8x.util.configureAppCheck
import com.google.firebase.Firebase
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
        
        // 1. Configure App Check first
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
    onConsumeOAuthUri: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var globalError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            try {
                val repository = MusicRepository(context)
                repository.getUserProfile().collectLatest {
                    userProfile = it
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AppContent", "Failed to load user profile", e)
                globalError = "Profile Load Error: ${e.localizedMessage}"
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
            if (globalError != null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = globalError!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (currentUser == null) {
                AuthScreen(
                    onAuthSuccess = { currentUser = Firebase.auth.currentUser }
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
