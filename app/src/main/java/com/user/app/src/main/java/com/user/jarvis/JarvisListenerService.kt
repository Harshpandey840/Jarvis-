package com.user.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.UUID

/**
 * Runs in the background and listens continuously. Only reacts once it hears
 * the wake word "Jarvis" so it doesn't fire on every random sound.
 */
class JarvisListenerService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "jarvis_listener_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.user.jarvis.STOP"
        private const val RESTART_DELAY_MS = 400L
        private const val RESUME_AFTER_SPEAK_DELAY_MS = 600L
    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var commandExecutor: CommandExecutor
    private val handler = Handler(Looper.getMainLooper())

    private var isSpeaking = false
    private var isServiceActive = false
    private var isAwaitingCommand = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(recognitionListener)
        commandExecutor = CommandExecutor(this) { speak(it) }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListeningAndSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("Sun raha hoon (bolo: Jarvis...)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isServiceActive = true
        startListeningCycle()
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    handler.postDelayed({ startListeningCycle() }, RESUME_AFTER_SPEAK_DELAY_MS)
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    handler.postDelayed({ startListeningCycle() }, RESUME_AFTER_SPEAK_DELAY_MS)
                }
            })
        }
    }

    private fun startListeningCycle() {
        if (!isServiceActive || isSpeaking) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            // Give more pause tolerance so "Jarvis... (pause) ...command" isn't cut short
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
        }
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!isServiceActive) return
        handler.postDelayed({ startListeningCycle() }, RESTART_DELAY_MS)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val heard = matches?.firstOrNull().orEmpty()

            // If we just said "Ji bolo", treat whatever comes next as the
            // command directly — no need to say "Jarvis" again.
            if (isAwaitingCommand) {
                isAwaitingCommand = false
                if (heard.isBlank()) {
                    scheduleRestart()
                } else {
                    updateNotification("Suna: $heard")
                    commandExecutor.execute(heard)
                }
                return
            }

            val commandAfterWakeWord = WakeWordDetector.stripWakeWord(heard)
            if (commandAfterWakeWord != null) {
                updateNotification("Suna: $heard")
                if (commandAfterWakeWord.isBlank()) {
                    isAwaitingCommand = true
                    speak("Ji bolo")
                } else {
                    commandExecutor.execute(commandAfterWakeWord)
                }
            } else {
                // No wake word heard — keep listening silently, don't react.
                scheduleRestart()
            }
        }

        override fun onError(error: Int) {
            // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT happen constantly in always-on
            // mode (silence between sentences) — just restart quietly.
            if (isAwaitingCommand) {
                isAwaitingCommand = false
            }
            scheduleRestart()
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun speak(text: String) {
        updateNotification(text)
        val utteranceId = UUID.randomUUID().toString()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        // onStart/onDone of the utterance listener pause/resume listening
        // so Jarvis doesn't hear itself talking.
    }

    private fun stopListeningAndSelf() {
        isServiceActive = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer.stopListening()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(content: String): Notification {
        val stopIntent = Intent(this, JarvisListenerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis active hai")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Band karo", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Jarvis Listener", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Jarvis background listening status"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        isServiceActive = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
