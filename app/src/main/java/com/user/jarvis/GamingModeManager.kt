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

import android.os.Handler
import android.os.Looper

class GamingModeManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: GamingModeManager? = null

        fun getInstance(context: Context): GamingModeManager {
            return instance ?: synchronized(this) {
                instance ?: GamingModeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("gaming_mode_prefs", Context.MODE_PRIVATE)

    var isGamingModeOn: Boolean
        get() = prefs.getBoolean("is_gaming_mode_on", false)
        set(value) = prefs.edit().putBoolean("is_gaming_mode_on", value).apply()

    var isManuallyEnabled: Boolean
        get() = prefs.getBoolean("is_manually_enabled", false)
        set(value) = prefs.edit().putBoolean("is_manually_enabled", value).apply()

    var isAimAssistOn: Boolean
        get() = prefs.getBoolean("is_aim_assist_on", false)
        set(value) = prefs.edit().putBoolean("is_aim_assist_on", value).apply()

    var isAutoModeEnabled: Boolean
        get() = prefs.getBoolean("is_auto_gaming_mode", false)
        set(value) {
            prefs.edit().putBoolean("is_auto_gaming_mode", value).apply()
            if (value) {
                startAutoDetection()
            } else {
                stopAutoDetection()
            }
        }

    private var originalScreenTimeout: Int
        get() = prefs.getInt("orig_screen_timeout", -1)
        set(value) = prefs.edit().putInt("orig_screen_timeout", value).apply()

    private var originalRingerMode: Int
        get() = prefs.getInt("orig_ringer_mode", -1)
        set(value) = prefs.edit().putInt("orig_ringer_mode", value).apply()

    var currentGame: String?
        get() = prefs.getString("current_game", null)
        set(value) = prefs.edit().putString("current_game", value).apply()

    private var autoDetectionThread: Thread? = null
    @Volatile private var isAutoDetectionRunning = false

    // Known game package names for detection
    private val KNOWN_GAMES = setOf(
        "com.dts.freefiremax",
        "com.dts.freefireth",
        "com.tencent.ig",
        "com.pubg.imobile",
        "com.activision.callofduty.shooter"
    )

    init {
        if (isAutoModeEnabled) {
            startAutoDetection()
        }
    }

    private fun startAutoDetection() {
        if (isAutoDetectionRunning) return
        isAutoDetectionRunning = true
        autoDetectionThread = Thread {
            val handler = Handler(Looper.getMainLooper())
            var lastDetectedGame: String? = null

            while (isAutoDetectionRunning) {
                try {
                    val game = checkForegroundGame()
                    // Only process state changes if the foreground game actually changed
                    if (game != lastDetectedGame) {
                        lastDetectedGame = game
                        handler.post {
                            if (game != null) {
                                if (!isGamingModeOn || currentGame != game) {
                                    Log.i("GamingModeManager", "Auto-detection: Game detected ($game). Starting Gaming Mode.")
                                    isManuallyEnabled = false // Flag as auto-started
                                    enableGamingMode(null)
                                }
                            } else {
                                if (isGamingModeOn && !isManuallyEnabled) {
                                    Log.i("GamingModeManager", "Auto-detection: Game exited. Stopping Gaming Mode.")
                                    disableGamingMode(null)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GamingModeManager", "Error in auto detection", e)
                }
                try {
                    Thread.sleep(5000) // Conservative polling to save battery while relying on unprivileged APIs
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        autoDetectionThread?.start()
    }

    private fun stopAutoDetection() {
        isAutoDetectionRunning = false
        autoDetectionThread?.interrupt()
        autoDetectionThread = null
    }

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
        // Explicit UI/Voice trigger is considered a manual override
        if (onSpeak != null) {
            isManuallyEnabled = true
        }

        val wasAlreadyOn = isGamingModeOn

        if (wasAlreadyOn && onSpeak != null) {
            onSpeak.invoke("Gaming mode is already on, sir.")
        }

        if (!wasAlreadyOn) {
            // Handle settings write permission gracefully
            if (!Settings.System.canWrite(context)) {
                Log.w("GamingModeManager", "Permission denied: WRITE_SETTINGS. Cannot adjust screen timeout natively.")
                if (onSpeak != null) {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    onSpeak.invoke("Please grant permission to modify system settings for full gaming optimizations, sir.")
                    // We do NOT return here. We degrade gracefully and apply the other optimizations (like background throttling).
                }
            } else {
                try {
                    val currentTimeout = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
                    originalScreenTimeout = currentTimeout
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE)
                    Log.i("GamingModeManager", "Optimization applied: Screen timeout extended")
                } catch (e: Exception) {
                    Log.w("GamingModeManager", "Could not modify screen timeout natively", e)
                }
            }

            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    originalRingerMode = audioManager.ringerMode
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE

                    // Adjust volume to 85% for gaming
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val targetVolume = (maxVolume * 0.85).toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
                    Log.i("GamingModeManager", "Optimization applied: DND/Ringer mode adjusted to vibrate")
                } else {
                    Log.w("GamingModeManager", "Permission denied: Notification Policy Access. Cannot modify DND settings.")
                    if (onSpeak != null) {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        onSpeak.invoke("Please grant Do Not Disturb access to optimize gaming mode, sir.")
                    }
                }
            } catch (e: Exception) {
                Log.w("GamingModeManager", "Could not modify ringer mode natively", e)
            }
        }

        isGamingModeOn = true

        if (!wasAlreadyOn) {
            optimizeJarvisBackgroundTasks()
        }

        val game = checkForegroundGame()
        if (game != null) {
            if (currentGame != game || !wasAlreadyOn) {
                currentGame = game
                Log.i("GamingModeManager", "Gaming Mode Active. Game detected: $game")
                if (!wasAlreadyOn) onSpeak?.invoke("Gaming mode activated. Detected $game. Optimizing background activity.")
            }
        } else {
            if (currentGame != null || !wasAlreadyOn) {
                currentGame = null
                Log.i("GamingModeManager", "Gaming Mode Active. No specific game detected.")
                if (!wasAlreadyOn) onSpeak?.invoke("Gaming mode activated. Optimizing background activity.")
            }
        }

        // Notify UI
        val updateIntent = Intent("com.user.jarvis.GAMING_MODE_UPDATED")
        context.sendBroadcast(updateIntent)
    }

    private fun optimizeJarvisBackgroundTasks() {
        try {
            // Signal to Jarvis services to reduce non-essential polling and logging
            val intent = Intent("com.user.jarvis.ACTION_GAMING_MODE_OPTIMIZE")
            context.sendBroadcast(intent)

            // Check for PerformanceHintManager available on Android 12+ (API 31+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val hintManager = context.getSystemService(android.os.PerformanceHintManager::class.java)
                if (hintManager != null) {
                    Log.i("GamingModeManager", "PerformanceHintManager available. Optimization logged.")
                    // Note: Cannot legitimately hint for the game's process since we don't own its TIDs.
                } else {
                    Log.i("GamingModeManager", "PerformanceHintManager unavailable on this device.")
                }
            }
        } catch (e: Exception) {
            Log.w("GamingModeManager", "Failed to apply background task optimizations", e)
        }
    }

    fun disableGamingMode(onSpeak: ((String) -> Unit)? = null) {
        if (!isGamingModeOn) {
            onSpeak?.invoke("Gaming mode is already off, sir.")
            return
        }

        restoreJarvisBackgroundTasks()

        if (originalScreenTimeout != -1) {
            try {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, originalScreenTimeout)
            } catch (e: SecurityException) {
                Log.e("GamingModeManager", "Could not restore screen timeout", e)
            }
        }

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted && originalRingerMode != -1) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.ringerMode = originalRingerMode
            }
        } catch (e: Exception) {
            Log.e("GamingModeManager", "Could not restore ringer mode", e)
        }

        isGamingModeOn = false
        isManuallyEnabled = false
        currentGame = null

        val updateIntent = Intent("com.user.jarvis.GAMING_MODE_UPDATED")
        context.sendBroadcast(updateIntent)

        Log.i("GamingModeManager", "Gaming Mode STOPPED. Normal operations resumed.")
        onSpeak?.invoke("Gaming mode deactivated. Normal operations resumed.")
    }

    private fun restoreJarvisBackgroundTasks() {
        try {
            val intent = Intent("com.user.jarvis.ACTION_GAMING_MODE_RESTORE")
            context.sendBroadcast(intent)
            Log.i("GamingModeManager", "Jarvis background tasks restored.")
        } catch (e: Exception) {
            Log.w("GamingModeManager", "Failed to restore background task optimizations", e)
        }
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
