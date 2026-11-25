package com.aistudio.aimusic.wltv.jqr8x.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import com.aistudio.aimusic.wltv.jqr8x.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WORD & LIGHT AI STUDIO",
            style = MaterialTheme.typography.headlineLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your Ideas. Your Lyrics. Your Music. Your Videos.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        GoogleSignInButton(
            isLoading = isLoading,
            onClick = {
                if (isLoading) return@GoogleSignInButton
                errorMessage = null
                isLoading = true
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                        isLoading = false
                        onAuthSuccess()
                    },
                    onAuthError = {
                        isLoading = false
                        errorMessage = it
                    },
                    scope = scope,
                    onAuthCancelled = { isLoading = false }
                )
            }
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onSecondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Signing in...", fontWeight = FontWeight.Bold)
        } else {
            Text("Continue with Google", fontWeight = FontWeight.Bold)
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        Log.e("Auth", "Default Web Client ID not found in strings.xml. Ensure google-services.json is present and the plugin is applied.", e)
        onAuthError("Google Sign-In configuration missing (R.string.default_web_client_id)")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val activity = context.findActivity()
            if (activity == null) {
                onAuthError("Unable to find Activity context")
                return@launch
            }
            
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                Log.e("Auth", "Unexpected credential type: ${credential.type}")
                onAuthError("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "User cancelled Google Sign-In")
            onAuthCancelled()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Rethrow cancellation so the coroutine system handles it correctly
            // and we don't report it as an auth error to the user.
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            val rawMessage = e.localizedMessage ?: e.message ?: "Sign in failed"
            val friendlyMessage = when {
                rawMessage.contains("10:") || rawMessage.contains("DEVELOPER_ERROR") -> 
                    "Configuration Error (10): Please ensure SHA-1 is registered in Firebase Console and Google provider is enabled."
                rawMessage.contains("7:") || rawMessage.contains("NETWORK_ERROR") ->
                    "Network Error: Please check your internet connection."
                else -> "Error: $rawMessage"
            }
            onAuthError(friendlyMessage)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
