package com.user.jarvis

import android.content.Context

object ApiKeyManager {
    private const val PREFS_NAME = "jarvis_keys"
    private const val KEY_GROQ = "GROQ_API_KEY"

    fun getGroqApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GROQ, "") ?: ""
    }

    fun setGroqApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GROQ, apiKey).apply()
    }
}
