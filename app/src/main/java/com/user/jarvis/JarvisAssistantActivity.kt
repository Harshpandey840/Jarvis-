package com.user.jarvis

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class JarvisAssistantActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start JarvisListenerService with the assist command action
        val intent = Intent(this, JarvisListenerService::class.java).apply {
            action = "com.user.jarvis.ACTION_START_ASSISTANT_COMMAND"
        }

        // Use standard startService for normal flow,
        // JarvisListenerService should already be foreground or handle its foreground state
        try {
            startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback for Android 8.0+ strict background rules if service isn't running
            try {
                androidx.core.content.ContextCompat.startForegroundService(this, intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        // Finish the activity immediately to remain invisible to the user
        finish()
    }
}
