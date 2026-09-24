package com.user.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class JarvisListenerService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JarvisListenerService"

        private const val CHANNEL_ID = "jarvis_voice_service"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.user.jarvis.ACTION_STOP"

        private const val COMMAND_TIMEOUT = 6000L
        private const val AFTER_SPEAK_DELAY = 350L
        private const val WAKE_RESTART_DELAY = 250L
        private const val START_DELAY = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private var serviceActive = false
    private var isSpeaking = false

    private var waitingForCommand = false
    private var speakThenListenForCommand = false

    private var recognizerStarting = false
    private var recognitionRunning = false

    private var wakeWordEngine: JarvisWakeWordEngine? = null

    private var wakeWordRestartRunnable: Runnable? = null
    private var commandTimeoutRunnable: Runnable? = null

    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var commandExecutor: CommandExecutor


    // ---------------------------------------------------------
    // SERVICE
    // ---------------------------------------------------------

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "JarvisListenerService created")

        serviceActive = true

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            createNotification("Jarvis listening for Hey Jarvis")
        )

        acquireWakeLock()

        tts = TextToSpeech(this, this)

        commandExecutor = CommandExecutor(
            this,
            { text -> speak(text) },
            { startWakeWordDetection() }
        )

        createSpeechRecognizer()

        try {
            wakeWordEngine = JarvisWakeWordEngine(
                context = this,
                onWakeWordDetected = {
                    handler.post {
                        onWakeWordDetected()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create wake word engine", e)
            wakeWordEngine = null
        }

        handler.postDelayed(
            {
                if (serviceActive) {
                    startWakeWordDetection()
                }
            },
            START_DELAY
        )
    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopJarvisService()
            return START_NOT_STICKY
        }

        if (!serviceActive) {
            serviceActive = true
        }

        return START_STICKY
    }


    override fun onDestroy() {
        Log.d(TAG, "JarvisListenerService destroyed")

        serviceActive = false

        cancelRecognition()
        cancelCommandTimeout()
        cancelWakeRestart()

        try {
            wakeWordEngine?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Wake engine stop error", e)
        }

        wakeWordEngine = null

        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {
        }

        releaseWakeLock()

        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    // ---------------------------------------------------------
    // TEXT TO SPEECH
    // ---------------------------------------------------------

    override fun onInit(status: Int) {

        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS initialization failed")
            return
        }

        val result = tts.setLanguage(Locale("hi", "IN"))

        tts.setSpeechRate(1.0f)
        tts.setPitch(1.0f)

        tts.setOnUtteranceProgressListener(
            object : android.speech.tts.UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }

                override fun onDone(utteranceId: String?) {
                    handler.post {
                        isSpeaking = false

                        if (
                            speakThenListenForCommand &&
                            serviceActive
                        ) {
                            speakThenListenForCommand = false

                            handler.postDelayed(
                                {
                                    if (serviceActive) {
                                        startCommandListening()
                                    }
                                },
                                AFTER_SPEAK_DELAY
                            )
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    handler.post {
                        isSpeaking = false

                        if (
                            speakThenListenForCommand &&
                            serviceActive
                        ) {
                            speakThenListenForCommand = false
                            startCommandListening()
                        }
                    }
                }
            }
        )

        ttsReady = true

        Log.d(TAG, "TTS ready. Language result = $result")
    }


    private fun speak(text: String) {

        if (!serviceActive) return
        if (!ttsReady) return
        if (text.isBlank()) return

        Log.d(TAG, "Jarvis speaking: $text")

        isSpeaking = true

        val utteranceId =
            "jarvis_${System.currentTimeMillis()}"

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }


    // ---------------------------------------------------------
    // WAKE WORD DETECTION
    // ---------------------------------------------------------

    private fun startWakeWordDetection() {

        if (!serviceActive) return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            updateNotification("Microphone permission required")
            return
        }

        if (waitingForCommand) return
        if (isSpeaking) return

        cancelRecognition()
        cancelCommandTimeout()
        cancelWakeRestart()

        try {
            updateNotification("Listening for Hey Jarvis")

            wakeWordEngine?.restart()

            Log.d(TAG, "Wake word detection started")

        } catch (e: Exception) {
            Log.e(TAG, "Wake word start error", e)

            scheduleWakeWordRestart()
        }
    }


    private fun onWakeWordDetected() {

        if (!serviceActive) return
        if (waitingForCommand) return

        Log.d(TAG, "HEY JARVIS DETECTED")

        cancelWakeRestart()

        try {
            wakeWordEngine?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Wake engine stop error", e)
        }

        cancelRecognition()

        waitingForCommand = true

        updateNotification("Jarvis activated")

        speakThenListenForCommand = true

        speak("Ji, bolo.")

        startCommandTimeout()
    }


    private fun scheduleWakeWordRestart() {

        if (!serviceActive) return

        cancelWakeRestart()

        wakeWordRestartRunnable = Runnable {

            if (serviceActive && !waitingForCommand) {
                startWakeWordDetection()
            }
        }

        handler.postDelayed(
            wakeWordRestartRunnable!!,
            WAKE_RESTART_DELAY
        )
    }


    private fun cancelWakeRestart() {

        wakeWordRestartRunnable?.let {
            handler.removeCallbacks(it)
        }

        wakeWordRestartRunnable = null
    }


    // ---------------------------------------------------------
    // SPEECH RECOGNIZER
    // ---------------------------------------------------------

    private fun createSpeechRecognizer() {

        if (!SpeechRecognizer.isRecognition
