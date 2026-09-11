package com.user.jarvis

object VoiceCommandProcessor {

    fun parse(rawInput: String): Command {
        val text = rawInput.trim()
        val lower = text.lowercase()

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
