package com.user.jarvis.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.RecognitionService.Callback

class JarvisRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {}
    override fun onCancel(listener: Callback) {}
    override fun onStopListening(listener: Callback) {}
}
