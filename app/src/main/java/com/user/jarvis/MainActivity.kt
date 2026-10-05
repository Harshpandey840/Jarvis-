package com.user.jarvis

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import android.util.TypedValue
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var stateLabel: TextView
    private lateinit var youSaidText: TextView
    private lateinit var jarvisReplyText: TextView
    private lateinit var chatScrollView: ScrollView
    private lateinit var chatContainer: LinearLayout
    private lateinit var gamingStatusText: TextView
    private lateinit var gamingGameText: TextView
    private lateinit var gamingOptimizationText: TextView
    private lateinit var aimAssistStatusText: TextView
    private lateinit var gamingModeToggleBtn: Button
    private lateinit var aimAssistToggleBtn: Button
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var micButton: View
    private lateinit var voiceSettingsButton: View
    private lateinit var notesListButton: View
    private lateinit var fileSummaryButton: View
    private lateinit var securityModeButton: View
    private lateinit var jarvisToggleButton: Button
    private lateinit var particleOrb: ParticleSphereView
    private lateinit var micGlow: View

    private lateinit var commandExecutor: CommandExecutor
    private var isJarvisRunning = false

    private lateinit var systemSettingsStatusText: TextView
    private lateinit var systemSettingsEnableBtn: Button
    private lateinit var systemSettingsManager: SystemSettingsManager
    private var wrongPinAttempts = 0

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) it + Manifest.permission.POST_NOTIFICATIONS else it
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val fileSummaryLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) handleFileForSummary(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stateLabel = findViewById(R.id.stateLabel)
        youSaidText = findViewById(R.id.youSaidText)
        jarvisReplyText = findViewById(R.id.jarvisReplyText)
        chatScrollView = findViewById(R.id.chatScrollView)
        chatContainer = findViewById(R.id.chatContainer)
        gamingStatusText = findViewById(R.id.gamingStatusText)
        gamingGameText = findViewById(R.id.gamingGameText)
        gamingOptimizationText = findViewById(R.id.gamingOptimizationText)
        aimAssistStatusText = findViewById(R.id.aimAssistStatusText)
        gamingModeToggleBtn = findViewById(R.id.gamingModeToggleBtn)
        aimAssistToggleBtn = findViewById(R.id.aimAssistToggleBtn)
        statusDot = findViewById(R.id.statusDot)
        statusLabel = findViewById(R.id.statusLabel)
        micButton = findViewById(R.id.micButton)
        voiceSettingsButton = findViewById(R.id.voiceSettingsButton)
        notesListButton = findViewById(R.id.notesListButton)
        fileSummaryButton = findViewById(R.id.fileSummaryButton)
        securityModeButton = findViewById(R.id.securityModeButton)
        jarvisToggleButton = findViewById(R.id.jarvisToggleButton)
        particleOrb = findViewById(R.id.particleOrb)
        micGlow = findViewById(R.id.micGlow)

        systemSettingsStatusText = findViewById(R.id.systemSettingsStatusText)
        systemSettingsEnableBtn = findViewById(R.id.systemSettingsEnableBtn)

        systemSettingsManager = SystemSettingsManager(this)

        systemSettingsEnableBtn.setOnClickListener {
            systemSettingsManager.requestWriteSettingsPermission()
        }

        youSaidText.visibility = View.INVISIBLE
        jarvisReplyText.visibility = View.INVISIBLE

        // Hidden elements mapped to preserve compatibility without crashing
        statusDot.visibility = View.GONE
        statusLabel.visibility = View.GONE

        statusDot.backgroundTintList = ColorStateList.valueOf(getColorCompat(R.color.accent_offline_gray))

      commandExecutor = CommandExecutor(
    this,
    { text -> speak(text) },
    {},
    { view ->
        runOnUiThread {
            chatContainer.addView(view)
            chatScrollView.post {
                chatScrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
)  

        tts = TextToSpeech(this, this)

        gamingModeToggleBtn.setOnClickListener {
            val manager = GamingModeManager.getInstance(this)
            if (manager.isGamingModeOn) {
                manager.disableGamingMode { msg -> speak(msg) }
            } else {
                manager.enableGamingMode { msg -> speak(msg) }
            }
        }

        aimAssistToggleBtn.setOnClickListener {
            val manager = GamingModeManager.getInstance(this)
            if (manager.isAimAssistOn) {
                manager.disableAimAssist { msg -> speak(msg) }
            } else {
                manager.enableAimAssist { msg -> speak(msg) }
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(recognitionListener)

        permissionLauncher.launch(requiredPermissions)

        micButton.setOnClickListener { startListening() }
        jarvisToggleButton.setOnClickListener { toggleJarvisService() }
        voiceSettingsButton.setOnClickListener { showSettingsDialog() }
        notesListButton.setOnClickListener { startActivity(Intent(this, ListActivity::class.java)) }
        fileSummaryButton.setOnClickListener { requestOverlayPermission() }
        securityModeButton.setOnClickListener { enrollFace() }

        setupPulseAnimation()
        lockUiUntilPinVerified()

        if (AirTouchSettings.isEnabled(this)) {
            AirTouchForegroundService.start(this)
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            try {
                startActivity(intent)
            } catch (e: Exception) { }
        }

        checkAndPromptGroqApiKey()
    }

    private fun showApiKeySettingsDialog() {
        val options = arrayOf("Groq API Key", "RapidAPI Key")
        AlertDialog.Builder(this)
            .setTitle("API Key Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkAndPromptGroqApiKey(force = true)
                    1 -> checkAndPromptRapidApiKey(force = true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAndPromptGroqApiKey(force: Boolean = false) {
        val currentKey = ApiKeyManager.getGroqApiKey(this)
        if (currentKey.isBlank() || force) {
            val input = EditText(this).apply {
                hint = "Enter Groq API Key"
                setText(currentKey)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(50, 50, 50, 50)
            }
            AlertDialog.Builder(this)
                .setTitle(if (currentKey.isBlank()) "API Key Required" else "Update API Key")
                .setMessage("Please enter your Groq API key to use Jarvis AI features.")
                .setView(input)
                .setCancelable(!currentKey.isBlank() && force)
                .setPositiveButton("Save") { _, _ ->
                    val newKey = input.text.toString().trim()
                    if (newKey.isNotBlank() && newKey.startsWith("gsk_")) {
                        ApiKeyManager.setGroqApiKey(this, newKey)
                        Toast.makeText(this, "API Key saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invalid Key! Must start with gsk_", Toast.LENGTH_SHORT).show()
                        checkAndPromptGroqApiKey(force) // Prompt again if empty or invalid
                    }
                }
                .apply {
                    if (force && currentKey.isNotBlank()) {
                        setNegativeButton("Cancel", null)
                    }
                }
                .show()
        }
    }

    private fun checkAndPromptRapidApiKey(force: Boolean = false) {
        val currentKey = ApiKeyManager.getRapidApiKey(this)
        if (currentKey.isBlank() || force) {
            val input = EditText(this).apply {
                hint = "Enter RapidAPI Key"
                setText(currentKey)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(50, 50, 50, 50)
            }
            AlertDialog.Builder(this)
                .setTitle(if (currentKey.isBlank()) "RapidAPI Key Required" else "Update RapidAPI Key")
                .setMessage("Please enter your RapidAPI key for train tracking.")
                .setView(input)
                .setCancelable(force)
                .setPositiveButton("Save") { _, _ ->
                    val newKey = input.text.toString().trim()
                    if (newKey.isNotBlank()) {
                        ApiKeyManager.setRapidApiKey(this, newKey)
                        Toast.makeText(this, "RapidAPI Key saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Key cannot be empty!", Toast.LENGTH_SHORT).show()
                        checkAndPromptRapidApiKey(force)
                    }
                }
                .apply {
                    if (force) {
                        setNegativeButton("Cancel", null)
                    }
                }
                .show()
        }
    }
    private val gamingModeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateGamingStatus()
        }
    }

    private fun updateGamingStatus() {
        val manager = GamingModeManager.getInstance(this)
        gamingStatusText.text = "Gaming Mode: ${if (manager.isGamingModeOn) "ON" else "OFF"}"

        val game = manager.currentGame
        if (manager.isGamingModeOn) {
            gamingGameText.text = "Game: ${game ?: "No game detected"}"
            // Tangible optimization: keep screen on while app is in foreground and gaming mode is on
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            gamingGameText.text = "Game: None"
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val hasSettings = Settings.System.canWrite(this)
        val hasDnd = notificationManager.isNotificationPolicyAccessGranted

        if (manager.isGamingModeOn) {
            gamingOptimizationText.text = "Optimization: ${if (hasSettings && hasDnd) "ACTIVE" else "LIMITED"}"
        } else {
            gamingOptimizationText.text = "Optimization: NONE"
        }

        val overlayGranted = Settings.canDrawOverlays(this)
        if (manager.isAimAssistOn) {
            aimAssistStatusText.text = "Aim Assist: ON"
        } else if (!overlayGranted) {
            aimAssistStatusText.text = "Aim Assist: PERMISSION REQUIRED"
        } else {
            aimAssistStatusText.text = "Aim Assist: OFF"
        }

        gamingModeToggleBtn.text = if (manager.isGamingModeOn) "Stop Gaming" else "Start Gaming"
        aimAssistToggleBtn.text = if (manager.isAimAssistOn) "Crosshair: OFF" else "Crosshair: ON"
    }

    private fun updateSystemSettingsUI() {
        val isAllowed = systemSettingsManager.isWriteSettingsAllowed()
        if (isAllowed) {
            systemSettingsStatusText.text = "Modify system settings: Allowed"
            systemSettingsEnableBtn.visibility = View.GONE
        } else {
            systemSettingsStatusText.text = "Modify system settings: Not allowed"
            systemSettingsEnableBtn.visibility = View.VISIBLE
        }
    }

override fun onResume() {
        super.onResume()
        updateGamingStatus()
        updateSystemSettingsUI()

        val filter = android.content.IntentFilter("com.user.jarvis.GAMING_MODE_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(gamingModeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(gamingModeReceiver, filter)
        }
        if (FaceSecurityPrefs.isEnabled(this) && FaceSecurityPrefs.getReferenceFacePath(this) != null) {
            val intent = Intent(this, FaceSecurityService::class.java)
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (e: Exception) { }
        }
}
    private fun getColorCompat(resId: Int) = ContextCompat.getColor(this, resId)

    // ---------- Security Mode (face recognition lock) ----------

    private fun onSecurityModeClicked() {
        if (FaceSecurityPrefs.isEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("Security Mode")
                .setMessage("Security Mode ON hai. Band karna hai?")
                .setPositiveButton("Band karo") { _, _ ->
                    FaceSecurityPrefs.setEnabled(this, false)
                    Toast.makeText(this, "Security Mode band kar diya", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Rehne do", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Security Mode Enable karo")
                .setMessage("Apna face enroll karna hoga. Camera ke saamne seedha dekho aur OK dabao.")
                .setPositiveButton("OK") { _, _ -> enrollFace() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun enrollFace() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission chahiye", Toast.LENGTH_SHORT).show()
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder().build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture)

                val photoFile = File(cacheDir, "enroll_temp.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            cameraProvider.unbindAll()
                            processEnrollmentPhoto(photoFile)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            cameraProvider.unbindAll()
                            Toast.makeText(this@MainActivity, "Photo capture fail hua", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processEnrollmentPhoto(photoFile: File) {
        val bitmap = BitmapFactory.decodeFile(photoFile.path)
        if (bitmap == null) {
            Toast.makeText(this, "Photo padh nahi paya", Toast.LENGTH_SHORT).show()
            return
        }
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        FaceDetection.getClient(options).process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Toast.makeText(this, "Face detect nahi hua, dobara try karo", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val box = faces[0].boundingBox
                try {
                    val cropped = Bitmap.createBitmap(
                        bitmap,
                        box.left.coerceIn(0, bitmap.width - 1),
                        box.top.coerceIn(0, bitmap.height - 1),
                        box.width().coerceIn(1, bitmap.width - box.left.coerceIn(0, bitmap.width - 1)),
                        box.height().coerceIn(1, bitmap.height - box.top.coerceIn(0, bitmap.height - 1))
                    )
                    val refFile = File(filesDir, "reference_face.jpg")
                    FileOutputStream(refFile).use { out ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    FaceSecurityPrefs.setReferenceFacePath(this, refFile.path)
                    FaceSecurityPrefs.setEnabled(this, true)
                    requestDeviceAdminIfNeeded()
                    Toast.makeText(this, "Face enroll ho gaya, Security Mode ON", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Crop fail hua", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Face detection fail hua", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------- File summarize ----------

    private fun handleFileForSummary(uri: Uri) {
        stateLabel.text = "File padh raha hoon..."
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (text.isBlank()) {
                speak("Is file mein text nahi mila")
                return
            }
            stateLabel.text = "Summarize kar raha hoon..."
            AiCommandParser.summarizeText(text, ApiKeyManager.getGroqApiKey(this)) { summary ->
                addChatMessage("[File summary]", true)
                speak(summary)
            }
        } catch (e: Exception) {
            speak("File nahi padh paya")
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Voice Settings", "API Key Settings", "AirTouch Settings")
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showVoicePicker()
                    1 -> showApiKeySettingsDialog()
                    2 -> showAirTouchSettingsDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAirTouchSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_airtouch, null)
        val switchAirTouch = dialogView.findViewById<android.widget.Switch>(R.id.switchAirTouch)
        val btnAccessibility = dialogView.findViewById<Button>(R.id.btnAccessibility)
        val btnOverlay = dialogView.findViewById<Button>(R.id.btnOverlay)
        val seekCursorSmoothing = dialogView.findViewById<android.widget.SeekBar>(R.id.seekCursorSmoothing)

        val seekGestureCooldown = dialogView.findViewById<android.widget.SeekBar>(R.id.seekGestureCooldown)

        val btnCustomGestures = dialogView.findViewById<Button>(R.id.btnCustomGestures)

        switchAirTouch.isChecked = AirTouchSettings.isEnabled(this)
        seekCursorSmoothing.progress = AirTouchSettings.getCursorSmoothing(this)
        seekGestureCooldown.progress = AirTouchSettings.getGestureCooldown(this)

        switchAirTouch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AirTouchSettings.setEnabled(this, true)
                AirTouchForegroundService.start(this)
            } else {
                AirTouchSettings.setEnabled(this, false)
                AirTouchForegroundService.stop(this)
            }
        }

        btnCustomGestures.setOnClickListener {
            startActivity(Intent(this, com.user.jarvis.gestures.ui.CustomGesturesActivity::class.java))
        }

        seekCursorSmoothing.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    AirTouchSettings.setCursorSmoothing(this@MainActivity, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        seekGestureCooldown.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    AirTouchSettings.setGestureCooldown(this@MainActivity, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission is already granted", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

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
        // Pulse animation is now handled internally by ParticleSphereView
        // through its continuous 3D rotation, and dynamic RMS scaling in onRmsChanged.
    }
    // ---------- PIN lock + intruder security ----------

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
            .setMessage("Ye PIN app kholte waqt maangega. Agar koi 3 baar galat PIN daale, uska photo khinch ke phone lock ho jayega.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Set karo") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..6) {
                    prefs.edit().putString("pin", pin).apply()
                    requestDeviceAdminIfNeeded()
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
                if (input.text.toString() == savedPin) {
                    wrongPinAttempts = 0
                    unlockUi()
                } else {
                    wrongPinAttempts++
                    Toast.makeText(this, "Galat PIN", Toast.LENGTH_SHORT).show()
                    if (wrongPinAttempts >= 3) {
                        triggerSecurityLockdown()
                        wrongPinAttempts = 0
                    }
                    promptEnterPin(savedPin)
                }
            }
            .show()
    }

    private fun unlockUi() {
        micButton.isEnabled = true
        jarvisToggleButton.isEnabled = true
    }

    private fun requestDeviceAdminIfNeeded() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, JarvisDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Security lock feature ke liye ye permission chahiye")
            }
            try { startActivity(intent) } catch (e: Exception) { }
        }
    }

    private fun triggerSecurityLockdown() {
        captureIntruderPhoto()
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, JarvisDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
            }
        } catch (e: Exception) { }
    }

    private fun captureIntruderPhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder().build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture)

                val photoFile = File(filesDir, "intruder_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            cameraProvider.unbindAll()
                        }
                        override fun onError(exception: ImageCaptureException) {
                            cameraProvider.unbindAll()
                        }
                    }
                )
            } catch (e: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

    // ---------- Tap-to-speak ----------

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val hindiResult = tts.setLanguage(Locale("hi", "IN"))
            if (hindiResult == TextToSpeech.LANG_MISSING_DATA || hindiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale("en", "IN"))
            }
            VoicePreferences.applySavedVoice(this, tts)
            tts.setPitch(0.78f)
            tts.setSpeechRate(0.98f)
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        }
        stateLabel.text = "Listening..."
        speechRecognizer.startListening(intent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.firstOrNull().orEmpty()
            if (spokenText.isNotBlank()) {
                addChatMessage(spokenText, true)
                stateLabel.text = "Thinking..."
                commandExecutor.execute(spokenText)
            } else {
                stateLabel.text = "Just Say It."
            }
        }
        override fun onError(error: Int) { stateLabel.text = "Just Say It." }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            val scale = 1f + (rmsdB / 10f).coerceIn(0f, 1f) * 0.5f
            micButton.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(50)
                .start()
            particleOrb.pulseScale = 1f + (rmsdB / 10f).coerceIn(0f, 1f) * 0.2f
        }
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
            jarvisToggleButton.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.ic_media_play, 0, 0)
            statusDot.backgroundTintList = ColorStateList.valueOf(getColorCompat(R.color.accent_offline_gray))
            statusLabel.text = "STANDBY"
        } else {
            requestBatteryOptimizationExemption()
            requestOverlayPermission()
            val startIntent = Intent(this, JarvisListenerService::class.java)
            ContextCompat.startForegroundService(this, startIntent)
            isJarvisRunning = true
            jarvisToggleButton.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.ic_media_pause, 0, 0)
            statusDot.backgroundTintList = ColorStateList.valueOf(getColorCompat(R.color.accent_online_green))
            statusLabel.text = "ALWAYS ON"
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

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            try { startActivity(intent) } catch (e: Exception) { }
        }
    }

    private fun speak(text: String) {
        val cleanText = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        addChatMessage(cleanText, false)
        stateLabel.text = "Jarvis is speaking"
        SciFiTone.play()
        tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)

        if (cleanText.contains("API key khaali hai", ignoreCase = true)) {
            checkAndPromptGroqApiKey()
        } else if (cleanText.contains("RapidAPI key nahi hai", ignoreCase = true)) {
            checkAndPromptRapidApiKey(force = true)
        }
    }

    private fun addChatMessage(text: String, isUser: Boolean) {
        val textView = TextView(this).apply {
            this.text = text
            setTextColor(getColorCompat(R.color.text_primary))
            textSize = 14f

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isUser) Gravity.END else Gravity.START
                bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
            }
            layoutParams = params

            val bgResId = if (isUser) R.drawable.bg_chat_user else R.drawable.bg_chat_jarvis
            background = ContextCompat.getDrawable(this@MainActivity, bgResId)
        }
        chatContainer.addView(textView)
        chatScrollView.post {
            chatScrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(gamingModeReceiver)
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
