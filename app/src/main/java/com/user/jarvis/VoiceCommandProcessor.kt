package com.user.jarvis

object VoiceCommandProcessor {

    fun parse(rawInput: String): Command {
        val text = rawInput.trim()
        val lower = text.lowercase()

        if (Regex("time (kya|batao)|kitna baja").containsMatchIn(lower)) {
            return Command.TellTime
        }
        if (Regex("battery (kitni|batao|percent)").containsMatchIn(lower)) {
            return Command.TellBattery
        }

        if (Regex("volume (badhao|badha do|increase|up)").containsMatchIn(lower)) {
            return Command.VolumeUp
        }
        if (Regex("volume (kam karo|ghatao|decrease|down)").containsMatchIn(lower)) {
            return Command.VolumeDown
        }

        if (Regex("(torch|flashlight|light) (jalao|on karo|on)").containsMatchIn(lower)) {
            return Command.FlashlightOn
        }
        if (Regex("(torch|flashlight|light) (bandh karo|off karo|off)").containsMatchIn(lower)) {
            return Command.FlashlightOff
        }

        Regex("^(google pe search karo|google search|search karo)\\s+(.+)$").find(lower)?.let {
            return Command.GoogleSearch(it.groupValues[2].trim())
        }

        Regex("^whatsapp (pe |par )?(\\w+) ko (bolo|likho|message karo)\\s+(.+)$").find(lower)?.let {
            return Command.SendWhatsApp(it.groupValues[2].trim(), it.groupValues[4].trim())
        }
        Regex("^whatsapp message\\s+(\\w+)\\s+(.+)$").find(lower)?.let {
            return Command.SendWhatsApp(it.groupValues[1].trim(), it.groupValues[2].trim())
        }

        Regex("^(open|khol|khol do|launch)\\s+(.+)$").find(lower)?.let {
            val appName = it.groupValues[2].trim()
            return Command.OpenApp(appName)
        }
        Regex("^(.+?)\\s+khol( do)?$").find(lower)?.let {
            return Command.OpenApp(it.groupValues[1].trim())
        }

        Regex("^call\\s+(.+)$").find(lower)?.let {
            return Command.CallContact(it.groupValues[1].trim())
        }
        Regex("^(.+?)\\s+ko\\s+call\\s+karo$").find(lower)?.let {
            return Command.CallContact(it.groupValues[1].trim())
        }
        Regex("^phone\\s+karo\\s+(.+?)\\s+ko$").find(lower)?.let {
            return Command.CallContact(it.groupValues[1].trim())
        }

        Regex("^message\\s+(\\w+)\\s+(.+)$").find(lower)?.let {
            return Command.SendMessage(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        Regex("^send message to\\s+(\\w+)\\s+saying\\s+(.+)$").find(lower)?.let {
            return Command.SendMessage(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        Regex("^(\\w+)\\s+ko\\s+(bolo|likho|msg karo|message karo)\\s+(.+)$").find(lower)?.let {
            return Command.SendMessage(it.groupValues[1].trim(), it.groupValues[3].trim())
        }

        return Command.Unknown(text)
    }
}
