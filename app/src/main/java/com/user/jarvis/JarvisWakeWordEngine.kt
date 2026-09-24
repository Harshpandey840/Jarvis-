package com.user.jarvis

import android.content.Context
import android.util.Log

class JarvisWakeWordEngine(
    private val context: Context,
    private val onWakeWord: () -> Unit
) {

    companion object {
        private const val TAG = "JarvisWakeWordEngine"
    }

    private var running = false

    fun start() {
        if (running) return

        running = true

        Log.d(
            TAG,
            "Wake word engine started"
        )

        /*
         * Temporary compatibility mode.
         *
         * OpenWakeWord integration will be connected
         * after the APK build is stable.
         */
    }

    fun stop() {
        running = false

        Log.d(
            TAG,
            "Wake word engine stopped"
        )
    }

    fun restart() {
        stop()
        start()
    }

    fun release() {
        running = false

        Log.d(
            TAG,
            "Wake word engine released"
        )
    }

    fun isRunning(): Boolean {
        return running
    }

    /*
     * Internal callback helper.
     * Future real wake-word detector can call this
     * when "Hey Jarvis" is detected.
     */
    fun notifyWakeWordDetected() {
        if (!running) return

        try {
            onWakeWord()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Wake word callback failed",
                e
            )
        }
    }
}
