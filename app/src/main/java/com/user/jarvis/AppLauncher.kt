package com.user.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

class AppLauncher(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun openApp(spokenName: String): Boolean {
        val target = normalize(spokenName)

        // Direct app/package mapping for common apps.
        val directPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "yt" to "com.google.android.youtube",

            "instagram" to "com.instagram.android",
            "insta" to "com.instagram.android",

            "whatsapp" to "com.whatsapp",
            "whatsappmessenger" to "com.whatsapp",

            "chrome" to "com.android.chrome",
            "googlechrome" to "com.android.chrome",

            "facebook" to "com.facebook.katana",
            "fb" to "com.facebook.katana",

            "telegram" to "org.telegram.messenger",

            "googlemaps" to "com.google.android.apps.maps",
            "maps" to "com.google.android.apps.maps",

            "playstore" to "com.android.vending",
            "googleplaystore" to "com.android.vending"
        )

        // Special Android Settings handling.
        if (target == "settings" || target == "setting") {
            return try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }

        // Try direct package launch first.
        val directPackage = directPackages[target]

        if (directPackage != null && isPackageInstalled(directPackage)) {
            val intent = pm.getLaunchIntentForPackage(directPackage)

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }

        // Fallback: search installed launcher apps by name.
        return openByAppName(target)
    }

    private fun openByAppName(target: String): Boolean {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(launcherIntent, 0)

        // Exact match
        var best = apps.firstOrNull {
            normalize(it.loadLabel(pm).toString()) == target
        }

        // Partial match
        if (best == null) {
            best = apps
                .filter {
                    normalize(it.loadLabel(pm).toString()).contains(target)
                }
                .minByOrNull {
                    it.loadLabel(pm).toString().length
                }
        }

        // Reverse partial match
        if (best == null) {
            best = apps.firstOrNull {
                target.contains(
                    normalize(it.loadLabel(pm).toString())
                )
            }
        }

        if (best == null) return false

        val pkg = best.activityInfo.packageName
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return true
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            pm.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun normalize(s: String): String {
        return s
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }
}
