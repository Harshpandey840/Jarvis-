package com.user.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

class AppLauncher(
    private val context: Context
) {

    private val pm =
        context.packageManager

    private val packages =
        mapOf(
            "youtube" to
                "com.google.android.youtube",

            "yt" to
                "com.google.android.youtube",

            "instagram" to
                "com.instagram.android",

            "insta" to
                "com.instagram.android",

            "whatsapp" to
                "com.whatsapp",

            "chrome" to
                "com.android.chrome",

            "google chrome" to
                "com.android.chrome",

            "facebook" to
                "com.facebook.katana",

            "fb" to
                "com.facebook.katana",

            "telegram" to
                "org.telegram.messenger",

            "google maps" to
                "com.google.android.apps.maps",

            "maps" to
                "com.google.android.apps.maps",

            "play store" to
                "com.android.vending",

            "playstore" to
                "com.android.vending"
        )

    fun openApp(
        spokenName: String
    ): Boolean {

        val target =
            normalize(spokenName)

        // ---------------------------------------------
        // SETTINGS
        // ---------------------------------------------

        if (
            target == "settings" ||
            target == "setting"
        ) {

            return try {

                val intent =
                    Intent(
                        Settings.ACTION_SETTINGS
                    ).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }

                context.startActivity(intent)

                true

            } catch (_: Exception) {

                false
            }
        }

        // ---------------------------------------------
        // DIRECT PACKAGE
        // ---------------------------------------------

        val directPackage =
            packages[target]

        if (
            directPackage != null &&
            isInstalled(directPackage)
        ) {

            val launchIntent =
                pm.getLaunchIntentForPackage(
                    directPackage
                )

            if (launchIntent != null) {

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                try {

                    context.startActivity(
                        launchIntent
                    )

                    return true

                } catch (_: Exception) {
                }
            }
        }

        // ---------------------------------------------
        // CAMERA
        // ---------------------------------------------

        if (
            target == "camera" ||
            target == "cam"
        ) {

            return try {

                val intent =
                    Intent(
                        "android.media.action.IMAGE_CAPTURE"
                    ).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }

                context.startActivity(intent)

                true

            } catch (_: Exception) {

                false
            }
        }

        // ---------------------------------------------
        // GENERIC INSTALLED APP SEARCH
        // ---------------------------------------------

        return openByLauncherName(target)
    }

    private fun openByLauncherName(
        target: String
    ): Boolean {

        val launcherIntent =
            Intent(
                Intent.ACTION_MAIN,
                null
            ).apply {

                addCategory(
                    Intent.CATEGORY_LAUNCHER
                )
            }

        val apps =
            pm.queryIntentActivities(
                launcherIntent,
                0
            )

        // Exact
        var best =
            apps.firstOrNull {

                normalize(
                    it.loadLabel(pm).toString()
                ) == target
            }

        // Contains
        if (best == null) {

            best =
                apps
                    .filter {

                        normalize(
                            it.loadLabel(pm).toString()
                        ).contains(target)
                    }
                    .minByOrNull {

                        it.loadLabel(pm)
                            .toString()
                            .length
                    }
        }

        // Reverse contains
        if (best == null) {

            best =
                apps.firstOrNull {

                    val label =
                        normalize(
                            it.loadLabel(pm).toString()
                        )

                    label.isNotBlank() &&
                        target.contains(label)
                }
        }

        if (best == null) {
            return false
        }

        val packageName =
            best.activityInfo.packageName

        val intent =
            pm.getLaunchIntentForPackage(
                packageName
            ) ?: return false

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        return try {

            context.startActivity(intent)

            true

        } catch (_: Exception) {

            false
        }
    }

    private fun isInstalled(
        packageName: String
    ): Boolean {

        return try {

            pm.getApplicationInfo(
                packageName,
                0
            )

            true

        } catch (
            _: PackageManager.NameNotFoundException
        ) {

            false
        }
    }

    private fun normalize(
        value: String
    ): String {

        return value
            .lowercase()
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }
}
