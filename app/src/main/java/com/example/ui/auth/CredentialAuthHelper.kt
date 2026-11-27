package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.firebase.isPlaceholderFirebaseValue
import com.example.data.firebase.safeFirebaseAuth
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private const val AUTH_TAG = "Auth"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun getApkSigningSha1(context: Context): String? {
    return try {
        val pm = context.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return null
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return null

        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        digest.joinToString(":") { "%02X".format(it) }
    } catch (_: Throwable) {
        null
    }
}

private fun readWebClientIdSafely(context: Context): String? {
    return try {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) {
            null
        } else {
            context.getString(resId).trim().takeIf { !isPlaceholderFirebaseValue(it) }
        }
    } catch (_: Throwable) {
        null
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val auth = safeFirebaseAuth()
    if (auth == null) {
        onUnauthenticated()
        return
    }
    if (auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    if (credentialManager == null) {
        onUnauthenticated()
        return
    }
    val clientId = readWebClientIdSafely(context)
    if (clientId == null) {
        onUnauthenticated()
        return
    }

    scope.launch {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (detail: String?, apkSha1: String?) -> Unit = { _, _ -> }
) {
    val auth = safeFirebaseAuth()
    if (auth == null) {
        onAuthError("Firebase Authentication is not initialized. Check app/google-services.json.")
        return
    }
    if (credentialManager == null) {
        onAuthError("Google Credential Manager is unavailable on this device.")
        return
    }
    val clientId = readWebClientIdSafely(context)
    if (clientId == null) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found or invalid.")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Unable to start Google Sign-In without an Activity context.")
        return
    }

    scope.launch {
        try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned by Google Sign-In.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val sha1 = getApkSigningSha1(context)
            val rawMsg = e.localizedMessage ?: e.message
            if (rawMsg?.contains("16") == true || rawMsg?.contains("reauth", ignoreCase = true) == true) {
                withContext(NonCancellable) {
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (_: Throwable) {
                    }
                }
            }
            val detail = listOfNotNull(
                rawMsg?.takeIf { it.isNotBlank() },
                sha1?.let { "APK SHA-1: $it" }
            ).joinToString(" • ").ifBlank { null }
            onAuthCancelled(detail, sha1)
        } catch (t: Throwable) {
            Log.e(AUTH_TAG, "Google Sign-In failed", t)
            val sha1 = getApkSigningSha1(context)
            val msg = t.localizedMessage ?: "Sign in failed"
            onAuthError(if (sha1 != null) "$msg (APK SHA-1: $sha1)" else msg)
        }
    }
}

fun signOut(
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager?.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                try {
                    safeFirebaseAuth()?.signOut()
                } catch (t: Throwable) {
                    Log.w(AUTH_TAG, "Failed to sign out from FirebaseAuth: ${t.message}")
                }
                onSignOutComplete()
            }
        }
    }
}
