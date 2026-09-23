package com.user.jarvis

object WakeWordDetector {

    private val WAKE_VARIANTS = listOf(
        "hey jarvis",
        "hey jarvish",
        "hey jarwis",
        "hey jaarvis",
        "jarvis",
        "jarvish",
        "jarwis",
        "jaarvis",
        "जार्विस",
        "जारविस",
        "जार्वि"
    )

    /**
     * Returns:
     * null  = wake word not detected
     * ""    = wake word detected but no command followed
     * text  = wake word detected + command
     */
    fun stripWakeWord(rawText: String): String? {
        val original = rawText.trim()
        if (original.isBlank()) return null

        val lower = original.lowercase(LocaleHelper.locale)

        for (variant in WAKE_VARIANTS) {
            if (lower == variant) {
                return ""
            }

            if (lower.startsWith("$variant ")) {
                return original.substring(variant.length).trim()
            }

            if (lower.startsWith("$variant,")) {
                return original.substring(variant.length)
                    .trim()
                    .trimStart(',', ':', '-', ' ')
            }
        }

        return null
    }

    fun containsWakeWord(rawText: String): Boolean {
        return stripWakeWord(rawText) != null
    }

    private object LocaleHelper {
        val locale = java.util.Locale.ENGLISH
    }
}
