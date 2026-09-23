package com.user.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

class AppLauncher(
    private val context: Context
) {

    private val pm: PackageManager =
        context.packageManager

    fun openApp(
        spokenName: String
    ): Boolean {

        val target =
            normalize(
                spokenName
            )

        val directPackages =
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

                "whatsappmessenger" to
                        "com.whatsapp",

                "chrome" to
                        "com.android.chrome",

                "googlechrome" to
                        "com.android.chrome",

                "facebook" to
                        "com.facebook.katana",

                "fb" to
                        "com.facebook.katana",

                "telegram" to
                        "org.telegram.messenger",

                "maps" to
                        "com.google.android.apps.maps",

                "googlemaps" to
                        "com.google.android.apps.maps",

                "playstore" to
                        "com.android.vending",

                "googleplaystore" to
                        "com.android.vending"
            )

        // SETTINGS
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

                context.startActivity(
                    intent
                )

                true

            } catch (_: Exception) {

                false
            }
        }

        // DIRECT PACKAGE
        val directPackage =
            directPackages[target]

        if (
            directPackage != null &&
            isPackageInstalled(
                directPackage
            )
        ) {

            val intent =
                pm.getLaunchIntentForPackage(
                    directPackage
                )

            if (intent != null) {

                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                context.startActivity(
                    intent
                )

                return true
            }
        }

        return openByAppName(
            target
        )
    }

    private fun openByAppName(
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

        var best =
            apps.firstOrNull {

                normalize(
                    it.loadLabel(pm).toString()
                ) == target
            }

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

        if (best == null) {

            best =
                apps.firstOrNull {

                    target.contains(
                        normalize(
                            it.loadLabel(pm).toString()
                        )
                    )
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
            )
                ?: return false

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        context.startActivity(
            intent
        )

        return true
    }

    private fun isPackageInstalled(
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
                Regex("[^a-z0-9]"),
                ""
            )
    }
}
