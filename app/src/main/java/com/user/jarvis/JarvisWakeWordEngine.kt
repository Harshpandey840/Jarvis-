package com.user.jarvis

import android.content.Context
import android.util.Log
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class JarvisWakeWordEngine(
    private val context: Context,
    private val onWakeWord: () -> Unit
) {

    companion object {
        private const val TAG = "JarvisWakeWord"

        private const val MODEL_NAME = "Hey Jarvis"
        private const val MODEL_FILE = "hey_jarvis.onnx"

        private const val THRESHOLD = 0.10f
        private const val COOLDOWN_MS = 2000L
    }

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    private var engine: WakeWordEngine? = null
    private var detectionJob: Job? = null

    @Volatile
    private var running = false

    @Volatile
    private var processingWake = false

    fun start() {

        if (running) return

        try {

            if (engine == null) {

                val models = listOf(
                    WakeWordModel(
                        name = MODEL_NAME,
                        modelPath = MODEL_FILE,
                        threshold = THRESHOLD
                    )
                )

                engine = WakeWordEngine(
                    context = context,
                    models = models,
                    detectionCooldownMs = COOLDOWN_MS
                )
            }

            detectionJob?.cancel()

            detectionJob = scope.launch {

                engine!!
                    .detections
                    .catch { error ->

                        Log.e(
                            TAG,
                            "Wake word detection error",
                            error
                        )

                        running = false
                    }
                    .collect { detection ->

                        Log.d(
                            TAG,
                            "Detected: ${detection.model.name}, " +
                                    "score=${detection.score}"
                        )

                        if (
                            detection.model.name == MODEL_NAME &&
                            !processingWake
                        ) {

                            processingWake = true

                            launch(Dispatchers.Main) {

                                if (running) {
                                    onWakeWord()
                                }
                            }
                        }
                    }
            }

            engine?.start()

            running = true

            Log.d(
                TAG,
                "OpenWakeWord started"
            )

        } catch (error: Exception) {

            running = false

            Log.e(
                TAG,
                "OpenWakeWord start failed",
                error
            )
        }
    }

    fun stop() {

        if (!running) return

        try {
            engine?.stop()
        } catch (error: Exception) {

            Log.e(
                TAG,
                "OpenWakeWord stop failed",
                error
            )
        }

        running = false
    }

    fun restart() {

        stop()

        processingWake = false

        start()
    }

    fun release() {

        detectionJob?.cancel()
        detectionJob = null

        try {
            engine?.stop()
        } catch (_: Exception) {
        }

        try {
            engine?.release()
        } catch (_: Exception) {
        }

        engine = null

        running = false
        processingWake = false
    }

    fun isRunning(): Boolean {
        return running
    }
}
