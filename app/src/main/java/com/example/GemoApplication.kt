package com.example

import android.app.Application
import android.util.Log
import com.example.data.firebase.FirebaseInitializer

class GemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseInitializer.ensureInitialized(this)
            Log.d("GemoApplication", "FirebaseApp successfully initialized in GemoApplication.onCreate")
        } catch (e: Exception) {
            Log.e("GemoApplication", "Error initializing FirebaseApp in GemoApplication.onCreate", e)
        }
    }
}
