package com.user.jarvis

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

object VoicePreferences {

    private const val PREFS_NAME = "jarvis_voice_prefs"
    private const val KEY_VOICE_NAME = "voice_name"

    fun getCandidateVoices(tts: TextToSpeech): List<Voice> {
        val voices = tts.voices ?: return emptyList()
        return voices
            .filter { voice ->
                val localeStr = voice.locale.toString()
                (localeStr.startsWith("en") || localeStr.startsWith("hi")) && !voice.isNetworkConnectionRequired
            }
            .sortedBy { it.locale.toString() + it.name }
    }

    fun displayName(voice: Voice): String {
        val localeLabel = when (voice.locale.toString()) {
            "en_IN" -> "English (India)"
            "en_US" -> "English (US)"
            "en_GB" -> "English (UK)"
            "hi_IN" -> "Hindi"
            else -> voice.locale.toString()
        }
        return "$localeLabel — ${voice.name}"
    }

    fun saveVoiceName(context: Context, voiceName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_VOICE_NAME, voiceName).apply()
    }

    fun getSavedVoiceName(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VOICE_NAME, null)
    }

    fun applySavedVoice(context: Context, tts: TextToSpeech) {
        val savedName = getSavedVoiceName(context) ?: return
        val voices = tts.voices ?: return
        val match = voices.firstOrNull { it.name == savedName }
        if (match != null) {
            tts.voice = match
        }
    }
}
