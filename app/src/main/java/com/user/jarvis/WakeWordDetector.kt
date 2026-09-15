package com.user.jarvis

object WakeWordDetector {

    private val WAKE_VARIANTS = listOf(
        "jarvis", "jarvish", "jarwis", "jaarvis",
        "जार्विस", "जारविस", "जार्वि"
    )

    fun stripWakeWord(rawText: String): String? {
        val lower = rawText.trim().lowercase()
        for (variant in WAKE_VARIANTS) {
            if (lower.startsWith(variant)) {
                val after = rawText.substring(variant.length).trim()
                return after
            }
        }
        return null
    }
}
