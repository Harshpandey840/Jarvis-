package com.user.jarvis

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

object VoicePreferences {

    private const val PREFS_NAME = "jarvis_voice_prefs"
    private const val KEY_VOICE_NAME = "voice_name"
    private const val KEY_PITCH = "voice_pitch"
    private const val KEY_SPEECH_RATE = "voice_speech_rate"

    // Deep, powerful, authoritative AI settings
    const val DEFAULT_PITCH = 0.65f
    const val DEFAULT_SPEECH_RATE = 0.90f

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

    fun savePitch(context: Context, pitch: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_PITCH, pitch).apply()
    }

    fun getPitch(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_PITCH, DEFAULT_PITCH)
    }

    fun saveSpeechRate(context: Context, speechRate: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SPEECH_RATE, speechRate).apply()
    }

    fun getSpeechRate(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE)
    }

    fun applySavedVoice(context: Context, tts: TextToSpeech) {
        tts.setPitch(getPitch(context))
        tts.setSpeechRate(getSpeechRate(context))

        val voices = tts.voices ?: return
        val savedName = getSavedVoiceName(context)

        if (savedName != null) {
            val match = voices.firstOrNull { it.name == savedName }
            if (match != null) {
                tts.voice = match
                return
            }
        }

        // If no saved voice, try to find a deep male voice.
        // Lower frequencies often have "male", "low" or specific network-less configurations
        val candidates = getCandidateVoices(tts)
        val deepMaleVoice = candidates.firstOrNull { voice ->
            val nameLower = voice.name.lowercase()
            val features = voice.features?.joinToString(",")?.lowercase() ?: ""
            // In Android's local voices, e.g. hi-in-x-hie-local, we can't be sure, but we can look for specific names
            // Google TTS often has network/local variants. We prefer local for speed and offline.
            (nameLower.contains("male") || features.contains("male") || nameLower.contains("-local-"))
        } ?: candidates.firstOrNull { it.name.lowercase().contains("hi-in-x-hie-local") } // Example fallback

        if (deepMaleVoice != null) {
            tts.voice = deepMaleVoice
            saveVoiceName(context, deepMaleVoice.name)
        } else if (candidates.isNotEmpty()) {
            // Fallback to finding something with en_IN or hi_IN
            val fallbackVoice = candidates.firstOrNull { it.locale.toString().contains("hi") } ?: candidates.first()
            tts.voice = fallbackVoice
            saveVoiceName(context, fallbackVoice.name)
        }
    }
}
