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

        private const val COMMAND_TIMEOUT =
            6000L

        private const val AFTER_SPEAK_DELAY =
            350L

        private const val WAKE_RESTART_DELAY =
            250L

        private const val START_DELAY =
            1000L

        private const val TAG =
            "JarvisListenerService"
    }

    // ============================================================
    // CORE
    // ============================================================

    private val handler =
        Handler(Looper.getMainLooper())

    private var speechRecognizer:
            SpeechRecognizer? = null

    private lateinit var tts: TextToSpeech

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
    // START
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
        // START
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
    // TTS INIT
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

                            if (!serviceActive) {
                                return@post
                            }

                            // ------------------------------------
                            // "HEY JARVIS" -> "JI BOLO"
                            // -> COMMAND LISTENING
                            // ------------------------------------

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

                                // --------------------------------
                                // NORMAL REPLY FINISHED
                                // --------------------------------

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
    // OPEN WAKE WORD START
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

        } catch (error: Exception) {

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

        // Stop OpenWakeWord BEFORE SpeechRecognizer.
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

                updateNotification(
                    "HEARING • Command"
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

                recognitionRunning = false
            }

            override fun onError(
                error: Int
            ) {

                recognitionRunning = false
                recognizerStarting = false

                if (!serviceActive) {
                    return
                }

                if (waitingForCommand) {

                    waitingForCommand = false

                    commandTimeoutRunnable?.let {
                        handler.removeCallbacks(it)
                    }

                    commandTimeoutRunnable = null

                    scheduleWakeWordRestart(
                        WAKE_RESTART_DELAY
                    )

                } else {

                    scheduleWakeWordRestart(
                        WAKE_RESTART_DELAY
                    )
                }
            }

            override fun onResults(
                results: android.os.Bundle?
            ) {

                recognitionRunning = false
                recognizerStarting = false

                if (!serviceActive) {
                    return
                }

                val matches =
                    results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                val heard =
                    matches
                        ?.firstOrNull()
                        ?.trim()
                        ?: ""

                if (heard.isBlank()) {

                    waitingForCommand = false

                    scheduleWakeWordRestart(
                        WAKE_RESTART_DELAY
                    )

                    return
                }

                handleCommandSpeech(
                    heard
                )
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

    // ============================================================
    // COMMAND SPEECH
    // ============================================================

    private fun handleCommandSpeech(
        heard: String
    ) {

        if (!serviceActive) {
            return
        }

        if (!waitingForCommand) {
            return
        }

        waitingForCommand = false

        commandTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }

        commandTimeoutRunnable = null

        cancelRecognition()

        val command =
            cleanCommand(
                heard
            )

        if (command.isBlank()) {

            waitingForCommand = true

            speakThenListenForCommand = true

            speak(
                "Ji, bolo."
            )

            startCommandTimeout()

            return
        }

        updateNotification(
            "COMMAND • $command"
        )

        executeCommand(
            command
        )
    }

    // ============================================================
    // CLEAN COMMAND
    // ============================================================

    private fun cleanCommand(
        command: String
    ): String {

        return command
            .trim()
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    // ============================================================
    // EXECUTE COMMAND
    // ============================================================

    private fun executeCommand(
        command: String
    ) {

        if (!serviceActive) {
            return
        }

        try {

            commandExecutor.execute(
                command
            )

        } catch (error: Exception) {

            updateNotification(
                "Command error"
            )

            speak(
                "Command execute nahi ho paya."
            )
        }
    }

    // ============================================================
    // COMMAND LISTENING
    // ============================================================

    private fun startCommandListening() {

        if (!serviceActive) {
            return
        }

        if (isSpeaking) {
            return
        }

        if (!waitingForCommand) {
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

        startRecognition()
    }

    // ============================================================
    // START SPEECH RECOGNITION
    // ============================================================

    private fun startRecognition() {

        if (!serviceActive) {
            return
        }

        if (isSpeaking) {
            return
        }

        if (!waitingForCommand) {
            return
        }

        if (recognizerStarting) {
            return
        }

        if (recognitionRunning) {
            return
        }

        if (speechRecognizer == null) {
            createSpeechRecognizer()
        }

        val recognizer =
            speechRecognizer
                ?: return

        recognizerStarting = true

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                // Hinglish / Indian English
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "en-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "en-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    900L
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    400L
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    650L
                )
            }

        try {

            recognizer.startListening(
                intent
            )

        } catch (_: Exception) {

            recognizerStarting = false
            recognitionRunning = false

            scheduleWakeWordRestart(
                700L
            )
        }
    }

    // ============================================================
    // COMMAND TIMEOUT
    // ============================================================

    private fun startCommandTimeout() {

        commandTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }

        commandTimeoutRunnable =
            Runnable {

                if (
                    serviceActive &&
                    waitingForCommand
                ) {

                    waitingForCommand = false

                    speakThenListenForCommand = false

                    cancelRecognition()

                    updateNotification(
                        "READY • Say Hey Jarvis"
                    )

                    scheduleWakeWordRestart(
                        WAKE_RESTART_DELAY
                    )
                }
            }

        handler.postDelayed(
            commandTimeoutRunnable!!,
            COMMAND_TIMEOUT
        )
    }

    // ============================================================
    // SPEAK
    // ============================================================

    private fun speak(
        text: String
    ) {

        if (!serviceActive) {
            return
        }

        val cleanText =
            text
                .replace(
                    Regex("\\*\\*(.*?)\\*\\*"),
                    "$1"
                )
                .trim()

        if (cleanText.isBlank()) {

            if (
                speakThenListenForCommand
            ) {

                speakThenListenForCommand =
                    false

                waitingForCommand = true

                startCommandListening()

            } else {

                scheduleWakeWordRestart(
                    200L
                )
            }

            return
        }

        cancelRecognition()

        if (!ttsReady) {

            isSpeaking = false

            if (
                speakThenListenForCommand
            ) {

                speakThenListenForCommand =
                    false

                waitingForCommand = true

                startCommandListening()

            } else {

                scheduleWakeWordRestart(
                    250L
                )
            }

            return
        }

        isSpeaking = true

        updateNotification(
            "SPEAKING • $cleanText"
        )

        try {

            val utteranceId =
                UUID.randomUUID()
                    .toString()

            tts.speak(
                cleanText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )

        } catch (_: Exception) {

            isSpeaking = false

            if (
                speakThenListenForCommand
            ) {

                speakThenListenForCommand =
                    false

                waitingForCommand = true

                startCommandListening()

            } else {

                scheduleWakeWordRestart(
                    300L
                )
            }
        }
    }

    // ============================================================
    // CANCEL RECOGNITION
    // ============================================================

    private fun cancelRecognition() {

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        recognitionRunning = false
        recognizerStarting = false
    }

    // ============================================================
    // WAKE WORD RESTART
    // ============================================================

    private fun scheduleWakeWordRestart(
        delay: Long
    ) {

        if (!serviceActive) {
            return
        }

        cancelWakeWordRestart()

        wakeWordRestartRunnable =
            Runnable {

                if (
                    serviceActive &&
                    !isSpeaking &&
                    !waitingForCommand
                ) {

                    startWakeWordDetection()
                }
            }

        handler.postDelayed(
            wakeWordRestartRunnable!!,
            delay
        )
    }

    private fun cancelWakeWordRestart() {

        wakeWordRestartRunnable?.let {

            handler.removeCallbacks(
                it
            )
        }

        wakeWordRestartRunnable = null
    }

    // ============================================================
    // WAKE LOCK
    // ============================================================

    private fun acquireWakeLock() {

        try {

            val powerManager =
                getSystemService(
                    POWER_SERVICE
                ) as PowerManager

            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Jarvis::VoiceWakeLock"
                )

            wakeLock?.setReferenceCounted(
                false
            )

            wakeLock?.acquire()

        } catch (_: Exception) {
        }
    }

    // ============================================================
    // NOTIFICATION CHANNEL
    // ============================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Jarvis Voice Assistant",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "Jarvis background voice assistant"

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

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private fun createNotification(
        text: String
    ): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Jarvis"
            )
            .setContentText(
                text
            )
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        if (!serviceActive) {
            return
        }

        try {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.notify(
                NOTIFICATION_ID,
                createNotification(text)
            )

        } catch (_: Exception) {
        }
    }

    // ============================================================
    // SERVICE
    // ============================================================

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        serviceActive = true

        if (speechRecognizer == null) {
            createSpeechRecognizer()
        }

        if (wakeWordEngine == null) {

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
        }

        if (
            !waitingForCommand &&
            !isSpeaking &&
            wakeWordEngine?.isRunning() != true
        ) {

            handler.postDelayed(
                {

                    if (serviceActive) {
                        startWakeWordDetection()
                    }

                },
                300L
            )
        }

        return START_STICKY
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        serviceActive = false

        waitingForCommand = false

        speakThenListenForCommand = false

        isSpeaking = false

        cancelWakeWordRestart()

        commandTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }

        commandTimeoutRunnable = null

        handler.removeCallbacksAndMessages(
            null
        )

        cancelRecognition()

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        try {
            wakeWordEngine?.release()
        } catch (_: Exception) {
        }

        wakeWordEngine = null

        try {
            tts.stop()
        } catch (_: Exception) {
        }

        try {
            tts.shutdown()
        } catch (_: Exception) {
        }

        try {

            wakeLock?.let {

                if (it.isHeld) {
                    it.release()
                }
            }

        } catch (_: Exception) {
        }

        wakeLock = null

        super.onDestroy()
    }
}
