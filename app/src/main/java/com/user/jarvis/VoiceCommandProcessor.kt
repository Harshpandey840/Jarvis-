package com.user.jarvis

object VoiceCommandProcessor {

    private fun normalize(s: String): String =
        s.trim().lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun containsAny(text: String, vararg options: String) = options.any { text.contains(it) }

    private fun stripFillerWords(text: String, fillers: Set<String>): String =
        text.split(" ")
            .filter { it.isNotBlank() && it !in fillers }
            .joinToString(" ")
            .trim()

    fun parse(rawInput: String): Command {
        val norm = normalize(rawInput)
        if (norm.isBlank()) return Command.Unknown(rawInput)

        if (norm.contains("lock")) return Command.LockPhone

        if (containsAny(norm, "mausam", "weather")) return Command.GetWeather

        if (norm.contains("time") && containsAny(norm, "kya", "batao", "kitna baja")) return Command.TellTime

        if (norm.contains("battery")) return Command.TellBattery

        if (norm.contains("share") && norm.contains("note")) return Command.ShareNotes
        if (norm.contains("share") && norm.contains("todo")) return Command.ShareTodos

        if (containsAny(norm, "contact banao", "naya contact", "add contact")) {
            val digitsMatch = Regex("\\b\\d{8,13}\\b").find(norm)
            if (digitsMatch != null) {
                val phone = digitsMatch.value
                val namePart = norm.substring(0, digitsMatch.range.first)
                val name = stripFillerWords(
                    namePart,
                    setOf("contact", "banao", "naya", "add", "number", "no", "ka", "ke", "liye", "name")
                )
                if (name.isNotBlank()) return Command.CreateContact(name, phone)
            }
        }

        if (norm.contains("volume")) {
            if (containsAny(norm, "badhao", "badha do", "increase", "tez", " up")) return Command.VolumeUp
            if (containsAny(norm, "kam karo", "ghatao", "decrease", " down", "kam kar")) return Command.VolumeDown
        }

        if (containsAny(norm, "torch", "flashlight", "flash light") || norm.contains("light")) {
            if (containsAny(norm, "bandh", "band", "off karo", " off", "close", "shut")) return Command.FlashlightOff
            if (containsAny(norm, "jalao", "on karo", " on")) return Command.FlashlightOn
        }

        if (norm.contains("wifi")) return Command.OpenWifiSettings
        if (norm.contains("bluetooth")) return Command.OpenBluetoothSettings

        if (containsAny(norm, "silent", "chup")) return Command.SilentModeOn
        if (norm.contains("sound") && containsAny(norm, "on karo", "chalu karo", " on")) return Command.SilentModeOff

        if (norm.contains("alarm")) {
            val timeMatch = Regex("(\\d{1,2})(?:\\s(\\d{2}))?").find(norm)
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
            return Command.Unknown(rawInput)
        }

        if (norm.contains("clipboard")) {
            if (containsAny(norm, "padho", "kya hai", "read", "batao")) return Command.ReadClipboard
            Regex("(likho|copy karo|save karo)\\s+(.+)").find(norm)?.let {
                val clipText = it.groupValues[2].trim()
                if (clipText.isNotBlank()) return Command.WriteClipboard(clipText)
            }
            return Command.ReadClipboard
        }

        if (norm.contains("todo")) {
            if (containsAny(norm, "padho", "batao", "list", "read") && !containsAny(norm, "add", "likho")) {
                return Command.ReadTodos
            }
            Regex("(add karo|likho|daalo)\\s+(.+)").find(norm)?.let {
                val item = it.groupValues[2].trim()
                if (item.isNotBlank()) return Command.AddTodo(item)
            }
            return Command.ReadTodos
        }

        if (norm.contains("note")) {
            if (containsAny(norm, "padho", "sunao", "read", "batao")) return Command.ReadNotes
            Regex("(likho|save karo|banao|add karo)\\s+(.+)").find(norm)?.let {
                val noteText = it.groupValues[2].trim()
                if (noteText.isNotBlank()) return Command.SaveNote(noteText)
            }
        }

        if (containsAny(norm, "gaana", "song", "music") && containsAny(norm, "chalao", "play", "bajao")) {
            val query = stripFillerWords(norm, setOf("gaana", "song", "music", "chalao", "play", "bajao", "karo"))
            if (query.isNotBlank()) return Command.PlaySong(query)
        }

        if (norm.contains("search")) {
            val query = stripFillerWords(norm, setOf("google", "pe", "search", "karo", "kar", "do", "for"))
            if (query.isNotBlank()) return Command.GoogleSearch(query)
        }

        if (norm.contains("whatsapp")) {
            val verbMatch = Regex("(bolo|likho|message karo|send karo|kaho|msg karo)\\s+(.+)").find(norm)
            if (verbMatch != null) {
                val messageText = verbMatch.groupValues[2].trim()
                val before = norm.substring(0, verbMatch.range.first)
                val nameCandidate = stripFillerWords(
                    before, setOf("whatsapp", "pe", "par", "ko", "message", "send", "to")
                ).split(" ").filter { it.isNotBlank() }.lastOrNull()
                if (!nameCandidate.isNullOrBlank() && messageText.isNotBlank()) {
                    return Command.SendWhatsApp(nameCandidate, messageText)
                }
            }
        }

        if (norm.contains("call")) {
            val name = stripFillerWords(norm, setOf("call", "karo", "kar", "do", "phone", "ko", "please", "kripya"))
            if (name.isNotBlank()) return Command.CallContact(name)
        }

        if (containsAny(norm, "message", "msg", "bolo", "likho") &&
            !norm.contains("whatsapp") && !norm.contains("note") &&
            !norm.contains("clipboard") && !norm.contains("todo")) {
            Regex("^message\\s+(\\w+)\\s+(.+)$").find(norm)?.let {
                return Command.SendMessage(it.groupValues[1], it.groupValues[2])
            }
            Regex("(\\w+)\\s+ko\\s+(bolo|likho|msg karo|message karo)\\s+(.+)").find(norm)?.let {
                return Command.SendMessage(it.groupValues[1], it.groupValues[3])
            }
        }

        if (containsAny(norm, "open", "khol", "launch", "start")) {
            val appName = stripFillerWords(
                norm,
                setOf("khol", "do", "open", "launch", "start", "karo", "please", "kripya", "mera", "the", "app")
            )
            if (appName.isNotBlank()) return Command.OpenApp(appName)
        }

        return Command.Unknown(rawInput)
    }
}
