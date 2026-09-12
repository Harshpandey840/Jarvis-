package com.user.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var statusText: TextView
    private lateinit var micButton: Button
    private lateinit var jarvisToggleButton: Button

    private lateinit var commandExecutor: CommandExecutor
    private var isJarvisRunning = false

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it + Manifest.permission.POST_NOTIFICATIONS
        } else it
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        micButton = findViewById(R.id.micButton)
        jarvisToggleButton = findViewById(R.id.jarvisToggleButton)

        commandExecutor = CommandExecutor(this) { speak(it) }

        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(recognitionListener)

        permissionLauncher.launch(requiredPermissions)

        micButton.setOnClickListener { startListening() }
        jarvisToggleButton.setOnClickListener { toggleJarvisService() }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
        }
    }

    private fun startListening() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            permissionLauncher.launch(requiredPermissions)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        statusText.text = "Sun raha hoon..."
        speechRecognizer.startListening(intent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.firstOrNull().orEmpty()
            statusText.text = "Suna: $spokenText"
            if (spokenText.isNotBlank()) commandExecutor.execute(spokenText)
        }

        override fun onError(error: Int) {
            statusText.text = "Samajh nahi aaya, phir se try karein"
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun toggleJarvisService() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            permissionLauncher.launch(requiredPermissions)
            return
        }
        if (isJarvisRunning) {
            val stopIntent = Intent(this, JarvisListenerService::class.java).apply {
                action = JarvisListenerService.ACTION_STOP
            }
            startService(stopIntent)
            isJarvisRunning = false
            jarvisToggleButton.text = "Start Jarvis (Always On)"
            statusText.text = "Jarvis band ho gaya"
        } else {
            requestBatteryOptimizationExemption()
            val startIntent = Intent(this, JarvisListenerService::class.java)
            ContextCompat.startForegroundService(this, startIntent)
            isJarvisRunning = true
            jarvisToggleButton.text = "Stop Jarvis"
            statusText.text = "Jarvis background mein sun raha hai — bolo \"Jarvis\" phir command"
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
            }
        }
    }

    private fun speak(text: String) {
        statusText.text = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
