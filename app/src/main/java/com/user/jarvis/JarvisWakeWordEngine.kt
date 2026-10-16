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
import kotlinx.coroutines.flow.catch
import java.io.File
import java.io.FileOutputStream

class JarvisWakeWordEngine(
    private val context: Context,
    private val onWakeWord: () -> Unit
) : IWakeWordDetector {

    companion object {
        private const val TAG = "JarvisWakeWordEngine"
    }

    private var wakeWordEngine: WakeWordEngine? = null
    private var scope: CoroutineScope? = null
    private var modelFile: File? = null

    private val manager = WakeWordEngineManager(
        modelProvider = {
            val file = modelFile
            if (file != null && file.exists()) {
                Pair(file.absolutePath, file.length())
            } else {
                Pair(null, 0L)
            }
        },
        detector = this,
        onWakeWord = onWakeWord
    )

    init {
        try {
            // Unpack model from assets
            val file = File(context.filesDir, "hey_jarvis.onnx")
            if (!file.exists()) {
                context.assets.open("hey_jarvis_v0.1.onnx").use { input ->
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
        manager.start()
    }

    fun stop() {
        manager.stop()
    }

    fun restart() {
        manager.restart()
    }

    fun release() {
        manager.release()
    }

    fun isRunning(): Boolean {
        return manager.isRunning()
    }

    override fun startDetector(modelPath: String, onDetection: (score: Float) -> Unit): Boolean {
        scope = CoroutineScope(Dispatchers.Default + Job())
        val model = WakeWordModel("hey_jarvis", modelPath, 0.5f)

        return try {
            wakeWordEngine = WakeWordEngine(
                context = context,
                models = listOf(model),
                detectionMode = DetectionMode.SINGLE_BEST,
                detectionCooldownMs = 1000L,
                scope = scope!!
            )

            scope?.launch {
                wakeWordEngine?.detections?.catch { e ->
                    Log.e(TAG, "Error collecting detections", e)
                }?.collect { detection ->
                    Log.d(TAG, "Wake word detected: \${detection.model.name} with score \${detection.score}")
                    onDetection(detection.score)
                }
            }

            wakeWordEngine?.start()
            Log.d(TAG, "Wake word engine initialized and model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word engine", e)
            false
        }
    }

    override fun stopDetector() {
        try {
            wakeWordEngine?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }
        scope?.cancel()
        scope = null
        Log.d(TAG, "Wake word engine stopped")
    }

    override fun releaseDetector() {
        try {
            wakeWordEngine?.release()
            wakeWordEngine = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing", e)
        }
        Log.d(TAG, "Wake word engine released")
    }
}
