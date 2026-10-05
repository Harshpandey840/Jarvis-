package com.user.jarvis

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

class GestureActionDispatcher(private val context: Context) {
    fun dispatchClick() {
        AirTouchAccessibilityService.instance?.performClick()
    }

    fun dispatchBack() {
        AirTouchAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun dispatchHome() {
        AirTouchAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun dispatchRecents() {
         AirTouchAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    fun dispatchScrollDown() {
         AirTouchAccessibilityService.instance?.performScroll(1)
    }

    fun dispatchScrollUp() {
         AirTouchAccessibilityService.instance?.performScroll(-1)
    }

    fun dispatchLockScreen() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            dpm.lockNow()
        } catch (e: SecurityException) {
            Log.e("GestureActionDispatcher", "DeviceAdmin not enabled for Lock Screen", e)
        }
    }

    fun dispatchVolumeUp() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    fun dispatchVolumeDown() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    fun dispatchPlayPause() {
        // Simple media button dispatch could be done via AudioManager
        // but let's use a generic media event or rely on accessibility/keyevents if needed.
        // Or simple Intent to media session:
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager.dispatchMediaKeyEvent(event)
    }

    fun dispatchScreenshot() {
        AirTouchAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    fun dispatchAction(actionName: String) {
        when (actionName) {
            "HOME" -> dispatchHome()
            "BACK" -> dispatchBack()
            "RECENTS" -> dispatchRecents()
            "SCROLL_UP" -> dispatchScrollUp()
            "SCROLL_DOWN" -> dispatchScrollDown()
            "LOCK_SCREEN" -> dispatchLockScreen()
            "VOLUME_UP" -> dispatchVolumeUp()
            "VOLUME_DOWN" -> dispatchVolumeDown()
            "SCREENSHOT" -> dispatchScreenshot()
            "PLAY_PAUSE" -> dispatchPlayPause()
        }
    }
}