package com.user.jarvis

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var stateLabel: TextView
    private lateinit var youSaidText: TextView
    private lateinit var jarvisReplyText: TextView
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var micButton: View
    private lateinit var voiceSettingsButton: TextView
    private lateinit var notesListButton: TextView
    private lateinit var jarvisToggleButton: Button

    private lateinit var commandExecutor: CommandExecutor
    private var isJarvisRunning = false

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) it + Manifest.permission.POST_NOTIFICATIONS else it
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stateLabel = findViewById(R.id.stateLabel)
        youSaidText = findViewById(R.id.youSaidText)
        jarvisReplyText = findViewById(R.id.jarvisReplyText)
        statusDot = findViewById(R.id.statusDot)
        statusLabel = findViewById(R.id.statusLabel)
        micButton = findViewById(R.id.micButton)
        voiceSettingsButton = findViewById(R.id.voiceSettingsButton)
        notesListButton = findViewById(R.id.notesListButton)
        jarvisToggleButton = findViewById(R.id.jarvisToggleButton)

        statusDot.backgroundTintList = ColorStateList.valueOf(getColorCompat(R.color.accent_offline_gray))

        commandExecutor = CommandExecutor(this) { speak(it) }

        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(recognitionListener)

        permissionLauncher.launch(requiredPermissions)

        micButton.setOnClickListener { startListening() }
        jarvisToggleButton.setOnClickListener { toggleJarvisService() }
        voiceSettingsButton.setOnClickListener { showVoicePicker() }
        notesListButton.setOnClickListener { startActivity(Intent(this, ListActivity::class.java)) }

        setupPulseAnimation()
        lockUiUntilPinVerified()
    }

    private fun getColorCompat(resId: Int) = ContextCompat.getColor(this, resId)

    // ---------- Voice picker ----------

    private fun showVoicePicker() {
        val voices = VoicePreferences.getCandidateVoices(tts)
        if (voices.isEmpty()) {
            Toast.makeText(this, "Koi extra voice nahi mili is phone mein", Toast.LENGTH_SHORT).show()
            return
        }
        val names = voices.map { VoicePreferences.displayName(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Voice choose karo")
            .setItems(names) { _, which ->
                val chosen = voices[which]
                tts.voice = chosen
                VoicePreferences.saveVoiceName(this, chosen.name)
                tts.speak("Namaste, main Jarvis hoon. Kaisi lagi ye awaaz?", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            .setNegativeButton("Band karo", null)
            .show()
    }

    // ---------- Orb pulse animation ----------

    private fun setupPulseAnimation() {
        pulseView(findViewById(R.id.pulseRingOuter), 0L)
        pulseView(findViewById(R.id.pulseRingInner), 800L)
    }

    private fun pulseView(view: View, delay: Long) {
        view.alpha = 0f
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.7f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.7f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.55f, 0f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1700
            startDelay = delay
            interpolator = LinearInterpolator()
            start()
        }
    }

    // ---------- PIN lock ----------

    private fun lockUiUntilPinVerified() {
        micButton.isEnabled = false
        jarvisToggleButton.isEnabled = false
        val securityPrefs = getSharedPreferences("jarvis_security", Context.MODE_PRIVATE)
        val savedPin = securityPrefs.getString("pin", null)
        if (savedPin == null) promptSetPin(securityPrefs) else promptEnterPin(savedPin)
    }

    private fun promptSetPin(prefs: SharedPreferences) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4 se 6 digit PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Jarvis PIN set karo")
            .setMessage("Ye PIN app kholte waqt maangega")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Set karo") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..6) {
                    prefs.edit().putString("pin", pin).apply()
                    unlockUi()
                } else {
                    Toast.makeText(this, "4 se 6 digit ka PIN daalo", Toast.LENGTH_SHORT).show()
                    promptSetPin(prefs)
                }
            }
            .setNegativeButton("Skip") { _, _ -> unlockUi() }
            .show()
    }

    private fun promptEnterPin(savedPin: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN daalo"
        }
        AlertDialog.Builder(this)
            .setTitle("Jarvis Locked")
            .setMessage("PIN daalke unlock karo")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Unlock") { _, _ ->
                if (input.text.toString() == savedPin) unlockUi()
                else {
                    Toast.makeText(this, "Galat PIN", Toast.LENGTH_SHORT).show()
                    promptEnterPin(savedPin)
                }
            }
            .show()
    }

    private fun unlockUi() {
        micButton.isEnabled = true
        jarvisToggleButton.isEnabled = true
    }

    // ---------- Tap-to-speak ----------

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            VoicePreferences.applySavedVoice(this, tts)
        }
    }

    private fun startListening() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            permissionLauncher.launch(requiredPermissions); return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        stateLabel.text = "Sun raha hoon..."
        speechRecognizer.startListening(intent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.firstOrNull().orEmpty()
            if (spokenText.isNotBlank()) {
                youSaidText.text = spokenText
                stateLabel.text = "Soch raha hoon..."
                commandExecutor.execute(spokenText)
            } else {
                stateLabel.text = "Mic dabao aur bolo"
            }
        }
        override fun onError(error: Int) { stateLabel.text = "Samajh nahi aaya, phir bolo" }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ---------- Always-on background Jarvis ----------

    private fun toggleJarvisService() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            permissionLauncher.launch(requiredPermissions); return
        }
        if (isJarvisRunning) {
            val stopIntent = Intent(this, JarvisListenerService::class.java).apply {
                action = JarvisListenerService.ACTION_STOP
            }
            startService(stopIntent)
            isJarvisRunning = false
            jarvisToggleButton.text = "  START JARVIS · ALWAYS ON"
            statusDot.backgroundTintList = ColorStateList.valueOf(getColorCompat(R.color.accent_offline_gray))
            statusLabel.text = "STANDBY"
        } else {
            requestOverlayPermission()
            requestBatteryOptimizationExemption()
            val startIntent = Intent(this, JarvisListenerService::class.java)
            ContextCompat.startForegroundService(this, startIntent)
            isJarvisRunning = true
            jarvisToggleButton.text = "  STOP JARVIS"
            statusDot.backgroundTintList = ColorStateList.valueOf(getColorCompat(R.color.accent_online_green))
            statusLabel.text = "ALWAYS ON"
        }
    }
private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            try { startActivity(intent) } catch (e: Exception) { }
        }
}
    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try { startActivity(intent) } catch (e: Exception) { }
        }
    }

    private fun speak(text: String) {
        jarvisReplyText.text = text
        stateLabel.text = "Mic dabao aur bolo"
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
