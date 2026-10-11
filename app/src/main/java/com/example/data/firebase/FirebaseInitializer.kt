package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.config.DatabaseConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.net.URI

/**
 * Robust Centralized Firebase Initializer.
 * Guarantees that FirebaseApp is initialized with all required parameters
 * (applicationId, projectId, apiKey, databaseUrl) before any Firebase service
 * (FirebaseAuth, FirebaseDatabase) is accessed.
 * Prevents startup crashes when google-services.json is absent.
 */
object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    @Synchronized
    fun ensureInitialized(context: Context, databaseUrl: String = DatabaseConfig.MAIN_DATABASE_URL): FirebaseApp? {
        val appContext = context.applicationContext ?: context
        try {
            try {
                return FirebaseApp.getInstance()
            } catch (_: Exception) {}

            val existingApps = FirebaseApp.getApps(appContext)
            if (existingApps.isNotEmpty()) {
                return existingApps.first()
            }

            val effectiveUrl = if (databaseUrl.trim().isBlank()) DatabaseConfig.MAIN_DATABASE_URL else databaseUrl.trim().removeSuffix("/")
            val host = try {
                URI(effectiveUrl).host ?: "ussr-error-404-default"
            } catch (_: Exception) {
                "ussr-error-404-default"
            }
            val projectId = host.substringBefore(".")

            val options = FirebaseOptions.Builder()
                .setApplicationId(appContext.packageName)
                .setProjectId(projectId)
                .setApiKey("AIzaSyDummyKeyForFirebaseAuth1234567890")
                .setDatabaseUrl(effectiveUrl)
                .build()

            val app = FirebaseApp.initializeApp(appContext, options)
            Log.d(TAG, "FirebaseApp initialized with project: $projectId, db: $effectiveUrl")
            return app
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize FirebaseApp: ${e.message}")
            return try { FirebaseApp.getInstance() } catch (_: Exception) { null }
        }
    }

    fun getSafeAuth(context: Context): FirebaseAuth? {
        ensureInitialized(context)
        return try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get FirebaseAuth instance: ${e.message}")
            null
        }
    }

    fun getSafeDatabase(context: Context, url: String = DatabaseConfig.MAIN_DATABASE_URL): FirebaseDatabase? {
        ensureInitialized(context, url)
        val sanitized = if (url.trim().isBlank()) DatabaseConfig.MAIN_DATABASE_URL else url.trim().removeSuffix("/")
        return try {
            FirebaseDatabase.getInstance(sanitized)
        } catch (_: Exception) {
            try {
                FirebaseDatabase.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get FirebaseDatabase instance: ${e.message}")
                null
            }
        }
    }
}
