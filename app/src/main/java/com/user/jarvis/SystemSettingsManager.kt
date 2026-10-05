package com.user.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class SystemSettingsManager(private val context: Context) {

    fun isWriteSettingsAllowed(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun requestWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
