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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class JarvisListenerService : Service(),
    TextToSpeech.OnInitListener {

    companion object {

        private const val CHANNEL_ID =
            "jarvis_voice_service"

        private const val NOTIFICATION_ID =
            1001

        private const val RESTART_DELAY =
            350L

        private const val COMMAND_TIMEOUT =
            6000L

        private const val AFTER_SPEAK_DELAY =
            350L

        private const val WAKE_LISTEN_DELAY =
            150L

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
    // RECOGNIZER MODE
    // ============================================================

    private enum class ListenMode {
        WAKE,
        COMMAND
    }

    private var listenMode =
        ListenMode.WAKE

    // ============================================================
    // START SERVICE
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

        // --------------------------------------------------------
        // TTS
        // --------------------------------------------------------

        tts = TextToSpeech(
            applicationContext,
            this
        )

        // --------------------------------------------------------
        // COMMAND EXECUTOR
        // --------------------------------------------------------

        commandExecutor =
            CommandExecutor(
                applicationContext
            ) { text ->

                handler.post {

                    if (serviceActive) {
                        speak(text)
                    }
                }
            }

        // --------------------------------------------------------
        // SPEECH RECOGNIZER
        // --------------------------------------------------------

        createSpeechRecognizer()

        // --------------------------------------------------------
        // START WAKE LISTENING
        // --------------------------------------------------------

        handler.postDelayed(
            {
                if (serviceActive) {
                    startWakeListening()
                }
            },
            700L
        )
    }

    // ============================================================
    // TTS INITIALIZATION
    // ============================================================

    override fun onInit(status: Int) {

        if (status ==
            TextToSpeech.SUCCESS
        ) {

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
                        android.speech.tts.UtteranceProgressListener() {

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

                                if (
                                    speakThenListenForCommand
                                ) {

                                    speakThenListenForCommand =
                                        false

                                    handler.postDelayed(
                                        {
                                            if (
                                                serviceActive &&
                                                !isSpeaking
                                            ) {
                                                startCommandListening()
                                            }
                                        },
                                        150L
                                    )

                                } else {

                                    scheduleWakeListening(
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

                                    startCommandListening()

                                } else {

                                    scheduleWakeListening(
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

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(
                applicationContext
            )

        speechRecognizer?.setRecognitionListener(
            recognitionListener
        )
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
                    if (
                        listenMode ==
                        ListenMode.WAKE
                    ) {
                        "LISTENING • Say Hey Jarvis"
                    } else {
                        "LISTENING • Command"
                    }
                )
            }

            override fun onBeginningOfSpeech() {

                updateNotification(
                    if (
                        listenMode ==
                        ListenMode.WAKE
                    ) {
                        "HEARING • Wake word"
                    } else {
                        "HEARING • Command"
                    }
                )
            }

            override fun onRmsChanged(
                rmsdB: Float
            ) {
                // Intentionally empty.
            }

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {
                // Intentionally empty.
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

                // --------------------------------------------
                // USER DID NOT SAY ANYTHING
                // --------------------------------------------

                if (
                    listenMode ==
                    ListenMode.COMMAND &&
                    waitingForCommand
                ) {

                    waitingForCommand = false

                    scheduleWakeListening(
                        250L
                    )

                    return
                }

                // --------------------------------------------
                // NORMAL WAKE RESTART
                // --------------------------------------------

                scheduleWakeListening(
                    RESTART_DELAY
                )
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

                if (
                    matches == null ||
                    matches.isEmpty()
                ) {

                    scheduleNextListening()

                    return
                }

                // SpeechRecognizer usually returns the
                // best result first.
                val heard =
                    matches.firstOrNull()
                        ?.trim()
                        ?: ""

                if (heard.isBlank()) {

                    scheduleNextListening()

                    return
                }

                handleRecognizedSpeech(
                    heard
                )
            }

            override fun onPartialResults(
                partialResults: android.os.Bundle?
            ) {
                // We intentionally wait for final result.
            }

            override fun onEvent(
                eventType: Int,
                params: android.os.Bundle?
            ) {
                // Intentionally empty.
            }
        }

    // ============================================================
    // HANDLE RECOGNIZED SPEECH
    // ============================================================

    private fun handleRecognizedSpeech(
        heardText: String
    ) {

        if (!serviceActive) {
            return
        }

        val heard =
            heardText.trim()

        if (heard.isBlank()) {

            scheduleNextListening()

            return
        }

        // ========================================================
        // COMMAND MODE
        // ========================================================

        if (waitingForCommand) {

            waitingForCommand = false

            commandTimeoutRunnable?.let {
                handler.removeCallbacks(it)
            }

            commandTimeoutRunnable = null

            cancelRecognition()

            val command =
                cleanCommand(heard)

            if (command.isBlank()) {

                speakThenListenForCommand =
                    true

                speak(
                    "Ji, bolo."
                )

                return
            }

            updateNotification(
                "COMMAND • $command"
            )

            executeCommand(
                command
            )

            return
        }

        // ========================================================
        // WAKE MODE
        // ========================================================

        val afterWake =
            WakeWordDetector.stripWakeWord(
                heard
            )

        // --------------------------------------------------------
        // NO WAKE WORD
        // --------------------------------------------------------

        if (afterWake == null) {

            updateNotification(
                "IGNORED • Waiting for Hey Jarvis"
            )

            scheduleWakeListening(
                RESTART_DELAY
            )

            return
        }

        // Wake word found.
        cancelRecognition()

        val command =
            cleanCommand(afterWake)

        updateNotification(
            "WAKE WORD DETECTED"
        )

        // ========================================================
        // ONLY "HEY JARVIS"
        // ========================================================

        if (command.isBlank()) {

            waitingForCommand = false

            speakThenListenForCommand =
                true

            speak(
                "Ji, bolo."
            )

            startCommandTimeout()

            return
        }

        // ========================================================
        // "HEY JARVIS + COMMAND"
        // ========================================================

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

        } catch (e: Exception) {

            updateNotification(
                "Command error"
            )

            speak(
                "Command execute nahi ho paya."
            )
        }
    }

    // ============================================================
    // START WAKE LISTENING
    // ============================================================

    private fun startWakeListening() {

        if (!serviceActive) {
            return
        }

        if (isSpeaking) {
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

        listenMode =
            ListenMode.WAKE

        waitingForCommand = false

        speakThenListenForCommand =
            false

        startRecognition(
            isCommandMode = false
        )
    }

    // ============================================================
    // START COMMAND LISTENING
    // ============================================================

    private fun startCommandListening() {

        if (!serviceActive) {
            return
        }

        if (isSpeaking) {
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

        listenMode =
            ListenMode.COMMAND

        waitingForCommand = true

        startRecognition(
            isCommandMode = true
        )

        startCommandTimeout()
    }

    // ============================================================
    // START RECOGNITION
    // ============================================================

    private fun startRecognition(
        isCommandMode: Boolean
    ) {

        if (!serviceActive) {
            return
        }

        if (isSpeaking) {
            return
        }

        if (recognizerStarting) {
            return
        }

        if (recognitionRunning) {
            return
        }

        val recognizer =
            speechRecognizer
                ?: run {

                    createSpeechRecognizer()

                    speechRecognizer
                }
                ?: return

        recognizerStarting =
            true

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
                    "en-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "en-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,
                    false
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
                    if (isCommandMode) {
                        900L
                    } else {
                        700L
                    }
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    if (isCommandMode) {
                        500L
                    } else {
                        500L
                    }
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    if (isCommandMode) {
                        650L
                    } else {
                        600L
                    }
                )
            }

        try {

            recognizer.startListening(
                intent
            )

        } catch (_: Exception) {

            recognizerStarting =
                false

            recognitionRunning =
                false

            scheduleWakeListening(
                500L
            )
        }
    }

    // ============================================================
    // SCHEDULE NEXT LISTENING
    // ============================================================

    private fun scheduleNextListening() {

        if (!serviceActive) {
            return
        }

        if (isSpeaking) {
            return
        }

        if (
            listenMode ==
            ListenMode.COMMAND &&
            waitingForCommand
        ) {

            startCommandListening()

        } else {

            scheduleWakeListening(
                RESTART_DELAY
            )
        }
    }

    // ============================================================
    // SCHEDULE WAKE LISTENING
    // ============================================================

    private fun scheduleWakeListening(
        delay: Long
    ) {

        if (!serviceActive) {
            return
        }

        handler.postDelayed(
            {

                if (
                    serviceActive &&
                    !isSpeaking &&
                    !waitingForCommand
                ) {

                    startWakeListening()
                }

            },
            delay
        )
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

                    waitingForCommand =
                        false

                    speakThenListenForCommand =
                        false

                    cancelRecognition()

                    scheduleWakeListening(
                        250L
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

            scheduleWakeListening(
                200L
            )

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

                startCommandListening()

            } else {

                scheduleWakeListening(
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

                startCommandListening()

            } else {

                scheduleWakeListening(
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

        recognitionRunning =
            false

        recognizerStarting =
            false
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
    // SERVICE BIND
    // ============================================================

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    // ============================================================
    // START COMMAND
    // ============================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        serviceActive = true

        if (
            speechRecognizer == null
        ) {
            createSpeechRecognizer()
        }

        return START_STICKY
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        serviceActive = false

        waitingForCommand =
            false

        speakThenListenForCommand =
            false

        commandTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }

        commandTimeoutRunnable = null

        handler.removeCallbacksAndMessages(
            null
        )

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

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
