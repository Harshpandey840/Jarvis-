package com.user.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
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
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlayView: View? = null

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

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::ListenerWakeLock")
        wakeLock?.acquire(10 * 60 * 60 * 1000L)
        addOverlayIfPermitted()
        isServiceActive = true
        startListeningCycle()
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            VoicePreferences.applySavedVoice(this, tts)
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
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

    private fun addOverlayIfPermitted() {
        if (!Settings.canDrawOverlays(this) || overlayView != null) return
        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = View(this)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                1, 1, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(view, params)
            overlayView = view
        } catch (e: Exception) { }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (e: Exception) { }
        overlayView = null
    }
}
