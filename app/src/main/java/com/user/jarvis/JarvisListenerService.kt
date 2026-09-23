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
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.UUID

class JarvisListenerService : Service(),
    TextToSpeech.OnInitListener {

    companion object {

        private const val CHANNEL_ID =
            "jarvis_voice_service"

        private const val NOTIFICATION_ID =
            1001

        // MainActivity uses this action to stop Jarvis.
        const val ACTION_STOP =
            "com.user.jarvis.ACTION_STOP"

        private const val COMMAND_TIMEOUT =
            6000L

        private const val AFTER_SPEAK_DELAY =
            350L

        private const val WAKE_RESTART_DELAY =
            250L

        private const val START_DELAY =
            1000L
    }

    // ============================================================
    // CORE
    // ============================================================

    private val handler =
        Handler(Looper.getMainLooper())

    private var speechRecognizer:
        SpeechRecognizer? = null

    private lateinit var tts:
        TextToSpeech

    private var ttsReady =
        false

    private var serviceActive =
        false

    private var isSpeaking =
        false

    private var waitingForCommand =
        false

    private var speakThenListenForCommand =
        false

    private var recognizerStarting =
        false

    private var recognitionRunning =
        false

    // ============================================================
    // OPEN WAKE WORD
    // ============================================================

    private var wakeWordEngine:
        JarvisWakeWordEngine? = null

    private var wakeWordRestartRunnable:
        Runnable? = null

    // ============================================================
    // WAKE LOCK
    // ============================================================

    private var wakeLock:
        PowerManager.WakeLock? = null

    // ============================================================
    // COMMAND TIMEOUT
    // ============================================================

    private var commandTimeoutRunnable:
        Runnable? = null

    // ============================================================
    // COMMAND EXECUTOR
    // ============================================================

    private lateinit var commandExecutor:
        CommandExecutor

    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate() {

        super.onCreate()

        serviceActive = true

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification(
                "Jarvis starting..."
            )
        )

        acquireWakeLock()

        // ========================================================
        // TTS
        // ========================================================

        tts =
            TextToSpeech(
                applicationContext,
                this
            )

        // ========================================================
        // COMMAND EXECUTOR
        // ========================================================

        commandExecutor =
            CommandExecutor(
                applicationContext,

                // SPEAK CALLBACK
                { text ->

                    handler.post {

                        if (serviceActive) {
                            speak(text)
                        }
                    }
                },

                // FINISHED CALLBACK
                {
                    handler.post {

                        if (
                            serviceActive &&
                            !isSpeaking &&
                            !waitingForCommand
                        ) {

                            scheduleWakeWordRestart(
                                WAKE_RESTART_DELAY
                            )
                        }
                    }
                }
            )

        // ========================================================
        // SPEECH RECOGNIZER
        // ========================================================

        createSpeechRecognizer()

        // ========================================================
        // OPEN WAKE WORD
        // ========================================================

        wakeWordEngine =
            JarvisWakeWordEngine(
                applicationContext
            ) {

                handler.post {

                    if (serviceActive) {
                        onWakeWordDetected()
                    }
                }
            }

        // ========================================================
        // START WAKE WORD
        // ========================================================

        handler.postDelayed(
            {

                if (serviceActive) {
                    startWakeWordDetection()
                }

            },
            START_DELAY
        )
    }

    // ============================================================
    // TTS INITIALIZATION
    // ============================================================

    override fun onInit(
        status: Int
    ) {

        if (
            status !=
            TextToSpeech.SUCCESS
        ) {
            return
        }

        ttsReady = true

        try {

            val result =
                tts.setLanguage(
                    Locale("en", "IN")
                )

            if (
                result ==
                TextToSpeech.LANG_MISSING_DATA ||
                result ==
                TextToSpeech.LANG_NOT_SUPPORTED
            ) {

                tts.setLanguage(
                    Locale.US
                )
            }

            tts.setSpeechRate(
                1.0f
            )

            tts.setPitch(
                1.0f
            )

        } catch (_: Exception) {
        }

        try {

            tts.setOnUtteranceProgressListener(
                object :
                    UtteranceProgressListener() {

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

                            if (!serviceActive) {
                                return@post
                            }

                            // "Hey Jarvis"
                            // -> "Ji, bolo"
                            // -> command listening
                            if (
                                speakThenListenForCommand
                            ) {

                                speakThenListenForCommand =
                                    false

                                handler.postDelayed(
                                    {

                                        if (
                                            serviceActive &&
                                            !isSpeaking &&
                                            waitingForCommand
                                        ) {

                                            startCommandListening()
                                        }

                                    },
                                    150L
                                )

                            } else {

                                scheduleWakeWordRestart(
                                    AFTER_SPEAK_DELAY
                                )
                            }
                        }
                    }

                    override fun onError(
                        utteranceId: String?
                    ) {

                        handler.post {

                            isSpeaking = false

                            if (!serviceActive) {
                                return@post
                            }

                            if (
                                speakThenListenForCommand
                            ) {

                                speakThenListenForCommand =
                                    false

                                waitingForCommand = true

                                startCommandListening()

                            } else {

                                scheduleWakeWordRestart(
                                    AFTER_SPEAK_DELAY
                                )
                            }
                        }
                    }
                }
            )

        } catch (_: Exception) {
        }
    }

    // ============================================================
    // START OPENWAKEWORD
    // ============================================================

    private fun startWakeWordDetection() {

        if (!serviceActive) {
            return
        }

        if (isSpeaking) {
            return
        }

        if (waitingForCommand) {
            return
        }

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            updateNotification(
                "Microphone permission required"
            )

            return
        }

        cancelRecognition()

        updateNotification(
            "READY • Say Hey Jarvis"
        )

        try {

            wakeWordEngine?.restart()

        } catch (_: Exception) {

            updateNotification(
                "Wake word error"
            )

            handler.postDelayed(
                {

                    if (serviceActive) {
                        startWakeWordDetection()
                    }

                },
                1000L
            )
        }
    }

    // ============================================================
    // WAKE WORD DETECTED
    // ============================================================

    private fun onWakeWordDetected() {

        if (!serviceActive) {
            return
        }

        if (isSpeaking) {
            return
        }

        if (waitingForCommand) {
            return
        }

        // Stop OpenWakeWord before SpeechRecognizer.
        try {
            wakeWordEngine?.stop()
        } catch (_: Exception) {
        }

        cancelWakeWordRestart()

        cancelRecognition()

        waitingForCommand = true

        speakThenListenForCommand = true

        updateNotification(
            "WAKE • Hey Jarvis"
        )

        speak(
            "Ji, bolo."
        )

        startCommandTimeout()
    }

    // ============================================================
    // CREATE SPEECH RECOGNIZER
    // ============================================================

    private fun createSpeechRecognizer() {

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                applicationContext
            )
        ) {

            updateNotification(
                "Speech recognition unavailable"
            )

            return
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        try {

            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(
                    applicationContext
                )

            speechRecognizer?.setRecognitionListener(
                recognitionListener
            )

        } catch (_: Exception) {

            speechRecognizer = null
        }
    }

    // ============================================================
    // RECOGNITION LISTENER
    // ============================================================

    private val recognitionListener =
        object : RecognitionListener {

            override fun onReadyForSpeech(
                params: android.os.Bundle?
            ) {

                recognizerStarting = false
                recognitionRunning = true

                updateNotification(
                    "LISTENING • Command"
                )
            }

            override fun onBeginningOfSpeech() {

               
