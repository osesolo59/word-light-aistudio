package com.aistudio.aimusic.wltv.jqr8x.ui.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val legacySignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        scope.launch {
            val activity = context.findActivity()
            if (activity == null) {
                isLoading = false
                errorMessage = "Unable to find Activity context after Google Sign-In."
                return@launch
            }
            if (activityResult.resultCode != Activity.RESULT_OK && activityResult.data == null) {
                isLoading = false
                errorMessage = "Sign-in was cancelled. Tap Continue with Google to try again."
                return@launch
            }
            try {
                @Suppress("DEPRECATION")
                val credential = Identity.getSignInClient(activity)
                    .getSignInCredentialFromIntent(activityResult.data)
                val idToken = credential.googleIdToken
                if (idToken.isNullOrBlank()) {
                    isLoading = false
                    errorMessage = "Google account did not return an ID token. Please verify Web Client ID configuration."
                    return@launch
                }
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                isLoading = false
                onAuthSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                isLoading = false
                if (e.statusCode == CommonStatusCodes.CANCELED) {
                    Log.w("Auth", "Classic Google Sign-In cancelled (${e.statusCode})")
                    errorMessage = "Sign-in was cancelled. Tap Continue with Google to try again."
                } else {
                    Log.e("Auth", "Classic Google Sign-In failed with code ${e.statusCode}", e)
                    errorMessage = formatGoogleAuthError(context, e, codeOverride = e.statusCode)
                }
            } catch (e: Exception) {
                isLoading = false
                Log.e("Auth", "Firebase credential exchange failed after Google Sign-In", e)
                errorMessage = formatGoogleAuthError(context, e)
            }
        }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    val launchClassicGoogleSignIn: () -> Unit = {
        val clientId = resolveWebClientId(context)
        val activity = context.findActivity()
        if (clientId.isEmpty()) {
            isLoading = false
            errorMessage = "Google Sign-In configuration missing (default_web_client_id)."
        } else if (activity == null) {
            isLoading = false
            errorMessage = "Unable to find Activity context for Google Sign-In."
        } else {
            val playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            if (playServicesStatus != ConnectionResult.SUCCESS &&
                !GoogleApiAvailability.getInstance().isUserResolvableError(playServicesStatus)
            ) {
                isLoading = false
                errorMessage = "Google Play Services is not available on this device (status=$playServicesStatus)."
            } else {
                scope.launch {
                    try {
                        @Suppress("DEPRECATION")
                        val signInIntentRequest = GetSignInIntentRequest.builder()
                            .setServerClientId(clientId)
                            .build()
                        @Suppress("DEPRECATION")
                        val pendingIntent = Identity.getSignInClient(activity)
                            .getSignInIntent(signInIntentRequest)
                            .await()
                        legacySignInLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ApiException) {
                        isLoading = false
                        Log.e("Auth", "Failed to start classic Google Sign-In (${e.statusCode})", e)
                        errorMessage = formatGoogleAuthError(context, e, codeOverride = e.statusCode)
                    } catch (e: Exception) {
                        isLoading = false
                        Log.e("Auth", "Failed to start classic Google Sign-In", e)
                        errorMessage = formatGoogleAuthError(context, e)
                    }
                }
            }
        }
    }

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
                    onAuthCancelled = {
                        isLoading = false
                        errorMessage = "Sign-in was dismissed. Tap Continue with Google or try the classic Google account picker below."
                    },
                    onFallbackToLegacySignIn = launchClassicGoogleSignIn
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = {
                if (isLoading) return@TextButton
                errorMessage = null
                isLoading = true
                launchClassicGoogleSignIn()
            },
            enabled = !isLoading,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("legacy_google_sign_in_button")
        ) {
            Text(
                text = "Use classic Google account picker",
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(12.dp)
                        .testTag("auth_error_text")
                )
            }
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
            .widthIn(max = 480.dp)
            .height(56.dp)
            .testTag("google_sign_in_button"),
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

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (runCatching { FirebaseApp.getInstance() }.isFailure) {
        onUnauthenticated()
        return
    }
    if (runCatching { Firebase.auth.currentUser }.getOrNull() != null) {
        onAuthSuccess()
        return
    }
    val clientId = resolveWebClientId(context)
    if (clientId.isEmpty()) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    scope.launch {
        try {
            val activity = context.findActivity()
            val credentialContext: Context = activity ?: context
            val cred = credentialManager.getCredential(credentialContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit,
    onFallbackToLegacySignIn: (() -> Unit)? = null
) {
    val clientId = resolveWebClientId(context)
    if (clientId.isEmpty()) {
        Log.e("Auth", "Default Web Client ID missing. Ensure app/google-services.json is present and google-services plugin is applied.")
        onAuthError("Google Sign-In configuration missing (default_web_client_id)")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("Unable to find Activity context")
            return@launch
        }

        try {
            val result = credentialManager.getCredential(activity, request)
            handleCredentialResult(result.credential, onAuthSuccess, onAuthError)
        } catch (e: NoCredentialException) {
            Log.w("Auth", "NoCredentialException from GetSignInWithGoogleOption; trying GetGoogleIdOption", e)
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(activity, fallbackRequest)
                handleCredentialResult(result.credential, onAuthSuccess, onAuthError)
            } catch (fallbackEx: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${fallbackEx.message}", fallbackEx)
                if (shouldTriggerLegacyFallbackOnCancel(fallbackEx) && onFallbackToLegacySignIn != null) {
                    onFallbackToLegacySignIn()
                } else if (isConfigErrorMessage(fallbackEx.message)) {
                    onAuthError(formatGoogleAuthError(context, fallbackEx))
                } else {
                    onAuthCancelled()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (fallbackEx: Exception) {
                Log.w("Auth", "CredentialManager fallback failed; attempting classic GIS sign-in", fallbackEx)
                if (onFallbackToLegacySignIn != null && shouldFallbackToLegacy(fallbackEx)) {
                    onFallbackToLegacySignIn()
                } else {
                    onAuthError(formatGoogleAuthError(context, fallbackEx))
                }
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            if (shouldTriggerLegacyFallbackOnCancel(e) && onFallbackToLegacySignIn != null) {
                onFallbackToLegacySignIn()
            } else if (isConfigErrorMessage(e.message)) {
                onAuthError(formatGoogleAuthError(context, e))
            } else {
                onAuthCancelled()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.w("Auth", "Credential provider unavailable on device; falling back to classic GIS sign-in", e)
            if (onFallbackToLegacySignIn != null) {
                onFallbackToLegacySignIn()
            } else {
                onAuthError(formatGoogleAuthError(context, e))
            }
        } catch (e: GetCredentialUnsupportedException) {
            Log.w("Auth", "CredentialManager unsupported on device; falling back to classic GIS sign-in", e)
            if (onFallbackToLegacySignIn != null) {
                onFallbackToLegacySignIn()
            } else {
                onAuthError(formatGoogleAuthError(context, e))
            }
        } catch (e: GetCredentialException) {
            Log.w("Auth", "CredentialManager GetCredentialException; checking fallback", e)
            if (onFallbackToLegacySignIn != null && shouldFallbackToLegacy(e)) {
                onFallbackToLegacySignIn()
            } else {
                onAuthError(formatGoogleAuthError(context, e))
            }
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(formatGoogleAuthError(context, e))
        }
    }
}

private suspend fun handleCredentialResult(
    credential: Credential,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
    } else {
        Log.e("Auth", "Unexpected credential type: ${credential.type}")
        onAuthError("Unexpected credential type: ${credential.type}")
    }
}

@SuppressLint("DiscouragedApi")
internal fun resolveWebClientId(context: Context): String {
    return try {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId != 0) context.getString(resId).trim() else ""
    } catch (e: Exception) {
        Log.e("Auth", "Default Web Client ID not found in resources. Ensure google-services.json is present and the plugin is applied.", e)
        ""
    }
}

private fun isConfigErrorMessage(message: String?): Boolean {
    val m = message?.lowercase().orEmpty()
    return m.contains("28444") ||
        m.contains("developer") ||
        m.contains("10:") ||
        m.contains("sha") ||
        m.contains("client_id")
}

private fun shouldTriggerLegacyFallbackOnCancel(e: GetCredentialCancellationException): Boolean {
    val m = e.message?.lowercase().orEmpty()
    return m.contains("blocked") ||
        m.contains("reauth") ||
        m.contains("cannot find") ||
        m.contains("16:")
}

private fun shouldFallbackToLegacy(e: Exception): Boolean {
    if (e is FirebaseAuthException || e is FirebaseNetworkException) return false
    return e is GetCredentialException ||
        e is NoCredentialException ||
        e is GetCredentialProviderConfigurationException ||
        e is GetCredentialUnsupportedException
}

internal fun formatGoogleAuthError(
    context: Context,
    error: Throwable,
    codeOverride: Int? = null
): String {
    val rawMessage = error.localizedMessage ?: error.message ?: "Sign-in failed"
    val normalized = rawMessage.lowercase()
    val isDeveloperConfigError = codeOverride == CommonStatusCodes.DEVELOPER_ERROR ||
        normalized.contains("10:") ||
        normalized.contains("developer_error") ||
        normalized.contains("developer console") ||
        normalized.contains("28444") ||
        normalized.contains("12500")

    return when {
        isDeveloperConfigError -> {
            val sha1 = getAppSigningSha1(context)
            val sha1Hint = if (sha1 != null) " Installed APK SHA-1: $sha1." else ""
            "Google Sign-In configuration error (${codeOverride ?: "10"}). Verify Google Sign-In is enabled in Firebase Authentication and this app's SHA-1 is registered in Firebase Project Settings.$sha1Hint"
        }
        error is FirebaseAuthException && error.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "Google Sign-In provider is disabled in Firebase Authentication Console. Enable Google under Authentication -> Sign-in method."
        error is FirebaseNetworkException ||
            codeOverride == CommonStatusCodes.NETWORK_ERROR ||
            normalized.contains("7:") ||
            normalized.contains("network_error") ->
            "Network Error: Please check your internet connection and device date/time."
        codeOverride == CommonStatusCodes.API_NOT_CONNECTED ||
            codeOverride == CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED ||
            normalized.contains("play services") ->
            "Google Play Services needs to be updated or enabled on this device to sign in with Google."
        else -> "Google Sign-In failed: $rawMessage"
    }
}

internal fun getAppSigningSha1(context: Context): String? {
    return try {
        val pm = context.packageManager
        val pkgName = context.packageName
        @Suppress("DEPRECATION")
        val signatures: Array<out Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES).signatures
        }
        val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
        digest.joinToString(":") { "%02X".format(it) }
    } catch (_: Exception) {
        null
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
    onSignOutComplete: () -> Unit = {},
    context: Context? = null
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear state: ${e.message}")
            }
            if (context != null) {
                try {
                    @Suppress("DEPRECATION")
                    Identity.getSignInClient(context).signOut().await()
                } catch (e: Exception) {
                    Log.w("Auth", "Failed to clear GIS SignInClient state: ${e.message}")
                }
            }
            try {
                Firebase.auth.signOut()
            } finally {
                onSignOutComplete()
            }
        }
    }
}
