package com.user.jarvis

object VoiceCommandProcessor {

    private fun normalize(s: String): String =
        s.trim().lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun containsAny(text: String, vararg options: String) = options.any { text.contains(it) }

    fun parse(rawInput: String): Command {
        val norm = normalize(rawInput)
        if (norm.isBlank()) return Command.Unknown(rawInput)

        if (norm.contains("time") && containsAny(norm, "kya", "batao", "kitna baja")) {
            return Command.TellTime
        }

        if (norm.contains("battery")) {
            return Command.TellBattery
        }

        if (norm.contains("volume")) {
            if (containsAny(norm, "badhao", "badha do", "increase", "tez", " up")) return Command.VolumeUp
            if (containsAny(norm, "kam karo", "ghatao", "decrease", " down", "kam kar")) return Command.VolumeDown
        }

        if (containsAny(norm, "torch", "flashlight", "flash light") || norm.contains("light")) {
            if (containsAny(norm, "jalao", "on karo", " on")) return Command.FlashlightOn
            if (containsAny(norm, "bandh", "off karo", " off")) return Command.FlashlightOff
        }

        if (norm.contains("search")) {
            var query = norm
            listOf("google pe search karo", "google search", "search karo", "search kar do", "search for", "search")
                .forEach { query = query.replace(it, " ") }
            query = query.trim()
            if (query.isNotBlank()) return Command.GoogleSearch(query)
        }

        if (norm.contains("whatsapp")) {
            val verbMatch = Regex("(bolo|likho|message karo|send karo|kaho|msg karo)\\s+(.+)").find(norm)
            if (verbMatch != null) {
                val messageText = verbMatch.groupValues[2].trim()
                val before = norm.substring(0, verbMatch.range.first)
                val nameCandidate = before
                    .replace("whatsapp", " ")
                    .replace(Regex("\\b(pe|par|ko|message|send|to)\\b"), " ")
                    .trim()
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .lastOrNull()
                if (!nameCandidate.isNullOrBlank() && messageText.isNotBlank()) {
                    return Command.SendWhatsApp(nameCandidate, messageText)
                }
            }
        }

        if (norm.contains("call")) {
            var name = norm
            listOf("call", "karo", "kar do", "phone", "ko", "please", "kripya")
                .forEach { name = name.replace(it, " ") }
            name = name.trim()
            if (name.isNotBlank()) return Command.CallContact(name)
        }

        if (containsAny(norm, "message", "msg", "bolo", "likho") && !norm.contains("whatsapp")) {
            Regex("^message\\s+(\\w+)\\s+(.+)$").find(norm)?.let {
                return Command.SendMessage(it.groupValues[1], it.groupValues[2])
            }
            Regex("(\\w+)\\s+ko\\s+(bolo|likho|msg karo|message karo)\\s+(.+)").find(norm)?.let {
                return Command.SendMessage(it.groupValues[1], it.groupValues[3])
            }
        }

        if (containsAny(norm, "open", "khol", "launch", "start")) {
            var appName = norm
            listOf("khol do", "open", "khol", "launch", "start", "karo", "please", "kripya", "mera", "the", "app")
                .forEach { appName = appName.replace(it, " ") }
            appName = appName.replace(Regex("\\s+"), " ").trim()
            if (appName.isNotBlank()) return Command.OpenApp(appName)
        }

        return Command.Unknown(rawInput)
    }
}
