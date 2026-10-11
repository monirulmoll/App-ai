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

    const val DEFAULT_API_KEY = "AIzaSyC7BGNxgeHIwg8N_EwbqOhno7BrjceL0So"
    const val DEFAULT_PROJECT_ID = "ussr-error-404"
    const val DEFAULT_FIREBASE_APP_ID = "1:96131864914:android:6dbb110c23cd195cd3c28b"
    const val DEFAULT_APPLICATION_ID = "com.aistudio.gemoai.rtdbch"
    const val DEFAULT_GCM_SENDER_ID = "96131864914"

    @Synchronized
    fun ensureInitialized(context: Context, databaseUrl: String = DatabaseConfig.MAIN_DATABASE_URL): FirebaseApp? {
        val appContext = context.applicationContext ?: context
        val effectiveUrl = if (databaseUrl.trim().isBlank()) DatabaseConfig.MAIN_DATABASE_URL else databaseUrl.trim().removeSuffix("/")
        try {
            // Check existing instances
            val existingApps = FirebaseApp.getApps(appContext)
            if (existingApps.isNotEmpty()) {
                val app = existingApps.first()
                if (app.options.apiKey.contains("DummyKey") || app.options.databaseUrl.isNullOrBlank()) {
                    Log.w(TAG, "Existing FirebaseApp missing databaseUrl or had dummy key, configuring...")
                    try {
                        val baseOptions = try {
                            FirebaseOptions.fromResource(appContext)
                        } catch (_: Exception) {
                            app.options
                        } ?: app.options

                        val newOptions = FirebaseOptions.Builder(baseOptions)
                            .setDatabaseUrl(effectiveUrl)
                            .setApplicationId(
                                if (baseOptions.applicationId.contains(":")) baseOptions.applicationId else DEFAULT_FIREBASE_APP_ID
                            )
                            .setProjectId(if (baseOptions.projectId.isNullOrBlank()) DEFAULT_PROJECT_ID else baseOptions.projectId)
                            .setApiKey(if (baseOptions.apiKey.contains("DummyKey")) DEFAULT_API_KEY else baseOptions.apiKey)
                            .build()

                        app.delete()
                        return FirebaseApp.initializeApp(appContext, newOptions)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to recreate FirebaseApp: ${e.message}")
                        return app
                    }
                } else {
                    return app
                }
            }

            val resourceOptions = try {
                FirebaseOptions.fromResource(appContext)
            } catch (_: Exception) {
                null
            }

            val options = if (resourceOptions != null && !resourceOptions.apiKey.contains("DummyKey")) {
                FirebaseOptions.Builder(resourceOptions)
                    .setDatabaseUrl(effectiveUrl)
                    .setApplicationId(
                        if (resourceOptions.applicationId.contains(":")) resourceOptions.applicationId else DEFAULT_FIREBASE_APP_ID
                    )
                    .build()
            } else {
                FirebaseOptions.Builder()
                    .setApplicationId(DEFAULT_FIREBASE_APP_ID)
                    .setGcmSenderId(DEFAULT_GCM_SENDER_ID)
                    .setProjectId(DEFAULT_PROJECT_ID)
                    .setApiKey(DEFAULT_API_KEY)
                    .setDatabaseUrl(effectiveUrl)
                    .build()
            }

            val app = FirebaseApp.initializeApp(appContext, options)
            Log.d(TAG, "FirebaseApp initialized with project: ${options.projectId}, db: ${options.databaseUrl}")
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
        val sanitized = if (url.trim().isBlank()) DatabaseConfig.MAIN_DATABASE_URL else url.trim().removeSuffix("/")
        ensureInitialized(context, sanitized)
        return try {
            FirebaseDatabase.getInstance(sanitized)
        } catch (e1: Exception) {
            try {
                val app = FirebaseApp.getInstance()
                FirebaseDatabase.getInstance(app, sanitized)
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to get FirebaseDatabase instance: ${e2.message}")
                null
            }
        }
    }
}
