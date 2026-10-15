package com.user.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.media.AudioFocusRequest
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale

enum class AudioState {
    STOPPED,
    STARTING_WAKE_WORD,
    WAITING_FOR_WAKE_WORD,
    HANDING_OFF_TO_COMMAND_RECOGNIZER,
    LISTENING_FOR_COMMAND,
    PROCESSING_COMMAND,
    SPEAKING,
    RECOVERING_FROM_ERROR
}

class JarvisListenerService : Service() {

    companion object {
        const val ACTION_STOP = "com.user.jarvis.ACTION_STOP"
        private const val CHANNEL_ID = "jarvis_listener_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "JarvisListenerService"
        private const val COMMAND_TIMEOUT = 6000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var wakeWordEngine: JarvisWakeWordEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var commandExecutor: CommandExecutor? = null

    private var serviceActive = false
    @Volatile private var currentState: AudioState = AudioState.STOPPED

    private var ttsReady = false
    private var speakThenListenForCommand = false
    private var commandTimeoutRunnable: Runnable? = null
    private var wakeRestartRunnable: Runnable? = null
    private var notificationPingRunnable: Runnable? = null
    private var notificationDotCount = 1
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        try {
            serviceActive = true
            createNotificationChannel()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("Jarvis Online - Starting..."),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("Jarvis Online - Starting...")
                )
            }

            createOverlayWindow()
            acquireWakeLock()

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            }

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
                        if (serviceActive && currentState == AudioState.PROCESSING_COMMAND) {
                            scheduleWakeWordRestart(250L)
                        }
                    }
                }
            )

            createSpeechRecognizer()

            try {
                wakeWordEngine = JarvisWakeWordEngine(applicationContext) {
                    handler.post {
                        onWakeWordDetected()
                    }
                }
                wakeWordEngine?.start()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to initialize Wake Word Engine", e)
            }

            notificationPingRunnable = object : Runnable {
                override fun run() {
                    if (serviceActive && currentState == AudioState.WAITING_FOR_WAKE_WORD) {
                        val dotsStr = ".".repeat(notificationDotCount)
                        updateNotification("Jarvis Online - Listening$dotsStr")
                        notificationDotCount = if (notificationDotCount >= 3) 1 else notificationDotCount + 1
                    }
                    handler.postDelayed(this, 2000L)
                }
            }
            handler.post(notificationPingRunnable!!)

            // Kick off state machine
            transitionToState(AudioState.STARTING_WAKE_WORD)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start JarvisListenerService", e)
            stopSelf()
        }
    }

    private fun transitionToState(newState: AudioState) {
        if (!serviceActive) return
        currentState = newState
        android.util.Log.d(TAG, "Transitioning audio state to: \$newState")

        when (newState) {
            AudioState.STARTING_WAKE_WORD -> {
                updateNotification("Starting Wake Word Engine...")
                cancelRecognition()
                if (wakeWordEngine?.isRunning() == true) {
                    transitionToState(AudioState.WAITING_FOR_WAKE_WORD)
                } else {
                    updateNotification("Wake Word Engine unavailable")
                    updateHudState("STANDBY", JarvisCoreView.State.IDLE)
                    // Explicitly reporting the issue instead of getting stuck
                    android.util.Log.e(TAG, "Wake word engine is not running, likely due to missing valid model file.")
                }
            }
            AudioState.WAITING_FOR_WAKE_WORD -> {
                updateNotification("Listening for Hey Jarvis")
                updateHudState("STANDBY", JarvisCoreView.State.IDLE)
            }
            AudioState.HANDING_OFF_TO_COMMAND_RECOGNIZER -> {
                updateNotification("Jarvis activated")
                wakeWordEngine?.stop()
                updateHudState("ACTIVATING...", JarvisCoreView.State.PROCESSING)

                speakThenListenForCommand = true
                speak("Ji, bolo.")
            }
            AudioState.LISTENING_FOR_COMMAND -> {
                wakeWordEngine?.stop()
                startCommandTimeout()
                updateHudState("JARVIS LISTENING", JarvisCoreView.State.LISTENING)
                startSpeechRecognizerListening()
            }
            AudioState.PROCESSING_COMMAND -> {
                cancelCommandTimeout()
                updateNotification("Processing command...")
                updateHudState("JARVIS PROCESSING", JarvisCoreView.State.PROCESSING)
            }
            AudioState.SPEAKING -> {
                updateNotification("Jarvis is speaking")
                updateHudState("JARVIS SPEAKING", JarvisCoreView.State.SPEAKING)
            }
            AudioState.RECOVERING_FROM_ERROR -> {
                cancelCommandTimeout()
                cancelRecognition()
                updateNotification("Recovering from error...")
                updateHudState("STANDBY", JarvisCoreView.State.IDLE)
                scheduleWakeWordRestart(500L)
            }
            AudioState.STOPPED -> {
                wakeWordEngine?.stop()
                cancelRecognition()
                updateHudState("STANDBY", JarvisCoreView.State.IDLE)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopJarvisService()
            return START_NOT_STICKY
        }
        if (intent?.action == "com.user.jarvis.ACTION_START_ASSISTANT_COMMAND") {
            handler.post {
                // If triggered by assistant intent, bypass wake word
                cancelWakeWordRestart()
                transitionToState(AudioState.HANDING_OFF_TO_COMMAND_RECOGNIZER)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        serviceActive = false
        transitionToState(AudioState.STOPPED)
        removeOverlayWindow()
        cancelWakeWordRestart()
        cancelCommandTimeout()
        speakThenListenForCommand = false
        notificationPingRunnable?.let { handler.removeCallbacks(it) }
        notificationPingRunnable = null
        wakeWordEngine?.release()
        wakeWordEngine = null
        cancelRecognition()
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        releaseWakeLock()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun updateHudState(text: String, state: JarvisCoreView.State) {
        handler.post {
            overlayView?.let { view ->
                val stateLabel = view.findViewById<TextView>(R.id.hudStateLabel)
                val coreView = view.findViewById<JarvisCoreView>(R.id.hudParticleOrb)
                stateLabel?.text = text
                coreView?.currentState = state
                if (state == JarvisCoreView.State.IDLE) {
                    view.visibility = View.GONE
                } else {
                    view.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun createOverlayWindow() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                android.util.Log.w(TAG, "Missing SYSTEM_ALERT_WINDOW permission; skipping overlay.")
                return
            }
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val frameLayout = android.view.LayoutInflater.from(this).inflate(R.layout.jarvis_hud_overlay, null) as FrameLayout

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            layoutParams.gravity = Gravity.TOP or Gravity.START
            layoutParams.x = 0
            layoutParams.y = 0

            overlayView = frameLayout
            try {
                windowManager?.addView(overlayView, layoutParams)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to add overlay window", e)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception in createOverlayWindow", e)
        }
    }

    private fun removeOverlayWindow() {
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to remove overlay window", e)
        }
    }

    private fun initializeTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val hindiResult = tts?.setLanguage(Locale("hi", "IN"))
                if (hindiResult == TextToSpeech.LANG_MISSING_DATA || hindiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("en", "IN"))
                }
                tts?.let { VoicePreferences.applySavedVoice(this@JarvisListenerService, it) }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        handler.post {
                            if (currentState == AudioState.PROCESSING_COMMAND || currentState == AudioState.HANDING_OFF_TO_COMMAND_RECOGNIZER) {
                                transitionToState(AudioState.SPEAKING)
                            }
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        handler.post {
                            if (speakThenListenForCommand) {
                                speakThenListenForCommand = false
                                handler.postDelayed({
                                    if (serviceActive) {
                                        transitionToState(AudioState.LISTENING_FOR_COMMAND)
                                    }
                                }, 150L)
                            } else if (serviceActive) {
                                scheduleWakeWordRestart(250L)
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        handler.post {
                            if (speakThenListenForCommand) {
                                speakThenListenForCommand = false
                                transitionToState(AudioState.LISTENING_FOR_COMMAND)
                            } else {
                                scheduleWakeWordRestart(250L)
                            }
                        }
                    }
                })
                ttsReady = true
            } else {
                ttsReady = false
            }
        }
    }

    private fun speak(text: String) {
        if (!serviceActive || text.isBlank()) return
        if (!ttsReady) {
            if (currentState == AudioState.LISTENING_FOR_COMMAND || currentState == AudioState.HANDING_OFF_TO_COMMAND_RECOGNIZER) {
                transitionToState(AudioState.LISTENING_FOR_COMMAND)
            } else {
                scheduleWakeWordRestart(500L)
            }
            return
        }
        cancelWakeWordRestart()
        val utteranceId = "jarvis_\${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun onWakeWordDetected() {
        android.util.Log.d(TAG, "Wake word 'Hey Jarvis' detected!")
        if (!serviceActive) return
        if (currentState != AudioState.WAITING_FOR_WAKE_WORD) return

        cancelWakeWordRestart()
        transitionToState(AudioState.HANDING_OFF_TO_COMMAND_RECOGNIZER)
    }

    private fun scheduleWakeWordRestart(delay: Long) {
        cancelWakeWordRestart()
        if (!serviceActive) return
        wakeRestartRunnable = Runnable {
            if (serviceActive && currentState != AudioState.WAITING_FOR_WAKE_WORD) {
                // Restart only if we can start
                if (wakeWordEngine?.isRunning() == true || wakeWordEngine == null) {
                    wakeWordEngine?.restart()
                }
                transitionToState(AudioState.STARTING_WAKE_WORD)
            }
        }
        handler.postDelayed(wakeRestartRunnable!!, delay)
    }

    private fun cancelWakeWordRestart() {
        wakeRestartRunnable?.let { handler.removeCallbacks(it) }
        wakeRestartRunnable = null
    }

    private fun createSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            return
        }
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                if (currentState == AudioState.LISTENING_FOR_COMMAND) {
                    updateNotification("Listening...")
                }
            }
            override fun onBeginningOfSpeech() {
                if (currentState == AudioState.LISTENING_FOR_COMMAND) {
                    updateNotification("Listening to your command...")
                }
            }
            override fun onRmsChanged(rmsdB: Float) {
                overlayView?.let { view ->
                    val coreView = view.findViewById<JarvisCoreView>(R.id.hudParticleOrb)
                    val intensity = (rmsdB / 10f).coerceIn(0f, 1f)
                    coreView?.externalPulse = 1f + intensity * 0.2f
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                if (currentState == AudioState.LISTENING_FOR_COMMAND) {
                    transitionToState(AudioState.PROCESSING_COMMAND)
                }
            }
            override fun onError(error: Int) {
                handler.post {
                    if (currentState != AudioState.LISTENING_FOR_COMMAND && currentState != AudioState.PROCESSING_COMMAND) return@post
                    transitionToState(AudioState.RECOVERING_FROM_ERROR)
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                handler.post {
                    if (currentState != AudioState.PROCESSING_COMMAND && currentState != AudioState.LISTENING_FOR_COMMAND) return@post

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()?.trim().orEmpty()
                    android.util.Log.d(TAG, "SpeechRecognizer onResults: \$spokenText")

                    if (spokenText.isBlank()) {
                        speakThenListenForCommand = false
                        transitionToState(AudioState.RECOVERING_FROM_ERROR)
                        return@post
                    }

                    transitionToState(AudioState.PROCESSING_COMMAND)
                    executeCommand(spokenText)
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
    }

    private fun startSpeechRecognizerListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            transitionToState(AudioState.RECOVERING_FROM_ERROR)
            return
        }

        val recognizer = speechRecognizer ?: run {
            createSpeechRecognizer()
            speechRecognizer ?: run {
                transitionToState(AudioState.RECOVERING_FROM_ERROR)
                return
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra("android.speech.extra.AUDIO_SOURCE", android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            transitionToState(AudioState.RECOVERING_FROM_ERROR)
        }
    }

    private fun cancelRecognition() {
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
    }

    private fun executeCommand(text: String) {
        try {
            commandExecutor?.execute(text)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Command execution failed", e)
            speak("Command execute nahi ho paya.")
            scheduleWakeWordRestart(1000L)
        }
    }

    private fun startCommandTimeout() {
        cancelCommandTimeout()
        commandTimeoutRunnable = Runnable {
            if (currentState == AudioState.LISTENING_FOR_COMMAND) {
                speakThenListenForCommand = false
                transitionToState(AudioState.RECOVERING_FROM_ERROR)
            }
        }
        handler.postDelayed(commandTimeoutRunnable!!, COMMAND_TIMEOUT)
    }

    private fun cancelCommandTimeout() {
        commandTimeoutRunnable?.let { handler.removeCallbacks(it) }
        commandTimeoutRunnable = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Jarvis background assistant"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        if (!serviceActive) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::WakeLock")
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire()
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun stopJarvisService() {
        serviceActive = false
        transitionToState(AudioState.STOPPED)
        removeOverlayWindow()
        cancelWakeWordRestart()
        cancelCommandTimeout()
        notificationPingRunnable?.let { handler.removeCallbacks(it) }
        notificationPingRunnable = null
        try { wakeWordEngine?.release() } catch (_: Exception) {}
        wakeWordEngine = null
        cancelRecognition()
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
