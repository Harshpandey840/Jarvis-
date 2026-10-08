package com.user.jarvis.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.user.jarvis.JarvisListenerService
import android.content.Context

class JarvisVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JarvisVoiceInteractionSession(this)
    }
}

class JarvisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        // When assistant is invoked, tell JarvisListenerService to start listening
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

        // Hide UI immediately since Jarvis HUD handles it
        hide()
    }
}
