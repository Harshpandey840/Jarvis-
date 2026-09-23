package com.user.jarvis

object VoiceCommandProcessor {

    private fun normalize(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsAny(text: String, vararg words: String): Boolean {
        return words.any { text.contains(it) }
    }

    private fun removeWords(text: String, words: Set<String>): String {
        return text
            .split(" ")
            .filter { it.isNotBlank() && it !in words }
            .joinToString(" ")
            .trim()
    }

    fun parse(rawInput: String): Command {

        val text = normalize(rawInput)

        if (text.isBlank()) {
            return Command.Unknown(rawInput)
        }

        // ---------------------------------------------------------
        // 1. PHONE LOCK - HIGHEST PRIORITY
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "lock screen",
                "lock the screen",
                "screen lock",
                "phone lock",
                "phone ko lock",
                "phone lock karo",
                "screen lock karo",
                "lock karo",
                "lock kar do",
                "lock kar",
                "lock"
            )
        ) {
            return Command.LockPhone
        }

        // ---------------------------------------------------------
        // 2. FLASHLIGHT
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "flashlight",
                "flash light",
                "torch",
                "टॉर्च",
                "light"
            )
        ) {
            if (
                containsAny(
                    text,
                    "off",
                    "band",
                    "bandh",
                    "bujha",
                    "bujhao",
                    "close"
                )
            ) {
                return Command.FlashlightOff
            }

            if (
                containsAny(
                    text,
                    "on",
                    "on karo",
                    "jalao",
                    "jala do",
                    "chalao"
                )
            ) {
                return Command.FlashlightOn
            }
        }

        // ---------------------------------------------------------
        // 3. VOLUME
        // ---------------------------------------------------------

        if (containsAny(text, "volume", "awaaz", "awaz")) {

            if (
                containsAny(
                    text,
                    "up",
                    "increase",
                    "increase karo",
                    "badhao",
                    "badha",
                    "tez"
                )
            ) {
                return Command.VolumeUp
            }

            if (
                containsAny(
                    text,
                    "down",
                    "decrease",
                    "kam",
                    "ghatao",
                    "ghata"
                )
            ) {
                return Command.VolumeDown
            }
        }

        // ---------------------------------------------------------
        // 4. APP OPENING - LOCAL / INSTANT
        // ---------------------------------------------------------

        val openWords = listOf(
            "open",
            "khol",
            "kholo",
            "kholna",
            "launch",
            "start",
            "chalao",
            "chala",
            "app kholo",
            "app khol"
        )

        if (openWords.any { text.contains(it) }) {

            val app = detectApp(text)

            if (app != null) {
                return Command.OpenApp(app)
            }

            val cleaned = removeWords(
                text,
                setOf(
                    "open",
                    "khol",
                    "kholo",
                    "kholna",
                    "launch",
                    "start",
                    "chalao",
                    "chala",
                    "karo",
                    "do",
                    "please",
                    "kripya",
                    "mera",
                    "meri",
                    "the",
                    "app"
                )
            )

            if (cleaned.isNotBlank()) {
                return Command.OpenApp(cleaned)
            }
        }

        // ---------------------------------------------------------
        // 5. WIFI
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "wifi",
                "wi fi",
                "वाईफाई"
            )
        ) {
            return Command.OpenWifiSettings
        }

        // ---------------------------------------------------------
        // 6. BLUETOOTH
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "bluetooth",
                "ब्लूटूथ"
            )
        ) {
            return Command.OpenBluetoothSettings
        }

        // ---------------------------------------------------------
        // 7. SILENT MODE
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "silent",
                "silent mode",
                "chup",
                "mute"
            )
        ) {
            return Command.SilentModeOn
        }

        if (
            containsAny(
                text,
                "sound on",
                "sound chalu",
                "sound chalao",
                "ringer on"
            )
        ) {
            return Command.SilentModeOff
        }

        // ---------------------------------------------------------
        // 8. TIME
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "what time",
                "time kya",
                "time bata",
                "kitna baja",
                "samay kya",
                "abhi time"
            )
        ) {
            return Command.TellTime
        }

        // ---------------------------------------------------------
        // 9. BATTERY
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "battery",
                "battery kitni",
                "battery bata"
            )
        ) {
            return Command.TellBattery
        }

        // ---------------------------------------------------------
        // 10. WEATHER
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "weather",
                "mausam",
                "temperature",
                "tapman"
            )
        ) {
            return Command.GetWeather
        }

        // ---------------------------------------------------------
        // 11. SEARCH
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "google search",
                "search karo",
                "search for",
                "google pe search"
            )
        ) {
            val query = text
                .replace("google search", "")
                .replace("search karo", "")
                .replace("search for", "")
                .replace("google pe search", "")
                .trim()

            if (query.isNotBlank()) {
                return Command.GoogleSearch(query)
            }
        }

        // ---------------------------------------------------------
        // 12. PLAY SONG
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "play song",
                "play music",
                "song chalao",
                "gaana chalao",
                "gaana bajao",
                "music chalao"
            )
        ) {
            val query = removeWords(
                text,
                setOf(
                    "play",
                    "song",
                    "music",
                    "gaana",
                    "chalao",
                    "bajao",
                    "karo"
                )
            )

            if (query.isNotBlank()) {
                return Command.PlaySong(query)
            }
        }

        // ---------------------------------------------------------
        // 13. CALL
        // ---------------------------------------------------------

        if (
            containsAny(
                text,
                "call",
                "phone lagao",
                "phone karo",
                "call karo"
            )
        ) {
            val name = removeWords(
                text,
                setOf(
                    "call",
                    "karo",
                    "kar",
                    "do",
                    "phone",
                    "lagao",
                    "lagana",
                    "ko",
                    "please",
                    "kripya"
                )
            )

            if (name.isNotBlank()) {
                return Command.CallContact(name)
            }
        }

        // ---------------------------------------------------------
        // 14. WHATSAPP
        // ---------------------------------------------------------

        if (text.contains("whatsapp")) {

            val match = Regex(
                "(?:whatsapp\\s+(?:pe|par)\\s+)?(.+?)\\s+ko\\s+(?:message|msg|text)\\s+(.+)"
            ).find(text)

            if (match != null) {
                val name = match.groupValues[1].trim()
                val message = match.groupValues[2].trim()

                if (name.isNotBlank() && message.isNotBlank()) {
                    return Command.SendWhatsApp(name, message)
                }
            }

            val sendMatch = Regex(
                "whatsapp\\s+(?:message|msg)\\s+(?:to|ko)\\s+(.+?)\\s+(?:saying|bolo|likho)\\s+(.+)"
            ).find(text)

            if (sendMatch != null) {
                return Command.SendWhatsApp(
                    sendMatch.groupValues[1].trim(),
                    sendMatch.groupValues[2].trim()
                )
            }
        }

        // ---------------------------------------------------------
        // 15. NOTE
        // ---------------------------------------------------------

        if (text.contains("note")) {

            if (
                containsAny(
                    text,
                    "read note",
                    "notes padho",
                    "notes batao",
                    "note padho"
                )
            ) {
                return Command.ReadNotes
            }

            val match = Regex(
                "(?:note|notes)\\s+(?:likho|save karo|banao|add karo)\\s+(.+)"
            ).find(text)

            if (match != null) {
                return Command.SaveNote(match.groupValues[1].trim())
            }
        }

        // ---------------------------------------------------------
        // 16. TODO
        // ---------------------------------------------------------

        if (text.contains("todo")) {

            if (
                containsAny(
                    text,
                    "todo padho",
                    "todo batao",
                    "read todo",
                    "todo list"
                ) &&
                !text.contains("add")
            ) {
                return Command.ReadTodos
            }

            val match = Regex(
                "todo\\s+(?:add karo|likho|daalo)\\s+(.+)"
            ).find(text)

            if (match != null) {
                return Command.AddTodo(match.groupValues[1].trim())
            }
        }

        // ---------------------------------------------------------
        // 17. CLIPBOARD
        // ---------------------------------------------------------

        if (text.contains("clipboard")) {

            if (
                containsAny(
                    text,
                    "read clipboard",
                    "clipboard padho",
                    "clipboard batao"
                )
            ) {
                return Command.ReadClipboard
            }

            val match = Regex(
                "clipboard\\s+(?:mein|me)\\s+(?:copy|likho|save)\\s+(.+)"
            ).find(text)

            if (match != null) {
                return Command.WriteClipboard(match.groupValues[1].trim())
            }
        }

        // ---------------------------------------------------------
        // 18. ALARM
        // ---------------------------------------------------------

        if (text.contains("alarm")) {

            val match = Regex(
                "(\\d{1,2})(?:\\s*(?::|baje|\\s)\\s*(\\d{1,2}))?"
            ).find(text)

            if (match != null) {

                var hour =
                    match.groupValues[1].toIntOrNull() ?: -1

                val minute =
                    match.groupValues.getOrNull(2)
                        ?.toIntOrNull()
                        ?: 0

                if (hour in 0..23) {

                    val pm =
                        containsAny(
                            text,
                            "raat",
                            "shaam",
                            "evening",
                            "night"
                        )

                    if (pm && hour < 12) {
                        hour += 12
                    }

                    return Command.SetAlarm(
                        hour,
                        minute.coerceIn(0, 59)
                    )
                }
            }
        }

        return Command.Unknown(rawInput)
    }

    private fun detectApp(text: String): String? {

        return when {

            containsAny(
                text,
                "youtube",
                "you tube",
                "yt"
            ) -> "YouTube"

            containsAny(
                text,
                "instagram",
                "insta"
            ) -> "Instagram"

            containsAny(
                text,
                "whatsapp",
                "whats app"
            ) -> "WhatsApp"

            containsAny(
                text,
                "chrome",
                "google chrome"
            ) -> "Chrome"

            containsAny(
                text,
                "facebook",
                "fb"
            ) -> "Facebook"

            containsAny(
                text,
                "telegram"
            ) -> "Telegram"

            containsAny(
                text,
                "camera",
                "cam"
            ) -> "Camera"

            containsAny(
                text,
                "settings",
                "setting"
            ) -> "Settings"

            containsAny(
                text,
                "calculator",
                "calc"
            ) -> "Calculator"

            containsAny(
                text,
                "maps",
                "google maps"
            ) -> "Google Maps"

            containsAny(
                text,
                "play store",
                "playstore"
            ) -> "Play Store"

            else -> null
        }
    }
}
