package com.user.jarvis

import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class JarvisWakeWordEngine(
    private val context: Context,
    private val onWakeWord: () -> Unit
) {

    companion object {
        private const val TAG = "JarvisWakeWordEngine"
        val rmsFlow = MutableStateFlow(0f)
    }

    private var running = false
    private var audioScope: CoroutineScope? = null

    // Internal processor tracking via reflection
    private var audioProcessorInstance: Any? = null
    private var onnxRunnerInstance: Any? = null
    private var predictMethod: java.lang.reflect.Method? = null

    private var lastDetectionTime = 0L

    fun start() {
        if (running) return
        running = true

        Log.d(TAG, "Wake word engine starting via Reflection")

        audioScope = CoroutineScope(Dispatchers.IO + Job())

        audioScope?.launch {
            try {
                // Read OpenWakeWord SDK constants via Reflection
                val audioRecorderClass = Class.forName("com.rementia.openwakeword.lib.audio.AudioRecorder")
                val sampleRate = audioRecorderClass.getField("SAMPLE_RATE").getInt(null)
                val channelConfig = audioRecorderClass.getField("CHANNEL_CONFIG").getInt(null)
                val audioFormat = audioRecorderClass.getField("AUDIO_FORMAT").getInt(null)
                val bufferSizeInShorts = audioRecorderClass.getField("BUFFER_SIZE_IN_SHORTS").getInt(null)

                // Initialize OpenWakeWord SDK manually via Reflection
                val onnxRunnerClass = Class.forName("com.rementia.openwakeword.lib.ml.OnnxModelRunner")
                val onnxConstructor = onnxRunnerClass.getConstructor(android.content.res.AssetManager::class.java, String::class.java)
                onnxRunnerInstance = onnxConstructor.newInstance(context.assets, "hey_jarvis_v0.1.onnx")

                val audioProcessorClass = Class.forName("com.rementia.openwakeword.lib.audio.AudioProcessor")
                val processorConstructor = audioProcessorClass.getConstructor(android.content.res.AssetManager::class.java, onnxRunnerClass)
                audioProcessorInstance = processorConstructor.newInstance(context.assets, onnxRunnerInstance)

                predictMethod = audioProcessorClass.getMethod("predictWakeWord", FloatArray::class.java)

                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    channelConfig,
                    audioFormat
                ).coerceAtLeast(bufferSizeInShorts * 2)

                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()
                    Log.d(TAG, "Wake word engine started via reflection")

                    val shortBuffer = ShortArray(bufferSizeInShorts)
                    val floatBuffer = FloatArray(bufferSizeInShorts)

                    while (isActive && running) {
                        val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                        if (read > 0) {
                            var sum = 0.0

                            // Convert short array (PCM) to float array [-1.0f, 1.0f]
                            for (i in 0 until read) {
                                val s = shortBuffer[i]
                                floatBuffer[i] = s / 32768f
                                sum += (s * s).toDouble()
                            }

                            val rms = sqrt(sum / read).toFloat()
                            rmsFlow.value = rms

                            // Predict wake word via reflection
                            val score = predictMethod?.invoke(audioProcessorInstance, floatBuffer) as? Float ?: 0f

                            if (score >= 0.5f) {
                                val now = System.currentTimeMillis()
                                if (now - lastDetectionTime > 1500L) { // Prevent burst triggering
                                    Log.d(TAG, "Wake word detected: $score")
                                    lastDetectionTime = now
                                    notifyWakeWordDetected()
                                }
                            }
                        }
                    }
                    audioRecord.stop()
                    audioRecord.release()
                } else {
                    Log.e(TAG, "AudioRecord initialization failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Custom AudioRecord loop failed", e)
            } finally {
                try {
                    val audioProcessorClose = audioProcessorInstance?.javaClass?.getMethod("close")
                    audioProcessorClose?.invoke(audioProcessorInstance)

                    val onnxRunnerClose = onnxRunnerInstance?.javaClass?.getMethod("close")
                    onnxRunnerClose?.invoke(onnxRunnerInstance)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to close reflection instances", e)
                }

                audioProcessorInstance = null
                onnxRunnerInstance = null
                predictMethod = null
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false

        Log.d(TAG, "Wake word engine stopped")
        audioScope?.cancel()
        audioScope = null
    }

    fun restart() {
        stop()
        start()
    }

    fun release() {
        stop()
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
