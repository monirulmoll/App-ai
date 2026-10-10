package com.example.data.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.data.local.LocalChatPreferences
import com.example.data.model.GoogleUserProfile
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class GoogleAuthManager(private val context: Context) {
    private val tag = "GoogleAuthManager"
    private val prefs = LocalChatPreferences(context)
    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<GoogleUserProfile?>(prefs.getGoogleUserProfile())
    val currentUser: StateFlow<GoogleUserProfile?> = _currentUser.asStateFlow()

    val isLoggedIn: Boolean get() = _currentUser.value != null

    suspend fun signInWithGoogle(
        activity: ComponentActivity,
        serverClientId: String? = null
    ): Result<GoogleUserProfile> {
        return try {
            val effectiveClientId = serverClientId?.trim()?.ifEmpty { null }
                ?: "388281348523-gemoai.apps.googleusercontent.com"

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(effectiveClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val subId = extractGoogleSubjectId(idToken) ?: googleIdTokenCredential.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val stableGoogleUserId = "google_$subId"

                val profile = GoogleUserProfile(
                    googleUserId = stableGoogleUserId,
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.id.substringBefore("@"),
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    idToken = idToken,
                    lastLoginAt = System.currentTimeMillis()
                )

                prefs.saveGoogleUserProfile(profile)
                _currentUser.value = profile
                Log.d(tag, "Google Sign-In successful for sub: $stableGoogleUserId (email: ${profile.email})")
                Result.success(profile)
            } else {
                Result.failure(IllegalStateException("Unsupported credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(tag, "Google Sign-In cancelled by user")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(tag, "Google Sign-In Credential Exception: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Google Sign-In failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(tag, "Error clearing credential state: ${e.message}")
        }
        prefs.clearGoogleUserProfile()
        _currentUser.value = null
        Log.d(tag, "Google user signed out")
    }

    companion object {
        fun extractGoogleSubjectId(idToken: String?): String? {
            if (idToken.isNullOrBlank()) return null
            return try {
                val parts = idToken.split(".")
                if (parts.size >= 2) {
                    val decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                    val json = JSONObject(String(decodedBytes, Charsets.UTF_8))
                    val sub = json.optString("sub", "")
                    if (sub.isNotBlank()) sub else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
