package com.user.jarvis

import java.util.Locale

object VoiceCommandProcessor {

    private fun normalize(
        value: String
    ): String {

        return value
            .trim()
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

    private fun containsAny(
        text: String,
        vararg options: String
    ): Boolean {

        return options.any {
            text.contains(it)
        }
    }

    private fun stripFillerWords(
        text: String,
        fillers: Set<String>
    ): String {

        return text
            .split(" ")
            .filter {
                it.isNotBlank() &&
                        it !in fillers
            }
            .joinToString(" ")
            .trim()
    }

    fun parse(
        rawInput: String
    ): Command {

        val norm =
            normalize(rawInput)

        if (norm.isBlank()) {
            return Command.Unknown(
                rawInput
            )
        }

        // ========================================================
        // LOCK PHONE
        // ========================================================

        if (
            containsAny(
                norm,
                "lock",
                "screen lock",
                "phone lock",
                "mobile lock",
                "फोन लॉक",
                "स्क्रीन लॉक"
            )
        ) {

            return Command.LockPhone
        }

        // ========================================================
        // WEATHER
        // ========================================================

        if (
            containsAny(
                norm,
                "mausam",
                "weather",
                "मौसम"
            )
        ) {

            return Command.GetWeather
        }

        // ========================================================
        // TIME
        // ========================================================

        if (
            containsAny(
                norm,
                "time",
                "samay",
                "समय"
            )
        ) {

            return Command.TellTime
        }

        // ========================================================
        // BATTERY
        // ========================================================

        if (
            containsAny(
                norm,
                "battery",
                "बैटरी"
            )
        ) {

            return Command.TellBattery
        }

        // ========================================================
        // SHARE NOTE
        // ========================================================

        if (
            norm.contains("share") &&
            norm.contains("note")
        ) {

            return Command.ShareNotes
        }

        // ========================================================
        // SHARE TODO
        // ========================================================

        if (
            norm.contains("share") &&
            norm.contains("todo")
        ) {

            return Command.ShareTodos
        }

        // ========================================================
        // CONTACT
        // ========================================================

        if (
            containsAny(
                norm,
                "contact banao",
                "naya contact",
                "add contact",
                "contact bana",
                "contact create"
            )
        ) {

            val digits =
                Regex(
                    "\\b\\d{8,13}\\b"
                ).find(norm)

            if (digits != null) {

                val phone =
                    digits.value

                val namePart =
                    norm.substring(
                        0,
                        digits.range.first
                    )

                val name =
                    stripFillerWords(
                        namePart,
                        setOf(
                            "contact",
                            "banao",
                            "bana",
                            "naya",
                            "add",
                            "number",
                            "no",
                            "ka",
                            "ke",
                            "liye",
                            "name",
                            "create"
                        )
                    )

                if (name.isNotBlank()) {

                    return Command.CreateContact(
                        name,
                        phone
                    )
                }
            }
        }

        // ========================================================
        // VOLUME
        // ========================================================

        if (
            containsAny(
                norm,
                "volume",
                "awaz",
                "आवाज",
                "वॉल्यूम"
            )
        ) {

            if (
                containsAny(
                    norm,
                    "badhao",
                    "badha do",
                    "increase",
                    "tez",
                    "up",
                    "बढ़ाओ"
                )
            ) {

                return Command.VolumeUp
            }

            if (
                containsAny(
                    norm,
                    "kam karo",
                    "ghatao",
                    "decrease",
                    "down",
                    "कम करो"
                )
            ) {

                return Command.VolumeDown
            }
        }

        // ========================================================
        // FLASHLIGHT
        // ========================================================

        if (
            containsAny(
                norm,
                "torch",
                "flashlight",
                "flash light",
                "torch light",
                "टॉर्च"
            )
        ) {

            if (
                containsAny(
                    norm,
                    "bandh",
                    "band",
                    "off",
                    "close",
                    "shut",
                    "बंद"
                )
            ) {

                return Command.FlashlightOff
            }

            if (
                containsAny(
                    norm,
                    "jalao",
                    "on",
                    "chalu",
                    "चालू",
                    "जलाओ"
                )
            ) {

                return Command.FlashlightOn
            }
        }

        // ========================================================
        // WIFI
        // ========================================================

        if (
            containsAny(
                norm,
                "wifi",
                "wi fi",
                "वाईफाई"
            )
        ) {

            return Command.OpenWifiSettings
        }

        // ========================================================
        // BLUETOOTH
        // ========================================================

        if (
            containsAny(
                norm,
                "bluetooth",
                "ब्लूटूथ"
            )
        ) {

            return Command.OpenBluetoothSettings
        }

        // ========================================================
        // SILENT
        // ========================================================

        if (
            containsAny(
                norm,
                "silent",
                "silent mode",
                "chup",
                "साइलेंट"
            )
        ) {

            return Command.SilentModeOn
        }

        // ========================================================
        // SOUND ON
        // ========================================================

        if (
            containsAny(
                norm,
                "sound on",
                "sound chalu",
                "ringer on"
            )
        ) {

            return Command.SilentModeOff
        }

        // ========================================================
        // ALARM
        // ========================================================

        if (
            containsAny(
                norm,
                "alarm",
                "अलार्म"
            )
        ) {

            val timeMatch =
                Regex(
                    "(\\d{1,2})(?:\\s+(\\d{2}))?"
                ).find(norm)

            if (timeMatch != null) {

                var hour =
                    timeMatch
                        .groupValues[1]
                        .toIntOrNull()
                        ?: -1

                val minute =
                    timeMatch
                        .groupValues[2]
                        .toIntOrNull()
                        ?: 0

                if (hour in 0..23) {

                    val isPm =
                        containsAny(
                            norm,
                            "raat",
                            "shaam",
                            "evening",
                            "night",
                            "pm",
                            "रात",
                            "शाम"
                        )

                    val isDopahar =
                        containsAny(
                            norm,
                            "dopahar",
                            "afternoon",
                            "दोपहर"
                        )

                    if (
                        (isPm || isDopahar) &&
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

            return Command.Unknown(
                rawInput
            )
        }

        // ========================================================
        // CLIPBOARD
        // ========================================================

        if (
            containsAny(
                norm,
                "clipboard",
                "क्लिपबोर्ड"
            )
        ) {

            if (
                containsAny(
                    norm,
                    "padho",
                    "kya hai",
                    "read",
                    "batao"
                )
            ) {

                return Command.ReadClipboard
            }

            Regex(
                "(likho|copy karo|save karo)\\s+(.+)"
            ).find(norm)?.let {

                val text =
                    it.groupValues[2]
                        .trim()

                if (text.isNotBlank()) {

                    return Command.WriteClipboard(
                        text
                    )
                }
            }

            return Command.ReadClipboard
        }

        // ========================================================
        // TODO
        // ========================================================

        if (
            norm.contains("todo")
        ) {

            if (
                containsAny(
                    norm,
                    "padho",
                    "batao",
                    "list",
                    "read"
                ) &&
                !containsAny(
                    norm,
                    "add",
                    "likho"
                )
            ) {

                return Command.ReadTodos
            }

            Regex(
                "(add karo|likho|daalo)\\s+(.+)"
            ).find(norm)?.let {

                val item =
                    it.groupValues[2]
                        .trim()

                if (item.isNotBlank()) {

                    return Command.AddTodo(
                        item
                    )
                }
            }

            return Command.ReadTodos
        }

        // ========================================================
        // NOTES
        // ========================================================

        if (
            norm.contains("note")
        ) {

            if (
                containsAny(
                    norm,
                    "padho",
                    "sunao",
                    "read",
                    "batao"
                )
            ) {

                return Command.ReadNotes
            }

            Regex(
                "(likho|save karo|banao|add karo)\\s+(.+)"
            ).find(norm)?.let {

                val note =
                    it.groupValues[2]
                        .trim()

                if (note.isNotBlank()) {

                    return Command.SaveNote(
                        note
                    )
                }
            }
        }

        // ========================================================
        // SONG
        // ========================================================

        if (
            containsAny(
                norm,
                "gaana",
                "song",
                "music",
                "गाना"
            ) &&
            containsAny(
                norm,
                "chalao",
                "play",
                "bajao",
                "चलाओ"
            )
        ) {

            val query =
                stripFillerWords(
                    norm,
                    setOf(
                        "gaana",
                        "song",
                        "music",
                        "chalao",
                        "play",
                        "bajao",
                        "karo"
                    )
                )

            if (query.isNotBlank()) {

                return Command.PlaySong(
                    query
                )
            }
        }

        // ========================================================
        // GOOGLE SEARCH
        // ========================================================

        if (
            containsAny(
                norm,
                "search",
                "google pe search",
                "google par search"
            )
        ) {

            val query =
                stripFillerWords(
                    norm,
                    setOf(
                        "google",
                        "pe",
                        "par",
                        "search",
                        "karo",
                        "kar",
                        "do",
                        "for"
                    )
                )

            if (query.isNotBlank()) {

                return Command.GoogleSearch(
                    query
                )
            }
        }

        // ========================================================
        // WHATSAPP
        // ========================================================

        if (
            norm.contains("whatsapp")
        ) {

            val match =
                Regex(
                    "(bolo|likho|message karo|send karo|kaho|msg karo)\\s+(.+)"
                ).find(norm)

            if (match != null) {

                val message =
                    match.groupValues[2]
                        .trim()

                val before =
                    norm.substring(
                        0,
                        match.range.first
                    )

                val name =
                    stripFillerWords(
                        before,
                        setOf(
                            "whatsapp",
                            "pe",
                            "par",
                            "ko",
                            "message",
                            "send",
                            "to"
                        )
                    )
                        .split(" ")
                        .filter {
                            it.isNotBlank()
                        }
                        .lastOrNull()

                if (
                    !name.isNullOrBlank() &&
                    message.isNotBlank()
                ) {

                    return Command.SendWhatsApp(
                        name,
                        message
                    )
                }
            }
        }

        // ========================================================
        // CALL
        // ========================================================

        if (
            containsAny(
                norm,
                "call",
                "phone",
                "फोन"
            )
        ) {

            val name =
                stripFillerWords(
                    norm,
                    setOf(
                        "call",
                        "karo",
                        "kar",
                        "do",
                        "phone",
                        "ko",
                        "please",
                        "kripya"
                    )
                )

            if (name.isNotBlank()) {

                return Command.CallContact(
                    name
                )
            }
        }

        // ========================================================
        // MESSAGE
        // ========================================================

        if (
            containsAny(
                norm,
                "message",
                "msg",
                "bolo",
                "likho"
            ) &&
            !norm.contains("whatsapp") &&
            !norm.contains("note") &&
            !norm.contains("clipboard") &&
            !norm.contains("todo")
        ) {

            Regex(
                "^message\\s+(\\w+)\\s+(.+)$"
            ).find(norm)?.let {

                return Command.SendMessage(
                    it.groupValues[1],
                    it.groupValues[2]
                )
            }

            Regex(
                "(\\w+)\\s+ko\\s+(bolo|likho|msg karo|message karo)\\s+(.+)"
            ).find(norm)?.let {

                return Command.SendMessage(
                    it.groupValues[1],
                    it.groupValues[3]
                )
            }
        }

        // ========================================================
        // OPEN APP
        // ========================================================

        if (
            containsAny(
                norm,
                "open",
                "khol",
                "launch",
                "start",
                "खोल"
            )
        ) {

            val appName =
                stripFillerWords(
                    norm,
                    setOf(
                        "khol",
                        "do",
                        "open",
                        "launch",
                        "start",
                        "karo",
                        "please",
                        "kripya",
                        "mera",
                        "the",
                        "app"
                    )
                )

            if (appName.isNotBlank()) {

                return Command.OpenApp(
                    appName
                )
            }
        }

        // ========================================================
        // UNKNOWN → AI
        // ========================================================

        return Command.Unknown(
            rawInput
        )
    }
}
