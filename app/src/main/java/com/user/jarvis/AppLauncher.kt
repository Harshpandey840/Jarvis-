package com.user.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AppLauncher(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun openApp(spokenName: String): Boolean {
        val target = normalize(spokenName)
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(launcherIntent, 0)

        var best = apps.firstOrNull { normalize(it.loadLabel(pm).toString()) == target }

        if (best == null) {
            best = apps
                .filter { normalize(it.loadLabel(pm).toString()).contains(target) }
                .minByOrNull { it.loadLabel(pm).toString().length }
        }

        if (best == null) {
            best = apps.firstOrNull { target.contains(normalize(it.loadLabel(pm).toString())) }
        }

        if (best == null) return false

        val pkg = best.activityInfo.packageName
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
}
