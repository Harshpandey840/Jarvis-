package com.user.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("is_running", false)) {
                val startIntent = Intent(context, JarvisListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, startIntent)
                } else {
                    context.startService(startIntent)
                }
            }
        }
    }
}
