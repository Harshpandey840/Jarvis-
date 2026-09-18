1700
            startDelay = delay
            interpolator = LinearInterpolator()
            start()
        }
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
            tts.language = Locale("hi", "IN")
            VoicePreferences.applySavedVoice(this, tts)
            tts.setPitch(0.85f)
            tts.setSpeechRate(0.95f)
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
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
            val missing = getMissingBackgroundPermissions()
            if (missing.isNotEmpty()) {
                showBackgroundSetupDialog(missing)
            } else {
                actuallyStartJarvisService()
            }
        }
    }

    private fun actuallyStartJarvisService() {
        val startIntent = Intent(this, JarvisListenerService::class.java)
        ContextCompat.startForegroundService(this, startIntent)
        isJarvisRunning = true
        jarvisToggleButton.text = "  STOP JARVIS"
        statusDot.backgroundTintList = ColorStateList.valueOf(getColorCompat(R.color.accent_online_green))
        statusLabel.text = "ALWAYS ON"
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
        jarvisReplyText.text = cleanText
        stateLabel.text = "Mic dabao aur bolo"
        SciFiTone.play()
        tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)
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
