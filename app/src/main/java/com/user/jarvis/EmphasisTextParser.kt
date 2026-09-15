package com.user.jarvis

object EmphasisTextParser {
    fun parseSegments(text: String): List<Pair<String, Boolean>> {
        val regex = Regex("\\*\\*(.*?)\\*\\*")
        val segments = mutableListOf<Pair<String, Boolean>>()
        var lastEnd = 0
        for (match in regex.findAll(text)) {
            if (match.range.first > lastEnd) {
                segments.add(text.substring(lastEnd, match.range.first) to false)
            }
            segments.add(match.groupValues[1] to true)
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) {
            segments.add(text.substring(lastEnd) to false)
        }
        return segments.filter { it.first.isNotBlank() }
    }
}
