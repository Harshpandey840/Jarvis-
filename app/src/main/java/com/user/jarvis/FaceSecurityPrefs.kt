package com.user.jarvis

import android.content.Context

object FaceSecurityPrefs {
    private const val PREFS_NAME = "jarvis_face_security"
    private const val KEY_ENABLED = "security_mode_enabled"
    private const val KEY_REFERENCE_PATH = "reference_face_path"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getReferenceFacePath(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_REFERENCE_PATH, null)

    fun setReferenceFacePath(context: Context, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_REFERENCE_PATH, path).apply()
    }
}
