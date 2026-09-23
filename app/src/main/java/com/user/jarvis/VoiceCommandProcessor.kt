package com.user.jarvis

object VoiceCommandProcessor {

    private fun normalize(text: String): String {
        return text
            .trim()
            .lowercase()
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

    private fun containsAny(
        text: String,
        vararg options: String
    ): Boolean {
        return options.any {
            text.contains(it)
        }
    }

    private fun removeWords(
        text: String,
        words: Set<String>
    ): String {
        return text
            .split(" ")
            .filter {
                it.isNotBlank() &&
                    it !in words
            }
            .joinToString(" ")
            .trim()
    }

    fun parse(rawInput: String): Command {

        val norm = normalize(rawInput)

        if (norm.isBlank()) {
            return Command.Unknown(rawInput)
        }

        // =====================================================
        // LOCK PHONE
        // =====================================================

        if (
            containsAny(
                norm,
                "phone lock",
                "mobile lock",
                "screen lock",
                "lock phone",
                "lock mobile",
                "lock karo"
            )
        ) {
            return Command.LockPhone
        }

        // =====================================================
        // FLASHLIGHT
        // =====================================================

        if (
            containsAny(
                norm,
                "torch",
                "flashlight",
                "flash light"
            )
        ) {

            if (
                containsAny(
                    norm,
                    "off",
                    "band",
                    "bandh",
                    "bujha",
                    "bujhao"
                )
            ) {
                return Command.FlashlightOff
            }

            if (
                containsAny(
                    norm,
                    "on",
                    "jala",
                    "jalao",
                    "chalao",
                    "karo"
                )
            ) {
                return Command.FlashlightOn
            }
        }

        // =====================================================
        // VOLUME
        // =====================================================

        if (norm.contains("volume")) {

            if (
                containsAny(
                    norm,
                    "badhao",
                    "badha",
                    "increase",
                    "up",
                    "tez"
                )
            ) {
                return Command.VolumeUp
            }

            if (
                containsAny(
                    norm,
                    "kam",
                    "ghatao",
                    "decrease",
                    "down"
                )
            ) {
                return Command.VolumeDown
            }
        }

        // =====================================================
        // TIME
        // =====================================================

        if (
            containsAny(
                norm,
                "time",
                "kitne baje",
                "kitna baja",
                "samay"
            )
        ) {
            return Command.TellTime
        }

        // =====================================================
        // BATTERY
        // =====================================================

        if (
            containsAny(
                norm,
                "battery",
                "battery kitni",
                "charge kitna"
            )
        ) {
            return Command.TellBattery
        }

        // =====================================================
        // WEATHER
        // =====================================================

        if (
            containsAny(
                norm,
                "weather",
                "mausam",
                "temperature",
                "barish"
            )
        ) {
            return Command.GetWeather
        }

        // =====================================================
        // WIFI
        // =====================================================

        if (
            containsAny(
                norm,
                "wifi",
                "wi fi"
            )
        ) {
            return Command.OpenWifiSettings
        }

        // =====================================================
        // BLUETOOTH
        // =====================================================

        if (
            containsAny(
                norm,
                "bluetooth",
                "blue tooth"
            )
        ) {
            return Command.OpenBluetoothSettings
        }

        // =====================================================
        // SILENT
        // =====================================================

        if (
            containsAny(
                norm,
                "silent mode",
                "silent",
                "chup mode"
            )
        ) {

            if (
                containsAny(
                    norm,
                    "off",
                    "band",
                    "sound on"
                )
            ) {
                return Command.SilentModeOff
            }

            return Command.SilentModeOn
        }

        // =====================================================
        // SEARCH
        // =====================================================

        if (
            containsAny(
                norm,
                "google search",
                "google pe search",
                "search karo",
                "search kar"
            )
        ) {

            val query =
                norm
                    .replace("google search", "")
                    .replace("google pe search", "")
                    .replace("search karo", "")
                    .replace("search kar", "")
                    .replace("search", "")
                    .trim()

            if (query.isNotBlank()) {
                return Command.GoogleSearch(query)
            }
        }

        // =====================================================
        // SONG
        // =====================================================

        if (
            containsAny(
                norm,
                "song",
                "gaana",
                "music"
            ) &&
            containsAny(
                norm,
                "play",
                "chalao",
                "bajao",
                "chala"
            )
        ) {

            val query =
                removeWords(
                    norm,
                    setOf(
                        "song",
                        "gaana",
                        "music",
                        "play",
                        "chalao",
                        "bajao",
                        "chala",
                        "karo"
                    )
                )

            if (query.isNotBlank()) {
                return Command.PlaySong(query)
            }
        }

        // =====================================================
        // ALARM
        // =====================================================

        if (norm.contains("alarm")) {

            val match =
                Regex(
                    "(\\d{1,2})(?:\\s+(\\d{1,2}))?"
                ).find(norm)

            if (match != null) {

                var hour =
                    match.groupValues[1]
                        .toIntOrNull()
                        ?: -1

                val minute =
                    match.groupValues
                        .getOrNull(2)
                        ?.toIntOrNull()
                        ?: 0

                if (
                    hour in 0..23 &&
                    minute in 0..59
                ) {

                    if (
                        containsAny(
                            norm,
                            "raat",
                            "shaam",
                            "evening",
                            "pm"
                        ) &&
                        hour < 12
                    ) {
                        hour += 12
                    }

                    return Command.SetAlarm(
                        hour,
                        minute
                    )
                }
            }
        }

        // =====================================================
        // CLIPBOARD
        // =====================================================

        if (norm.contains("clipboard")) {

            if (
                containsAny(
                    norm,
                    "read",
                    "padho",
                    "batao",
                    "kya hai"
                )
            ) {
                return Command.ReadClipboard
            }

            Regex(
                "(likho|copy karo|save karo)\\s+(.+)"
            ).find(norm)?.let {

                return Command.WriteClipboard(
                    it.groupValues[2].trim()
                )
            }

            return Command.ReadClipboard
        }

        // =====================================================
        // TODO
        // =====================================================

        if (norm.contains("todo")) {

            if (
                containsAny(
                    norm,
                    "read",
                    "padho",
                    "batao",
                    "list"
                )
            ) {
                return Command.ReadTodos
            }

            Regex(
                "(add karo|likho|daalo|add)\\s+(.+)"
            ).find(norm)?.let {

                return Command.AddTodo(
                    it.groupValues[2].trim()
                )
            }
        }

        // =====================================================
        // NOTE
        // =====================================================

        if (norm.contains("note")) {

            if (
                containsAny(
                    norm,
                    "read",
                    "padho",
                    "sunao",
                    "batao"
                )
            ) {
                return Command.ReadNotes
            }

            Regex(
                "(likho|save karo|banao|add karo|add)\\s+(.+)"
            ).find(norm)?.let {

                return Command.SaveNote(
                    it.groupValues[2].trim()
                )
            }
        }

        // =====================================================
        // WHATSAPP
        // =====================================================

        if (norm.contains("whatsapp")) {

            val match =
                Regex(
                    "whatsapp\\s+(?:par|pe)?\\s*(?:message\\s+)?(.+?)\\s+(?:ko\\s+)?(?:bolo|likho|message karo|send karo|msg karo)\\s+(.+)"
                ).find(norm)

            if (match != null) {

                return Command.SendWhatsApp(
                    match.groupValues[1].trim(),
                    match.groupValues[2].trim()
                )
            }
        }

        // =====================================================
        // CALL
        // =====================================================

        if (
            norm.startsWith("call ") ||
            norm.contains("call karo") ||
            norm.contains("phone karo")
        ) {

            val name =
                removeWords(
                    norm,
                    setOf(
                        "call",
                        "karo",
                        "kar",
                        "do",
                        "phone",
                        "please",
                        "kripya"
                    )
                )

            if (name.isNotBlank()) {
                return Command.CallContact(name)
            }
        }

        // =====================================================
        // MESSAGE
        // =====================================================

        Regex(
            "^(?:message|msg)\\s+(\\w+)\\s+(.+)$"
        ).find(norm)?.let {

            return Command.SendMessage(
                it.groupValues[1],
                it.groupValues[2]
            )
        }

        Regex(
            "(\\w+)\\s+ko\\s+(?:bolo|likho|msg karo|message karo)\\s+(.+)"
        ).find(norm)?.let {

            return Command.SendMessage(
                it.groupValues[1],
                it.groupValues[2]
            )
        }

        // =====================================================
        // OPEN APP
        // =====================================================

        if (
            containsAny(
                norm,
                "open ",
                "open",
                "khol",
                "kholo",
                "launch",
                "start"
            )
        ) {

            val app =
                removeWords(
                    norm,
                    setOf(
                        "open",
                        "khol",
                        "kholo",
                        "launch",
                        "start",
                        "karo",
                        "do",
                        "please",
                        "kripya",
                        "mera",
                        "the",
                        "app"
                    )
                )

            if (app.isNotBlank()) {

                return Command.OpenApp(
                    detectAppName(app)
                )
            }
        }

        return Command.Unknown(rawInput)
    }

    private fun detectAppName(
        input: String
    ): String {

        val text = normalize(input)

        return when {

            text == "yt" ||
                text.contains("youtube") ->
                "YouTube"

            text == "insta" ||
                text.contains("instagram") ->
                "Instagram"

            text.contains("whatsapp") ->
                "WhatsApp"

            text.contains("chrome") ->
                "Chrome"

            text == "fb" ||
                text.contains("facebook") ->
                "Facebook"

            text.contains("telegram") ->
                "Telegram"

            text.contains("google maps") ||
                text == "maps" ->
                "Google Maps"

            text.contains("play store") ||
                text.contains("playstore") ->
                "Play Store"

            text.contains("camera") ||
                text.contains("cam") ->
                "Camera"

            text.contains("settings") ||
                text.contains("setting") ->
                "Settings"

            else ->
                input.trim()
        }
    }
}
