package com.user.jarvis

import java.util.Locale

object WakeWordDetector {

    private val wakePhrases = listOf(

        "hey jarvis",
        "hey jarvish",
        "hey jarwis",
        "hey jaarvis",

        "jarvis",
        "jarvish",
        "jarwis",
        "jaarvis",

        "हे जार्विस",
        "हे जारविस",
        "जार्विस",
        "जारविस"
    )

    fun stripWakeWord(
        rawText: String
    ): String? {

        val text =
            normalize(rawText)

        if (text.isBlank()) {
            return null
        }

        val phrases =
            wakePhrases
                .map {
                    normalize(it)
                }
                .sortedByDescending {
                    it.length
                }

        for (phrase in phrases) {

            if (text == phrase) {
                return ""
            }

            if (
                text.startsWith(
                    "$phrase "
                )
            ) {

                return text
                    .removePrefix(
                        phrase
                    )
                    .trim()
            }
        }

        return null
    }

    fun isWakeWord(
        rawText: String
    ): Boolean {

        return stripWakeWord(
            rawText
        ) != null
    }

    private fun normalize(
        text: String
    ): String {

        return text
            .lowercase(
                Locale("hi", "IN")
            )
            .replace(
                Regex("[^\\p{L}\\p{N}\\s]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }
}
