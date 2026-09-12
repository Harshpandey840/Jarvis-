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

        if (norm.contains("wifi")) {
            return Command.OpenWifiSettings
        }
        if (norm.contains("bluetooth")) {
            return Command.OpenBluetoothSettings
        }

        if (containsAny(norm, "silent", "chup")) {
            return Command.SilentModeOn
        }
        if (norm.contains("sound") && containsAny(norm, "on karo", "chalu karo", " on")) {
            return Command.SilentModeOff
        }

        if (norm.contains("alarm")) {
            val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?").find(norm)
            if (timeMatch != null) {
                var hour = timeMatch.groupValues[1].toIntOrNull() ?: -1
                val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                if (hour in 0..23) {
                    val isPm = containsAny(norm, "raat", "shaam", "evening", "night")
                    val isDopahar = norm.contains("dopahar") || norm.contains("afternoon")
                    if ((isPm || isDopahar) && hour < 12) hour += 12
                    return Command.SetAlarm(hour, minute)
                }
            }
            return Command.SetAlarm(-1, -1)
        }

        if (norm.contains("note")) {
            if (containsAny(norm, "padho", "sunao", "read", "batao")) {
                return Command.ReadNotes
            }
            val verbMatch = Regex("(likho|save karo|banao|add karo)\\s+(.+)").find(norm)
            if (verbMatch != null) {
                val noteText = verbMatch.groupValues[2].trim()
                if (noteText.isNotBlank()) return Command.SaveNote(noteText)
            }
        }

        if (containsAny(norm, "gaana", "song", "music") && containsAny(norm, "chalao", "play", "bajao")) {
            var query = norm
            listOf("gaana", "song", "music", "chalao", "play", "bajao", "karo")
                .forEach { query = query.replace(it, " ") }
            query = query.replace(Regex("\\s+"), " ").trim()
            if (query.isNotBlank()) return Command.PlaySong(query)
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

        if (containsAny(norm, "message", "msg", "bolo", "likho") && !norm.contains("whatsapp") && !norm.contains("note")) {
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
