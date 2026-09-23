package com.user.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
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
import java.util.UUID

class JarvisListenerService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "jarvis_listener_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.user.jarvis.STOP"

        private const val RESTART_DELAY = 350L
        private const val COMMAND_TIMEOUT = 5500L
        private const val AFTER_SPEAK_DELAY = 350L
    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var commandExecutor: CommandExecutor

    private val handler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var serviceActive = false

    @Volatile
    private var isListening = false

    @Volatile
    private var isSpeaking = false

    @Volatile
    private var waitingForCommand = false

    private var ttsReady = false
    private var recognizerStarting = false

    private var commandTimeoutRunnable: Runnable? = null
    private var restartRunnable: Runnable? = null

    // ---------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        tts = TextToSpeech(this, this)

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer.setRecognitionListener(
            recognitionListener
        )

        commandExecutor = CommandExecutor(this) { response ->
            speak(response)
        }
    }

    // ---------------------------------------------------------
    // START
    // ---------------------------------------------------------

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopListeningAndSelf()
            return START_NOT_STICKY
        }

        startJarvisForeground()

        acquireWakeLock()

        serviceActive = true

        updateNotification(
            "READY • Hey Jarvis ke liye ready"
        )

        scheduleWakeListening(100)

        return START_STICKY
    }

    // ---------------------------------------------------------
    // FOREGROUND SERVICE
    // ---------------------------------------------------------

    private fun startJarvisForeground() {

        val notification =
            buildNotification(
                "READY • Hey Jarvis ke liye ready"
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    // ---------------------------------------------------------
    // WAKE LOCK
    // ---------------------------------------------------------

    private fun acquireWakeLock() {

        val powerManager =
            getSystemService(
                Context.POWER_SERVICE
            ) as PowerManager

        if (wakeLock?.isHeld == true) return

        wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Jarvis::VoiceListener"
            )

        wakeLock?.setReferenceCounted(false)

        wakeLock?.acquire()
    }

    // ---------------------------------------------------------
    // TTS
    // ---------------------------------------------------------

    override fun onInit(status: Int) {

        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            return
        }

        ttsReady = true

        try {

            val result =
                tts.setLanguage(
                    Locale("hi", "IN")
                )

            if (
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts.language = Locale.US
            }

            VoicePreferences.applySavedVoice(
                this,
                tts
            )

            tts.setPitch(0.85f)

            // Slightly faster than old 0.95
            tts.setSpeechRate(1.0f)

            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(
                        utteranceId: String?
                    ) {
                        isSpeaking = true

                        updateNotification(
                            "SPEAKING..."
                        )
                    }

                    override fun onDone(
                        utteranceId: String?
                    ) {

                        isSpeaking = false

                        if (!serviceActive) return

                        scheduleWakeListening(
                            AFTER_SPEAK_DELAY
                        )
                    }

                    override fun onError(
                        utteranceId: String?
                    ) {

                        isSpeaking = false

                        if (!serviceActive) return

                        scheduleWakeListening(
                            AFTER_SPEAK_DELAY
                        )
                    }
                }
            )

        } catch (_: Exception) {
            ttsReady = false
        }
    }

    private fun speak(text: String) {

        if (!serviceActive) return

        val cleanText =
            text
                .replace(
                    Regex("\\*\\*(.*?)\\*\\*"),
                    "$1"
                )
                .trim()

        if (cleanText.isEmpty()) {
            scheduleWakeListening(100)
            return
        }

        cancelRecognition()

        waitingForCommand = false

        if (!ttsReady) {
            scheduleWakeListening(100)
            return
        }

        isSpeaking = true

        updateNotification(
            "SPEAKING • $cleanText"
        )

        try {

            SciFiTone.play()

            val id =
                UUID.randomUUID().toString()

            tts.speak(
                cleanText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                id
            )

        } catch (_: Exception) {

            isSpeaking = false

            scheduleWakeListening(100)
        }
    }

    // ---------------------------------------------------------
    // WAKE LISTENING
    // ---------------------------------------------------------

    private fun startWakeListening() {

        if (!serviceActive) return
        if (isSpeaking) return
        if (waitingForCommand) return
        if (isListening) return
        if (recognizerStarting) return

        startRecognition(
            wakeMode = true
        )
    }

    // ---------------------------------------------------------
    // COMMAND LISTENING
    // ---------------------------------------------------------

    private fun startCommandListening() {

        if (!serviceActive) return
        if (isSpeaking) return
        if (isListening) return
        if (recognizerStarting) return

        waitingForCommand = true

        updateNotification(
            "LISTENING • Bolo..."
        )

        startRecognition(
            wakeMode = false
        )

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

                    cancelRecognition()

                    updateNotification(
                        "READY • Hey Jarvis"
                    )

                    scheduleWakeListening(100)
                }
            }

        handler.postDelayed(
            commandTimeoutRunnable!!,
            COMMAND_TIMEOUT
        )
    }

    // ---------------------------------------------------------
    // SPEECH RECOGNITION
    // ---------------------------------------------------------

    private fun startRecognition(
        wakeMode: Boolean
    ) {

        if (!serviceActive) return
        if (isSpeaking) return
        if (isListening) return
        if (recognizerStarting) return

        recognizerStarting = true

        try {

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

                    /*
                     * Partial results enabled.
                     *
                     * This helps command mode react
                     * sooner than waiting unnecessarily
                     * for a long silence.
                     */
                    putExtra(
                        RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                        true
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_MAX_RESULTS,
                        5
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_CALLING_PACKAGE,
                        packageName
                    )

                    if (wakeMode) {

                        putExtra(
                            RecognizerIntent
                                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                            700L
                        )

                        putExtra(
                            RecognizerIntent
                                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                            700L
                        )

                        putExtra(
                            RecognizerIntent
                                .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                            700L
                        )

                    } else {

                        putExtra(
                            RecognizerIntent
                                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                            650L
                        )

                        putExtra(
                            RecognizerIntent
                                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                            650L
                        )

                        putExtra(
                            RecognizerIntent
                                .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                            500L
                        )
                    }
                }

            speechRecognizer.startListening(intent)

            isListening = true
            recognizerStarting = false

            updateNotification(
                if (wakeMode) {
                    "READY • Listening..."
                } else {
                    "LISTENING • Bolo..."
                }
            )

        } catch (_: Exception) {

            recognizerStarting = false
            isListening = false

            scheduleWakeListening(
                800L
            )
        }
    }

    // ---------------------------------------------------------
    // RECOGNITION LISTENER
    // ---------------------------------------------------------

    private val recognitionListener =
        object : RecognitionListener {

            override fun onReadyForSpeech(
                params: Bundle?
            ) {
                isListening = true
                recognizerStarting = false
            }

            override fun onBeginningOfSpeech() {

                if (waitingForCommand) {

                    updateNotification(
                        "LISTENING • Sun raha hoon..."
                    )
                }
            }

            override fun onRmsChanged(
                rmsdB: Float
            ) {
                // Reserved for future waveform UI.
            }

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {
            }

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onResults(
                results: Bundle?
            ) {

                isListening = false
                recognizerStarting = false

                val matches =
                    results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                val heard =
                    matches
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                if (heard.isBlank()) {

                    handleEmptyResult()

                    return
                }

                handleRecognizedSpeech(
                    heard
                )
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {

                /*
                 * Partial results are intentionally
                 * not executed directly.
                 *
                 * We wait for final result so that
                 * "open YouTube" is not accidentally
                 * executed as just "open".
                 */
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
            }

            override fun onError(
                error: Int
            ) {

                isListening = false
                recognizerStarting = false

                if (!serviceActive) return

                when (error) {

                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {

                        if (waitingForCommand) {

                            waitingForCommand = false

                            commandTimeoutRunnable?.let {
                                handler.removeCallbacks(it)
                            }

                            updateNotification(
                                "READY • Hey Jarvis"
                            )
                        }

                        scheduleWakeListening(
                            RESTART_DELAY
                        )
                    }

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {

                        cancelRecognition()

                        scheduleWakeListening(
                            1000L
                        )
                    }

                    SpeechRecognizer.ERROR_AUDIO -> {

                        cancelRecognition()

                        scheduleWakeListening(
                            1000L
                        )
                    }

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {

                        waitingForCommand = false

                        updateNotification(
                            "Microphone permission required"
                        )

                        scheduleWakeListening(
                            2500L
                        )
                    }

                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {

                        /*
                         * Don't repeatedly hammer the
                         * recognizer when provider/network
                         * is unavailable.
                         */
                        scheduleWakeListening(
                            1500L
                        )
                    }

                    else -> {

                        scheduleWakeListening(
                            700L
                        )
                    }
                }
            }
        }

    // ---------------------------------------------------------
    // RECOGNIZED SPEECH
    // ---------------------------------------------------------

    private fun handleRecognizedSpeech(
        heard: String
    ) {

        if (!serviceActive) return

        // -----------------------------------------------------
        // COMMAND MODE
        // -----------------------------------------------------

        if (waitingForCommand) {

            waitingForCommand = false

            commandTimeoutRunnable?.let {
                handler.removeCallbacks(it)
            }

            val command =
                cleanCommand(heard)

            if (command.isBlank()) {

                speak("Ji, bolo.")

                return
            }

            updateNotification(
                "COMMAND • $command"
            )

            cancelRecognition()

            executeCommand(
                command
            )

            return
        }

        // -----------------------------------------------------
        // WAKE MODE
        // -----------------------------------------------------

        val afterWake =
            WakeWordDetector.stripWakeWord(
                heard
            )

        if (afterWake == null) {

            /*
             * Normal background speech.
             *
             * Ignore it.
             */
            scheduleWakeListening(
                RESTART_DELAY
            )

            return
        }

        cancelRecognition()

        val command =
            cleanCommand(afterWake)

        updateNotification(
            "WAKE WORD DETECTED"
        )

        // -----------------------------------------------------
        // ONLY "HEY JARVIS"
        // -----------------------------------------------------

        if (command.isBlank()) {

            startCommandListening()

            return
        }

        // -----------------------------------------------------
        // "HEY JARVIS + COMMAND"
        // -----------------------------------------------------

        updateNotification(
            "COMMAND • $command"
        )

        executeCommand(
            command
        )
    }

    // ---------------------------------------------------------
    // EXECUTE
    // ---------------------------------------------------------

    private fun executeCommand(
        command: String
    ) {

        try {

            /*
             * CommandExecutor itself decides whether
             * the command is local or AI.
             *
             * Local commands such as:
             * YouTube
             * Lock
             * Torch
             * Volume
             *
             * should never need Gemini.
             */
            commandExecutor.execute(
                command
            )

        } catch (_: Exception) {

            speak(
                "Command execute nahi ho paya."
            )
        }
    }

    private fun cleanCommand(
        text: String
    ): String {

        return text
            .trim()
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    private fun handleEmptyResult() {

        if (!serviceActive) return

        if (waitingForCommand) {

            waitingForCommand = false

            commandTimeoutRunnable?.let {
                handler.removeCallbacks(it)
            }

            updateNotification(
                "READY • Hey Jarvis"
            )
        }

        scheduleWakeListening(
            RESTART_DELAY
        )
    }

    // ---------------------------------------------------------
    // SAFE RESTART
    // ---------------------------------------------------------

    private fun scheduleWakeListening(
        delay: Long
    ) {

        if (!serviceActive) return
        if (isSpeaking) return

        restartRunnable?.let {
            handler.removeCallbacks(it)
        }

        restartRunnable =
            Runnable {

                restartRunnable = null

                if (
                    serviceActive &&
                    !isSpeaking &&
                    !isListening &&
                    !waitingForCommand
                ) {
                    startWakeListening()
                }
            }

        handler.postDelayed(
            restartRunnable!!,
            delay
        )
    }

    // ---------------------------------------------------------
    // CANCEL
    // ---------------------------------------------------------

    private fun cancelRecognition() {

        try {
            speechRecognizer.cancel()
        } catch (_: Exception) {
        }

        isListening = false
        recognizerStarting = false
    }

    // ---------------------------------------------------------
    // NOTIFICATION
    // ---------------------------------------------------------

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
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun buildNotification(
        content: String
    ): Notification {

        val stopIntent =
            Intent(
                this,
                JarvisListenerService::class.java
            ).apply {
                action = ACTION_STOP
            }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                100,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("Jarvis")
            .setContentText(content)
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        if (!serviceActive) return

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(text)
        )
    }

    // ---------------------------------------------------------
    // STOP
    // ---------------------------------------------------------

    private fun stopListeningAndSelf() {

        serviceActive = false

        waitingForCommand = false
        isListening = false
        isSpeaking = false

        commandTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }

        restartRunnable?.let {
            handler.removeCallbacks(it)
        }

        commandTimeoutRunnable = null
        restartRunnable = null

        try {
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        } catch (_: Exception) {
        }

        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {
        }

        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }

        wakeLock = null

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    // ---------------------------------------------------------
    // DESTROY
    // ---------------------------------------------------------

    override fun onDestroy() {

        serviceActive = false

        handler.removeCallbacksAndMessages(
            null
        )

        try {
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        } catch (_: Exception) {
        }

        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {
        }

        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }

        wakeLock = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
