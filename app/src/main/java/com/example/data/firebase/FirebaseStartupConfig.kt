package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val STARTUP_TAG = "FirebaseStartup"

data class FirebaseReadiness(
    val isReady: Boolean,
    val missingItems: List<String> = emptyList(),
    val detailMessage: String? = null
)

internal fun isPlaceholderFirebaseValue(value: String?): Boolean {
    if (value.isNullOrBlank()) return true
    val trimmed = value.trim()
    val lower = trimmed.lowercase()
    return lower == "null" ||
        lower.contains("placeholder") ||
        lower.contains("remixed-project-id") ||
        lower.contains("your_") ||
        lower.contains("your-") ||
        lower.contains("replace_me") ||
        lower.contains("changeme") ||
        lower.contains("todo") ||
        lower == "default"
}

private fun readStringResourceSafely(context: Context, name: String): String? {
    return try {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        if (resId == 0) null else context.getString(resId)
    } catch (_: Throwable) {
        null
    }
}

fun ensureFirebaseAppInitialized(context: Context): Boolean {
    return try {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            true
        } else {
            FirebaseApp.initializeApp(context) != null
        }
    } catch (t: Throwable) {
        Log.e(STARTUP_TAG, "FirebaseApp.initializeApp failed", t)
        false
    }
}

fun safeFirebaseAuth(): FirebaseAuth? {
    return try {
        Firebase.auth
    } catch (t: Throwable) {
        Log.e(STARTUP_TAG, "FirebaseAuth unavailable", t)
        null
    }
}

fun safeFirebaseFirestore(databaseId: String): FirebaseFirestore? {
    if (isPlaceholderFirebaseValue(databaseId)) return null
    return try {
        FirebaseFirestore.getInstance(databaseId)
    } catch (t: Throwable) {
        Log.e(STARTUP_TAG, "FirebaseFirestore unavailable for database '$databaseId'", t)
        null
    }
}

fun evaluateFirebaseReadiness(context: Context): FirebaseReadiness {
    val appContext = context.applicationContext
    val missing = mutableListOf<String>()

    val requiredGoogleServicesStrings = listOf(
        "google_app_id" to "google_app_id (from app/google-services.json)",
        "google_api_key" to "google_api_key (from app/google-services.json)",
        "project_id" to "project_id (from app/google-services.json)",
        "default_web_client_id" to "default_web_client_id (OAuth Web Client ID in google-services.json)",
        "firestore_database_id" to "firestore_database_id (in res/values/firebase_applet_config.xml)"
    )

    val resolvedValues = mutableMapOf<String, String>()
    for ((key, label) in requiredGoogleServicesStrings) {
        val raw = readStringResourceSafely(appContext, key)
        if (isPlaceholderFirebaseValue(raw)) {
            missing += label
        } else if (raw != null) {
            resolvedValues[key] = raw.trim()
        }
    }

    val appInitialized = ensureFirebaseAppInitialized(appContext)
    if (!appInitialized && "google_app_id (from app/google-services.json)" !in missing) {
        missing += "Default FirebaseApp failed to initialize (check app/google-services.json)"
    }

    if (missing.isNotEmpty()) {
        return FirebaseReadiness(
            isReady = false,
            missingItems = missing,
            detailMessage = "Firebase configuration is missing or contains placeholder values. Ensure app/google-services.json and res/values/firebase_applet_config.xml are present when building the APK."
        )
    }

    val auth = safeFirebaseAuth()
    if (auth == null) {
        return FirebaseReadiness(
            isReady = false,
            missingItems = listOf("Firebase Authentication instance"),
            detailMessage = "Firebase Authentication could not be initialized. Verify app/google-services.json matches your Firebase project."
        )
    }

    val dbId = resolvedValues["firestore_database_id"].orEmpty()
    val firestore = safeFirebaseFirestore(dbId)
    if (firestore == null) {
        return FirebaseReadiness(
            isReady = false,
            missingItems = listOf("Cloud Firestore instance ($dbId)"),
            detailMessage = "Cloud Firestore could not be initialized for database '$dbId'."
        )
    }

    return FirebaseReadiness(isReady = true)
}
