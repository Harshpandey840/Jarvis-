package com.user.jarvis

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.media.AudioManager
import android.provider.Settings
import android.util.Log

class GamingModeManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("gaming_mode_prefs", Context.MODE_PRIVATE)

    var isGamingModeOn: Boolean
        get() = prefs.getBoolean("is_gaming_mode_on", false)
        set(value) = prefs.edit().putBoolean("is_gaming_mode_on", value).apply()

    var isAimAssistOn: Boolean
        get() = prefs.getBoolean("is_aim_assist_on", false)
        set(value) = prefs.edit().putBoolean("is_aim_assist_on", value).apply()

    private var originalScreenTimeout: Int
        get() = prefs.getInt("orig_screen_timeout", -1)
        set(value) = prefs.edit().putInt("orig_screen_timeout", value).apply()

    private var originalRingerMode: Int
        get() = prefs.getInt("orig_ringer_mode", -1)
        set(value) = prefs.edit().putInt("orig_ringer_mode", value).apply()

    var currentGame: String?
        get() = prefs.getString("current_game", null)
        set(value) = prefs.edit().putString("current_game", value).apply()

    // Known game package names for detection
    private val KNOWN_GAMES = setOf(
        "com.dts.freefiremax",
        "com.dts.freefireth",
        "com.tencent.ig",
        "com.pubg.imobile",
        "com.activision.callofduty.shooter"
    )

    fun checkForegroundGame(): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 10000, time) // Check last 10 seconds

        var currentForegroundPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForegroundPackage = event.packageName
            }
        }

        return if (currentForegroundPackage != null && KNOWN_GAMES.contains(currentForegroundPackage)) {
            currentForegroundPackage
        } else {
            null
        }
    }

    fun enableGamingMode(onSpeak: ((String) -> Unit)? = null) {
        if (isGamingModeOn) {
            onSpeak?.invoke("Gaming mode is already on, sir.")
            return
        }

        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onSpeak?.invoke("Please grant permission to modify system settings for gaming optimizations, sir.")
            return
        }

        try {
            val currentTimeout = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            originalScreenTimeout = currentTimeout
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE)
        } catch (e: Exception) {
            Log.e("GamingModeManager", "Could not modify screen timeout", e)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalRingerMode = audioManager.ringerMode
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE

            // Adjust volume to 85% for gaming
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (maxVolume * 0.85).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
        } else {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onSpeak?.invoke("Please grant Do Not Disturb access to optimize gaming mode, sir.")
        }

        isGamingModeOn = true

        // Notify UI
        val updateIntent = Intent("com.user.jarvis.GAMING_MODE_UPDATED")
        context.sendBroadcast(updateIntent)

        val game = checkForegroundGame()
        if (game != null) {
            currentGame = game
            onSpeak?.invoke("Gaming mode activated. Detected $game. Optimizing background activity.")
        } else {
            currentGame = null
            onSpeak?.invoke("Gaming mode activated. Optimizing background activity.")
        }
    }

    fun disableGamingMode(onSpeak: ((String) -> Unit)? = null) {
        if (!isGamingModeOn) {
            onSpeak?.invoke("Gaming mode is already off, sir.")
            return
        }

        if (originalScreenTimeout != -1) {
            try {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, originalScreenTimeout)
            } catch (e: SecurityException) {
                Log.e("GamingModeManager", "Could not restore screen timeout", e)
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted && originalRingerMode != -1) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = originalRingerMode
        }

        isGamingModeOn = false
        currentGame = null

        val updateIntent = Intent("com.user.jarvis.GAMING_MODE_UPDATED")
        context.sendBroadcast(updateIntent)

        onSpeak?.invoke("Gaming mode deactivated. Normal operations resumed.")
    }

    fun enableAimAssist(onSpeak: ((String) -> Unit)? = null) {
        if (isAimAssistOn) {
            onSpeak?.invoke("Aim assist is already active, sir.")
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onSpeak?.invoke("Please grant display over other apps permission for the crosshair overlay, sir.")
            return
        }

        val serviceIntent = Intent(context, AimAssistOverlayService::class.java)
        context.startService(serviceIntent)

        isAimAssistOn = true

        val updateIntent = Intent("com.user.jarvis.GAMING_MODE_UPDATED")
        context.sendBroadcast(updateIntent)

        onSpeak?.invoke("Aim assist overlay engaged.")
    }

    fun disableAimAssist(onSpeak: ((String) -> Unit)? = null) {
        if (!isAimAssistOn) {
            onSpeak?.invoke("Aim assist is not currently active, sir.")
            return
        }

        val serviceIntent = Intent(context, AimAssistOverlayService::class.java)
        context.stopService(serviceIntent)

        isAimAssistOn = false

        val updateIntent = Intent("com.user.jarvis.GAMING_MODE_UPDATED")
        context.sendBroadcast(updateIntent)

        onSpeak?.invoke("Aim assist overlay disabled.")
    }
}
