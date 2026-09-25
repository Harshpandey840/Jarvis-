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
import kotlinx.coroutines.withContext
import java.io.File

class JarvisWakeWordEngine(
    private val context: Context,
    private val onWakeWord: () -> Unit
) {

    companion object {
        private const val TAG = "JarvisWakeWordEngine"
    }

    private var running = false
    private var engine: WakeWordEngine? = null
    private var engineScope: CoroutineScope? = null

    private fun copyModelsToFilesDir() {
        val models = listOf("hey_jarvis_v0.1.onnx", "melspectrogram.onnx", "embedding_model.onnx")
        for (model in models) {
            val file = File(context.filesDir, model)
            if (!file.exists()) {
                context.assets.open(model).use { inputStream ->
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
    }

    fun start() {
        if (running) return
        running = true

        Log.d(TAG, "Wake word engine starting")

        engineScope = CoroutineScope(Dispatchers.Default + Job())

        engineScope?.launch {
            withContext(Dispatchers.IO) {
                copyModelsToFilesDir()
            }

            if (!running) return@launch

            val wakeWordModelPath = File(context.filesDir, "hey_jarvis_v0.1.onnx").absolutePath
            val models = listOf(WakeWordModel("Hey Jarvis", wakeWordModelPath, 0.5f))

            engine = WakeWordEngine(
                context,
                models,
                DetectionMode.SINGLE_BEST,
                500L,
                engineScope!!
            )

            launch {
                try {
                    engine?.detections?.collect { detection ->
                        if (detection.model.name == "Hey Jarvis" && detection.score >= 0.5f) {
                            Log.d(TAG, "Wake word detected: ${detection.score}")
                            notifyWakeWordDetected()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Detection collection error", e)
                }
            }

            engine?.start()
            Log.d(TAG, "Wake word engine started")
        }
    }

    fun stop() {
        if (!running) return
        running = false

        Log.d(TAG, "Wake word engine stopped")
        engine?.stop()
        engineScope?.cancel()
        engineScope = null
        engine = null
    }

    fun restart() {
        stop()
        start()
    }

    fun release() {
        stop()
        engine?.release()
        engine = null
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
