package com.user.jarvis

/**
 * Interface representing the capability to start, stop, and configure a wake word detector loop.
 */
interface IWakeWordDetector {
    fun startDetector(modelPath: String, onDetection: (score: Float) -> Unit): Boolean
    fun stopDetector()
    fun releaseDetector()
}

/**
 * Pure Kotlin class managing wake word states, validity, and callbacks independent of Android.
 */
class WakeWordEngineManager(
    private val modelProvider: () -> Pair<String?, Long>, // Returns path and file size in bytes
    private val detector: IWakeWordDetector,
    private val onWakeWord: () -> Unit
) {
    private var running = false

    fun start() {
        if (running) return

        val (modelPath, size) = modelProvider()
        if (modelPath == null) {
            // Log.e("WakeWordEngineManager", "Model file missing")
            return
        }

        if (size == 0L) {
            // Log.e("WakeWordEngineManager", "Model file is empty. Cannot initialize Wake Word Engine.")
            return
        }

        // We only set running to true if the underlying detector successfully initializes
        val success = detector.startDetector(modelPath) { score ->
            if (score >= 0.5f) {
                notifyWakeWordDetected()
            }
        }

        if (success) {
            running = true
        }
    }

    fun stop() {
        if (!running) return
        running = false
        detector.stopDetector()
    }

    fun restart() {
        stop()
        start()
    }

    fun release() {
        stop()
        detector.releaseDetector()
    }

    fun isRunning(): Boolean {
        return running
    }

    fun notifyWakeWordDetected() {
        if (!running) return
        try {
            onWakeWord()
        } catch (e: Exception) {
            // Log.e("WakeWordEngineManager", "Wake word callback failed", e)
        }
    }
}
