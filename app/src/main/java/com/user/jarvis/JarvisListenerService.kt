package com.user.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
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
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.UUID

class JarvisListenerService : Service(), TextToSpeech.OnInitListener {

companion object {
    const val CHANNEL_ID = "jarvis_listener_channel"
    const val NOTIFICATION_ID = 1
    const val ACTION_STOP = "com.user.jarvis.STOP"

    private const val RESTART_DELAY_MS = 700L
    private const val AFTER_SPEAK_DELAY_MS = 700L
    private const val COMMAND_TIMEOUT_MS = 8000L
    private const val WAKE_LISTEN_TIMEOUT_MS = 5000L
}

private lateinit var speechRecognizer: SpeechRecognizer
private lateinit var tts: TextToSpeech
private lateinit var commandExecutor: CommandExecutor

private val handler = Handler(Looper.getMainLooper())

private var wakeLock: PowerManager.WakeLock? = null
private var overlayView: View? = null

@Volatile
private var serviceActive = false

@Volatile
private var isListening = false

@Volatile
private var isSpeaking = false

@Volatile
private var waitingForCommand = false

private var ttsReady = false
private var restartScheduled = false

private var commandTimeoutRunnable: Runnable? = null

override fun onCreate() {
    super.onCreate()

    createNotificationChannel()

    tts = TextToSpeech(this, this)

    speechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(this)

    speechRecognizer.setRecognitionListener(recognitionListener)

    commandExecutor = CommandExecutor(this) { response ->
        speak(response)
    }
}

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

    addOverlayIfPermitted()

    serviceActive = true

    updateNotification("READY • Bolo: Hey Jarvis")

    startWakeListening()

    return START_STICKY
}

// ---------------------------------------------------------
// FOREGROUND SERVICE
// ---------------------------------------------------------

private fun startJarvisForeground() {

    val notification =
        buildNotification("READY • Bolo: Hey Jarvis")

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

private fun acquireWakeLock() {

    val powerManager =
        getSystemService(Context.POWER_SERVICE) as PowerManager

    if (wakeLock?.isHeld == true) return

    wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "Jarvis::WakeWordWakeLock"
    )

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
            tts.setLanguage(Locale("hi", "IN"))

        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {

            tts.language = Locale.US
        }

        VoicePreferences.applySavedVoice(this, tts)

        tts.setPitch(0.85f)
        tts.setSpeechRate(0.95f)

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {

                    isSpeaking = true

                    updateNotification(
                        "SPEAKING..."
                    )
                }

                override fun onDone(utteranceId: String?) {

                    isSpeaking = false

                    if (!serviceActive) return

                    handler.postDelayed(
                        {
                            startWakeListening()
                        },
                        AFTER_SPEAK_DELAY_MS
                    )
                }

                override fun onError(utteranceId: String?) {

                    isSpeaking = false

                    if (!serviceActive) return

                    handler.postDelayed(
                        {
                            startWakeListening()
                        },
                        AFTER_SPEAK_DELAY_MS
                    )
                }
            }
        )

    } catch (_: Exception) {
    }
}

private fun speak(text: String) {

    if (!serviceActive || !ttsReady) return

    val cleanText =
        text.replace(
            Regex("\\*\\*(.*?)\\*\\*"),
            "$1"
        ).trim()

    if (cleanText.isEmpty()) return

    cancelRecognition()

    waitingForCommand = false

    isSpeaking = true

    updateNotification(
        "SPEAKING • $cleanText"
    )

    try {

        SciFiTone.play()

        val utteranceId =
            UUID.randomUUID().toString()

        tts.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

    } catch (_: Exception) {

        isSpeaking = false

        scheduleWakeListening()
    }
}

// ---------------------------------------------------------
// LISTENING
// ---------------------------------------------------------

/**
 * Current architecture uses SpeechRecognizer for wake-word
 * detection.
 *
 * IMPORTANT:
 * For true Google-Assistant-style always-on wake word,
 * replace this layer with a local wake-word engine such
 * as openWakeWord.
 */
private fun startWakeListening() {

    if (!serviceActive) return
    if (isSpeaking) return
    if (isListening) return

    waitingForCommand = false

    restartScheduled = false

    startRecognition(
        wakeMode = true
    )
}

private fun startCommandListening() {

    if (!serviceActive) return
    if (isSpeaking) return

    waitingForCommand = true

    startRecognition(
        wakeMode = false
    )

    commandTimeoutRunnable?.let {
        handler.removeCallbacks(it)
    }

    commandTimeoutRunnable =
        Runnable {

            if (waitingForCommand) {

                waitingForCommand = false

                cancelRecognition()

                updateNotification(
                    "READY • Bolo: Hey Jarvis"
                )

                scheduleWakeListening()
            }
        }

    handler.postDelayed(
        commandTimeoutRunnable!!,
        COMMAND_TIMEOUT_MS
    )
}

private fun startRecognition(
    wakeMode: Boolean
) {

    if (!serviceActive) return
    if (isSpeaking) return
    if (isListening) return

    try {

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                /*
                 * en-IN generally works better for Hinglish
                 * commands such as:
                 *
                 * "Jarvis Instagram kholo"
                 * "Jarvis flashlight on karo"
                 */
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "en-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "en-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
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

                    /*
                     * Shorter listening window.
                     * The recognizer will periodically restart.
                     */
                    putExtra(
                        RecognizerIntent
                            .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1000L
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1000L
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                        1500L
                    )

                } else {

                    /*
                     * Command mode gets a little more time.
                     */
                    putExtra(
                        RecognizerIntent
                            .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1200L
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1200L
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                        1000L
                    )
                }
            }

        isListening = true

        updateNotification(
            if (wakeMode)
                "READY • Listening for Hey Jarvis"
            else
                "LISTENING • Bolo..."
        )

        speechRecognizer.startListening(intent)

    } catch (e: Exception) {

        isListening = false

        updateNotification(
            "Microphone restart..."
        )

        scheduleWakeListening()
    }
}

// ---------------------------------------------------------
// SPEECH RECOGNITION CALLBACK
// ---------------------------------------------------------

private val recognitionListener =
    object : RecognitionListener {

        override fun onReadyForSpeech(
            params: Bundle?
        ) {
            isListening = true
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
            /*
             * Can later be connected to UI waveform.
             */
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

            handleRecognizedSpeech(heard)
        }

        override fun onPartialResults(
            partialResults: Bundle?
        ) {
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

            if (!serviceActive) return

            /*
             * Don't spam the notification for normal
             * speech timeout/no-match errors.
             */
            when (error) {

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {

                    if (waitingForCommand) {

                        waitingForCommand = false

                        updateNotification(
                            "READY • Bolo: Hey Jarvis"
                        )
                    }

                    scheduleWakeListening()
                }

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {

                    cancelRecognition()

                    handler.postDelayed(
                        {
                            startWakeListening()
                        },
                        1200L
                    )
                }

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {

                    waitingForCommand = false

                    updateNotification(
                        "Microphone permission required"
                    )

                    scheduleWakeListening()
                }

                SpeechRecognizer.ERROR_AUDIO -> {

                    cancelRecognition()

                    handler.postDelayed(
                        {
                            startWakeListening()
                        },
                        1500L
                    )
                }

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {

                    updateNotification(
                        "Network issue • Retrying..."
                    )

                    scheduleWakeListening()
                }

                else -> {

                    scheduleWakeListening()
                }
            }
        }
    }

// ---------------------------------------------------------
// SPEECH PROCESSING
// ---------------------------------------------------------

private fun handleRecognizedSpeech(
    heard: String
) {

    /*
     * COMMAND MODE
     */
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

        try {

            commandExecutor.execute(command)

        } catch (_: Exception) {

            speak(
                "Sorry, command execute nahi ho paya."
            )
        }

        return
    }

    /*
     * WAKE-WORD MODE
     */

    val commandAfterWakeWord =
        WakeWordDetector.stripWakeWord(
            heard
        )

    if (commandAfterWakeWord != null) {

        cancelRecognition()

        val command =
            cleanCommand(
                commandAfterWakeWord
            )

        updateNotification(
            "WAKE WORD DETECTED"
        )

        if (command.isBlank()) {

            /*
             * User said only:
             *
             * Hey Jarvis
             *
             * Now listen for the actual command.
             */
            startCommandListening()

        } else {

            /*
             * User said:
             *
             * Hey Jarvis flashlight on karo
             */
            updateNotification(
                "COMMAND • $command"
            )

            commandExecutor.execute(command)
        }

    } else {

        /*
         * Random speech/background audio.
         *
         * Ignore it and continue wake listening.
         */
        scheduleWakeListening()
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

        updateNotification(
            "READY • Bolo: Hey Jarvis"
        )
    }

    scheduleWakeListening()
}

// ---------------------------------------------------------
// RESTART CONTROL
// ---------------------------------------------------------

private fun scheduleWakeListening() {

    if (!serviceActive) return
    if (isSpeaking) return
    if (restartScheduled) return

    restartScheduled = true

    handler.postDelayed(
        {

            restartScheduled = false

            if (serviceActive &&
                !isSpeaking &&
                !isListening
            ) {

                startWakeListening()
            }

        },
        RESTART_DELAY_MS
    )
}

private fun cancelRecognition() {

    try {
        speechRecognizer.cancel()
    } catch (_: Exception) {
    }

    isListening = false
}

// ---------------------------------------------------------
// OVERLAY
// ---------------------------------------------------------

private fun addOverlayIfPermitted() {

    if (overlayView != null) return

    try {

        val windowManager =
            getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        val view = View(this)

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

            } else {

                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params =
            WindowManager.LayoutParams(
                1,
                1,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            )

        windowManager.addView(
            view,
            params
        )

        overlayView = view

    } catch (_: Exception) {
    }
}

private fun removeOverlay() {

    val view = overlayView ?: return

    try {

        val windowManager =
            getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        windowManager.removeView(view)

    } catch (_: Exception) {
    }

    overlayView = null
}

// ---------------------------------------------------------
// NOTIFICATION
// ---------------------------------------------------------

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
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

    return NotificationCompat.Builder(
        this,
        CHANNEL_ID
    )
        .setContentTitle(
            "Jarvis"
        )
        .setContentText(
            content
        )
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
            "Band karo",
            stopPendingIntent
        )
        .build()
}

private fun updateNotification(
    content: String
) {

    try {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager?.notify(
            NOTIFICATION_ID,
            buildNotification(content)
        )

    } catch (_: Exception) {
    }
}

private fun createNotificationChannel() {

    if (Build.VERSION.SDK_INT <
        Build.VERSION_CODES.O
    ) return

    val channel =
        NotificationChannel(
            CHANNEL_ID,
            "Jarvis Listener",
            NotificationManager.IMPORTANCE_LOW
        ).apply {

            description =
                "Jarvis background assistant status"

            setShowBadge(false)
        }

    getSystemService(
        NotificationManager::class.java
    )?.createNotificationChannel(channel)
}

// ---------------------------------------------------------
// STOP / DESTROY
// ---------------------------------------------------------

private fun stopListeningAndSelf() {

    serviceActive = false
    waitingForCommand = false
    isSpeaking = false

    handler.removeCallbacksAndMessages(null)

    commandTimeoutRunnable?.let {
        handler.removeCallbacks(it)
    }

    cancelRecognition()

    removeOverlay()

    try {
        tts.stop()
    } catch (_: Exception) {
    }

    wakeLock?.let {

        if (it.isHeld) {
            it.release()
        }
    }

    wakeLock = null

    if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.N
    ) {

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

    } else {

        @Suppress("DEPRECATION")
        stopForeground(true)
    }

    stopSelf()
}

override fun onDestroy() {

    serviceActive = false

    handler.removeCallbacksAndMessages(null)

    cancelRecognition()

    removeOverlay()

    wakeLock?.let {

        if (it.isHeld) {
            it.release()
        }
    }

    wakeLock = null

    try {
        speechRecognizer.destroy()
    } catch (_: Exception) {
    }

    try {
        tts.stop()
        tts.shutdown()
    } catch (_: Exception) {
    }

    super.onDestroy()
}

override fun onBind(
    intent: Intent?
): IBinder? = null

}
