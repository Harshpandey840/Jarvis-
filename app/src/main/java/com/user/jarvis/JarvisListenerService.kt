package com.user.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Context
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
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale

class JarvisListenerService : Service() {

    companion object {

        const val ACTION_STOP =
            "com.user.jarvis.ACTION_STOP"

        private const val CHANNEL_ID =
            "jarvis_listener_channel"

        private const val NOTIFICATION_ID =
            1001

        private const val TAG =
            "JarvisListenerService"

        private const val WAKE_RESTART_DELAY =
            700L

        private const val COMMAND_TIMEOUT =
            6000L
    }

    private val handler =
        Handler(Looper.getMainLooper())

    private var speechRecognizer:
            SpeechRecognizer? = null

    private var tts:
            TextToSpeech? = null

    private var wakeWordEngine:
            JarvisWakeWordEngine? = null

    private var wakeLock:
            PowerManager.WakeLock? = null

    private var commandExecutor:
            CommandExecutor? = null

    private var serviceActive = false

    private var waitingForCommand = false

    private var isSpeaking = false

    private var ttsReady = false

    private var speakThenListenForCommand = false

    private var commandTimeoutRunnable:
            Runnable? = null

    private var wakeRestartRunnable:
            Runnable? = null

    override fun onCreate() {

        super.onCreate()

        serviceActive = true

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification("Jarvis starting...")
        )

        acquireWakeLock()

        initializeTts()

        commandExecutor = CommandExecutor(
            applicationContext,
            { text ->
                handler.post {

                    if (serviceActive) {
                        speak(text)
                    }
                }
            },
            {
                handler.post {

                    if (
                        serviceActive &&
                        !isSpeaking &&
                        !waitingForCommand
                    ) {
                        scheduleWakeWordRestart(250L)
                    }
                }
            }
        )

        createSpeechRecognizer()

        wakeWordEngine =
            JarvisWakeWordEngine(
                applicationContext
            ) {
                onWakeWordDetected()
            }

        handler.postDelayed(
            {
                startWakeWordDetection()
            },
            1200L
        )
    }

    // ----------------------------------------------------
    // TEXT TO SPEECH
    // ----------------------------------------------------

    private fun initializeTts() {

        tts = TextToSpeech(
            applicationContext
        ) { status ->

            if (status == TextToSpeech.SUCCESS) {

                val hindiResult =
                    tts?.setLanguage(
                        Locale("hi", "IN")
                    )

                if (
                    hindiResult ==
                    TextToSpeech.LANG_MISSING_DATA ||
                    hindiResult ==
                    TextToSpeech.LANG_NOT_SUPPORTED
                ) {

                    tts?.setLanguage(
                        Locale("en", "IN")
                    )
                }

                tts?.setSpeechRate(1.0f)

                tts?.setPitch(1.0f)

                tts?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {

                        override fun onStart(
                            utteranceId: String?
                        ) {

                            handler.post {
                                isSpeaking = true
                            }
                        }

                        override fun onDone(
                            utteranceId: String?
                        ) {

                            handler.post {

                                isSpeaking = false

                                if (
                                    speakThenListenForCommand
                                ) {

                                    speakThenListenForCommand =
                                        false

                                    handler.postDelayed(
                                        {
                                            startCommandListening()
                                        },
                                        150L
                                    )

                                } else {

                                    if (
                                        serviceActive &&
                                        !waitingForCommand
                                    ) {

                                        scheduleWakeWordRestart(
                                            250L
                                        )
                                    }
                                }
                            }
                        }

                        override fun onError(
                            utteranceId: String?
                        ) {

                            handler.post {

                                isSpeaking = false

                                if (
                                    speakThenListenForCommand
                                ) {

                                    speakThenListenForCommand =
                                        false

                                    startCommandListening()

                                } else {

                                    scheduleWakeWordRestart(
                                        250L
                                    )
                                }
                            }
                        }
                    }
                )

                ttsReady = true

            } else {

                ttsReady = false
            }
        }
    }

    private fun speak(text: String) {

        if (text.isBlank()) {
            return
        }

        if (!ttsReady) {

            if (waitingForCommand) {
                startCommandListening()
            } else {
                scheduleWakeWordRestart(500L)
            }

            return
        }

        cancelWakeWordRestart()

        val utteranceId =
            "jarvis_${System.currentTimeMillis()}"

        isSpeaking = true

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    // ----------------------------------------------------
    // WAKE WORD
    // ----------------------------------------------------

    private fun startWakeWordDetection() {

        if (!serviceActive) return

        if (waitingForCommand) return

        if (isSpeaking) return

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.M
        ) {

            if (
                checkSelfPermission(
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                updateNotification(
                    "Microphone permission required"
                )

                return
            }
        }

        cancelRecognition()

        updateNotification(
            "Listening for Hey Jarvis"
        )

        wakeWordEngine?.restart()
    }

    private fun stopWakeWordDetection() {

        wakeWordEngine?.stop()
    }

    private fun onWakeWordDetected() {

        if (!serviceActive) return

        if (waitingForCommand) return

        if (isSpeaking) return

        stopWakeWordDetection()

        cancelWakeWordRestart()

        cancelRecognition()

        waitingForCommand = true

        speakThenListenForCommand = true

        updateNotification(
            "Jarvis activated"
        )

        startCommandTimeout()

        speak("Ji, bolo.")
    }

    private fun scheduleWakeWordRestart(
        delay: Long
    ) {

        cancelWakeWordRestart()

        if (!serviceActive) return

        wakeRestartRunnable =
            Runnable {

                if (
                    serviceActive &&
                    !waitingForCommand &&
                    !isSpeaking
                ) {

                    startWakeWordDetection()
                }
            }

        handler.postDelayed(
            wakeRestartRunnable!!,
            delay
        )
    }

    private fun cancelWakeWordRestart() {

        wakeRestartRunnable?.let {
            handler.removeCallbacks(it)
        }

        wakeRestartRunnable = null
    }

    // ----------------------------------------------------
    // SPEECH RECOGNITION
    // ----------------------------------------------------

    private fun createSpeechRecognizer() {

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                applicationContext
            )
        ) {
            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(
                applicationContext
            )

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: android.os.Bundle?
                ) {

                    updateNotification(
                        "Listening..."
                    )
                }

                override fun onBeginningOfSpeech() {

                    updateNotification(
                        "Listening to your command..."
                    )
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {

                    updateNotification(
                        "Processing..."
                    )
                }

                override fun onError(
                    error: Int
                ) {

                    handler.post {

                        if (!waitingForCommand) {
                            return@post
                        }

                        cancelCommandTimeout()

                        waitingForCommand = false

                        updateNotification(
                            "Wake word standby"
                        )

                        scheduleWakeWordRestart(
                            500L
                        )
                    }
                }

                override fun onResults(
                    results: android.os.Bundle?
                ) {

                    handler.post {

                        if (!waitingForCommand) {
                            return@post
                        }

                        cancelCommandTimeout()

                        val matches =
                            results?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )

                        val spokenText =
                            matches
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()

                        waitingForCommand = false

                        if (spokenText.isBlank()) {

                            scheduleWakeWordRestart(
                                300L
                            )

                            return@post
                        }

                        updateNotification(
                            "Executing..."
                        )

                        executeCommand(
                            spokenText
                        )
                    }
                }

                override fun onPartialResults(
                    partialResults: android.os.Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: android.os.Bundle?
                ) {
                }
            }
        )
    }

    private fun startCommandListening() {

        if (!serviceActive) return

        if (!waitingForCommand) {
            waitingForCommand = true
        }

        cancelWakeWordRestart()

        stopWakeWordDetection()

        cancelRecognition()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.M
        ) {

            if (
                checkSelfPermission(
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                waitingForCommand = false

                updateNotification(
                    "Microphone permission required"
                )

                return
            }
        }

        val recognizer =
            speechRecognizer
                ?: run {

                    createSpeechRecognizer()

                    speechRecognizer
                        ?: run {

                            waitingForCommand = false

                            scheduleWakeWordRestart(
                                500L
                            )

                            return
                        }
                }

        startCommandTimeout()

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "hi-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "hi-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )
            }

        try {

            updateNotification(
                "Listening for command..."
            )

            recognizer.startListening(
                intent
            )

        } catch (_: Exception) {

            waitingForCommand = false

            scheduleWakeWordRestart(
                500L
            )
        }
    }

    private fun cancelRecognition() {

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    // ----------------------------------------------------
    // COMMAND EXECUTION
    // ----------------------------------------------------

    private fun executeCommand(
        text: String
    ) {

        val command =
        private fun executeCommand(text: String) {

    try {

        commandExecutor?.execute(text)

    } catch (_: Exception) {

        speak("Command execute nahi ho paya.")
    }
        }
    }

    // ----------------------------------------------------
    // COMMAND TIMEOUT
    // ----------------------------------------------------

    private fun startCommandTimeout() {

        cancelCommandTimeout()

        commandTimeoutRunnable =
            Runnable {

                if (waitingForCommand) {

                    waitingForCommand = false

                    cancelRecognition()

                    updateNotification(
                        "Wake word standby"
                    )

                    speak(
                        "Koi command nahi mili."
                    )
                }
            }

        handler.postDelayed(
            commandTimeoutRunnable!!,
            COMMAND_TIMEOUT
        )
    }

    private fun cancelCommandTimeout() {

        commandTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }

        commandTimeoutRunnable = null
    }

    // ----------------------------------------------------
    // NOTIFICATION
    // ----------------------------------------------------

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Jarvis Assistant",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "Jarvis background assistant"

                    setShowBadge(false)
                }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification(
        text: String
    ): Notification {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        if (
                            Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.M
                        ) {
                            PendingIntent.FLAG_IMMUTABLE
                        } else {
                            0
                        }
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setContentTitle(
                "Jarvis"
            )
            .setContentText(
                text
            )
            .setContentIntent(
                pendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        if (!serviceActive) return

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            createNotification(text)
        )
    }

    // ----------------------------------------------------
    // WAKE LOCK
    // ----------------------------------------------------

    private fun acquireWakeLock() {

        try {

            val powerManager =
                getSystemService(
                    Context.POWER_SERVICE
                ) as PowerManager

            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Jarvis::WakeLock"
                )

            wakeLock?.acquire()
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {

        try {

            if (
                wakeLock?.isHeld == true
            ) {
                wakeLock?.release()
            }

        } catch (_: Exception) {
        }

        wakeLock = null
    }

    // ----------------------------------------------------
    // SERVICE
    // ----------------------------------------------------

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.action == ACTION_STOP
        ) {

            stopSelf()

            return START_NOT_STICKY
        }

        if (!serviceActive) {
            serviceActive = true
        }

        return START_STICKY
    }

    override fun onDestroy() {

        serviceActive = false

        cancelWakeWordRestart()

        cancelCommandTimeout()

        waitingForCommand = false

        speakThenListenForCommand = false

        stopWakeWordDetection()

        wakeWordEngine?.release()

        wakeWordEngine = null

        cancelRecognition()

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }

        tts = null

        releaseWakeLock()

        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
