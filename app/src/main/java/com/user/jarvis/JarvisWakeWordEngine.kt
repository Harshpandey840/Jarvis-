package com.user.jarvis

import android.content.Context
import android.util.Log
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class JarvisWakeWordEngine(
    private val context: Context,
    private val onWakeWord: () -> Unit
) {

    companion object {
        private const val TAG = "JarvisWakeWordEngine"
    }

    private var running = false
    private var wakeWordEngine: WakeWordEngine? = null
    private var scope: CoroutineScope? = null
    private var modelFile: File? = null

    init {
        try {
            // Unpack model from assets
            val file = File(context.filesDir, "hey_jarvis.tflite")
            if (!file.exists()) {
                context.assets.open("hey_jarvis_v0.1.tflite").use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            modelFile = file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model", e)
        }
    }

    fun start() {
        if (running) return

        val file = modelFile
        if (file == null || !file.exists()) {
            Log.e(TAG, "Model file missing")
            return
        }

        running = true
        scope = CoroutineScope(Dispatchers.Default + Job())

        val model = WakeWordModel("hey_jarvis", file.absolutePath, 0.5f)

        try {
            wakeWordEngine = WakeWordEngine(
                context,
                listOf(model),
                DetectionMode.SINGLE_BEST,
                1000L,
                scope!!
            )

            scope?.launch {
                wakeWordEngine!!.detections.collect { detection ->
                    if (running) {
                        Log.d(TAG, "Wake word detected: \${detection.model.name} score: \${detection.score}")
                        notifyWakeWordDetected()
                    }
                }
            }

            wakeWordEngine?.start()
            Log.d(TAG, "Wake word engine started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word engine", e)
            running = false
        }
    }

    fun stop() {
        if (!running) return
        running = false

        try {
            wakeWordEngine?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }

        scope?.cancel()
        scope = null

        Log.d(TAG, "Wake word engine stopped")
    }

    fun restart() {
        stop()
        start()
    }

    fun release() {
        stop()
        try {
            wakeWordEngine?.release()
            wakeWordEngine = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing", e)
        }
        Log.d(TAG, "Wake word engine released")
    }

    fun isRunning(): Boolean {
        return running
    }

    fun notifyWakeWordDetected() {
        if (!running) return

        try {
            onWakeWord()
        } catch (e: Exception) {
            Log.e(TAG, "Wake word callback failed", e)
        }
    }
}
