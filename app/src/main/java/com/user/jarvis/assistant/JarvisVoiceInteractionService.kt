package com.user.jarvis.assistant

import android.service.voice.VoiceInteractionService

class JarvisVoiceInteractionService : VoiceInteractionService() {
    companion object {
        var instance: JarvisVoiceInteractionService? = null
    }

    override fun onReady() {
        super.onReady()
        instance = this
    }

    override fun onShutdown() {
        super.onShutdown()
        instance = null
    }

    fun showAssistantSession() {
        try {
            showSession(null, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
