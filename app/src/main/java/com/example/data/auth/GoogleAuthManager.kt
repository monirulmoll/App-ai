package com.example.data.auth

import android.content.Context
import com.example.data.local.LocalChatPreferences
import com.example.data.model.GoogleUserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Deprecated wrapper maintained to prevent breaking any legacy call sites
class GoogleAuthManager(context: Context) {
    private val prefs = LocalChatPreferences(context)
    private val _currentUser = MutableStateFlow<GoogleUserProfile?>(prefs.getGoogleUserProfile())
    val currentUser: StateFlow<GoogleUserProfile?> = _currentUser.asStateFlow()

    val isLoggedIn: Boolean get() = prefs.isLoggedIn()

    suspend fun signOut() {
        prefs.clearAuthSession()
        _currentUser.value = null
    }
}
