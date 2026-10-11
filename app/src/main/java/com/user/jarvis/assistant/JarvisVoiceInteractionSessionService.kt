package com.user.jarvis.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.user.jarvis.JarvisListenerService
import android.content.Context
import com.user.jarvis.MainActivity

class JarvisVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JarvisVoiceInteractionSession(this)
    }
}

class JarvisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        // Launch MainActivity as Voice Activity
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            startVoiceActivity(launchIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                context.startActivity(launchIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        // Start listening
        val intent = Intent(context, JarvisListenerService::class.java).apply {
            action = "com.user.jarvis.ACTION_START_ASSISTANT_COMMAND"
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        hide()
    }
}
