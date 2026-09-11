package com.user.jarvis

object WakeWordDetector {

    private val WAKE_VARIANTS = listOf(
        "jarvis", "jarvish", "jarwis", "jaarvis",
        "जार्विस", "जारविस", "जार्वि"
    )

    fun stripWakeWord(rawText: String): String? {
        val lower = rawText.trim().lowercase()
        for (variant in WAKE_VARIANTS) {
            val idx = lower.indexOf(variant)
            if (idx != -1) {
                val after = rawText.substring(idx + variant.length).trim()
                return after
            }
        }
        return null
    }
}
