package com.user.jarvis

import android.content.Context
import android.content.SharedPreferences

object AirTouchSettings {
    private const val PREFS_NAME = "airtouch_prefs"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(context: Context): Boolean = getPrefs(context).getBoolean("is_enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("is_enabled", enabled).apply()

    fun getCursorSmoothing(context: Context): Int = getPrefs(context).getInt("cursor_smoothing", 30)
    fun setCursorSmoothing(context: Context, smoothing: Int) = getPrefs(context).edit().putInt("cursor_smoothing", smoothing).apply()

    fun isGestureEnabled(context: Context, gestureName: String): Boolean = getPrefs(context).getBoolean("gesture_${gestureName}_enabled", true)
    fun setGestureEnabled(context: Context, gestureName: String, enabled: Boolean) = getPrefs(context).edit().putBoolean("gesture_${gestureName}_enabled", enabled).apply()

    fun getGestureAction(context: Context, gestureName: String, defaultAction: String): String = getPrefs(context).getString("gesture_${gestureName}_action", defaultAction) ?: defaultAction
    fun setGestureAction(context: Context, gestureName: String, action: String) = getPrefs(context).edit().putString("gesture_${gestureName}_action", action).apply()

    fun getGestureCooldown(context: Context): Int = getPrefs(context).getInt("gesture_cooldown", 1000)
    fun setGestureCooldown(context: Context, cooldown: Int) = getPrefs(context).edit().putInt("gesture_cooldown", cooldown).apply()
}
